/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * Abstract implementation of the {@link PropertyAccessor} interface.
 * Provides base implementations of all convenience methods, with the
 * implementation of actual property access left to subclasses.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.0
 * @see #getPropertyValue
 * @see #setPropertyValue
 */
public abstract class AbstractPropertyAccessor extends TypeConverterSupport implements ConfigurablePropertyAccessor {
	/*
	 * 继承了 TypeConverterSupport
	 * TypeConverterSupport 是一个很流批的类,
	 *
	 * TypeConverterSupport extends PropertyEditorRegistrySupport implements TypeConverter
	 * TypeConverterSupport继承了PropertyEditorRegistrySupport
	 * PropertyEditorRegistrySupport, 完成了属性编辑器的注册,以及持有一个ConverterService成员,完成了对converter的注册以及转换服务
	 * TypeConverterSupport 将 converter 和 PropertyEditor 转换都交给委托类去实现啦
	 *
	 * 主要作用: 就是继承TypeConverterSupport\实现ConfigurationPropertyAccessor
	 */

	private boolean extractOldValueForEditor = false; // 是否为编辑器提取旧值

	private boolean autoGrowNestedPaths = false;
	// 是否自动增长,当嵌套路径包含一个null值
	// 当然若你希望null值能够被自动初始化也是可以的，请设值：accessor.setAutoGrowNestedPaths(true);这样数组、集合、Map等都会为null时候给你初始化(其它Bean请保证有默认构造函数)
	// 比如  PropertyAccessor,setPropertyValue("list[1]","niubi");  表示将当前目标对象的list成员的第一个元素设置为"niubi"
	// 但前提是啥,你得事先把这个对象 list 给初始话创建起来,比如 private List<String> list = new ArrayList<>();
	// 否则就会报错
	// 但是可以将 autoGrowNestedPaths 设置为true,一旦检查到路径上的某个属性为null,没有被用户初始化,那么我们来帮助他完成初始化操作

	boolean suppressNotWritablePropertyException = false; // 是否支持抛出不可写得异常


	@Override
	public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
		this.extractOldValueForEditor = extractOldValueForEditor;
	}

	@Override
	public boolean isExtractOldValueForEditor() {
		return this.extractOldValueForEditor;
	}

	@Override
	public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
		this.autoGrowNestedPaths = autoGrowNestedPaths;
	}

	@Override
	public boolean isAutoGrowNestedPaths() {
		return this.autoGrowNestedPaths;
	}


	@Override
	public void setPropertyValue(PropertyValue pv) throws BeansException {
		setPropertyValue(pv.getName(), pv.getValue());
	}

	@Override
	public void setPropertyValues(Map<?, ?> map) throws BeansException {
		setPropertyValues(new MutablePropertyValues(map));
	}

	@Override
	public void setPropertyValues(PropertyValues pvs) throws BeansException {
		setPropertyValues(pvs, false, false);
	}

	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException {
		setPropertyValues(pvs, ignoreUnknown, false);
	}

	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException {
		// 核心哦

		// 1. 存储异常
		List<PropertyAccessException> propertyAccessExceptions = null;
		List<PropertyValue> propertyValues = (pvs instanceof MutablePropertyValues ?
				((MutablePropertyValues) pvs).getPropertyValueList() : Arrays.asList(pvs.getPropertyValues()));

		if (ignoreUnknown) {
			// 临时开启支持抛出无法write的异常
			this.suppressNotWritablePropertyException = true;
		}
		try {
			for (PropertyValue pv : propertyValues) {
				// setPropertyValue may throw any BeansException, which won't be caught
				// here, if there is a critical failure such as no matching field.
				// We can attempt to deal only with less serious exceptions.
				try {
					setPropertyValue(pv);
				}
				catch (NotWritablePropertyException ex) {
					// 无法写入,如果ignoreUnknown为true,表示忽略未知的属性时,就不需要抛出异常
					if (!ignoreUnknown) {
						throw ex;
					}
					// Otherwise, just ignore it and continue...
				}
				catch (NullValueInNestedPathException ex) {
					// 属性的嵌套路径中有空值
					// 比如 Person类中有一个Address类,Address类有一个String类型的City
					// 那么对应的就是 person.address.city
					// 这就是嵌套路径,若使用 person.address1.city 就有异常
					if (!ignoreInvalid) {
						throw ex;
					}
					// Otherwise, just ignore it and continue...
				}
				catch (PropertyAccessException ex) {
					if (propertyAccessExceptions == null) {
						propertyAccessExceptions = new ArrayList<>();
					}
					propertyAccessExceptions.add(ex);
				}
			}
		}
		finally {
			if (ignoreUnknown) {
				this.suppressNotWritablePropertyException = false;
			}
		}

		// If we encountered individual exceptions, throw the composite exception.
		// 如果我们遇到单个异常，则抛出复合异常。
		if (propertyAccessExceptions != null) {
			PropertyAccessException[] paeArray = propertyAccessExceptions.toArray(new PropertyAccessException[0]);
			throw new PropertyBatchUpdateException(paeArray);
		}
	}


	// Redefined with public visibility.
	@Override
	@Nullable
	public Class<?> getPropertyType(String propertyPath) {
		return null;
	}

	/**
	 * Actually get the value of a property.
	 * @param propertyName name of the property to get the value of
	 * @return the value of the property
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't readable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed
	 */
	@Override
	@Nullable
	public abstract Object getPropertyValue(String propertyName) throws BeansException;

	/**
	 * Actually set a property value.
	 * @param propertyName name of the property to set value of
	 * @param value the new value
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed or a type mismatch occurred
	 */
	@Override
	public abstract void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;

}
