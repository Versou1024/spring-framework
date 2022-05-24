/*
 * Copyright 2002-2021 the original author or authors.
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

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.PrivilegedActionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * A basic {@link ConfigurablePropertyAccessor} that provides the necessary
 * infrastructure for all typical use cases.
 *
 * <p>This accessor will convert collection and array values to the corresponding
 * target collections or arrays, if necessary. Custom property editors that deal
 * with collections or arrays can either be written via PropertyEditor's
 * {@code setValue}, or against a comma-delimited String via {@code setAsText},
 * as String arrays are converted in such a format if the array itself is not
 * assignable.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @author Rod Johnson
 * @author Rob Harrop
 * @since 4.2
 * @see #registerCustomEditor
 * @see #setPropertyValues
 * @see #setPropertyValue
 * @see #getPropertyValue
 * @see #getPropertyType
 * @see BeanWrapper
 * @see PropertyEditorRegistrySupport
 */
public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {
	// 一个基本的ConfigurablePropertyAccessor ，为所有典型用例提供必要的基础设施。
	//	如有必要，此访问器会将集合和数组值转换为相应的目标集合或数组。
	//	处理集合或数组的自定义属性编辑器可以通过 PropertyEditor 的setValue编写，
	//	也可以通过setAsText针对逗号分隔的字符串编写，因为如果数组本身不可分配，则字符串数组将以这种格式转换。

	/**
	 * We'll create a lot of these objects, so we don't want a new logger every time.
	 */
	private static final Log logger = LogFactory.getLog(AbstractNestablePropertyAccessor.class);

	private int autoGrowCollectionLimit = Integer.MAX_VALUE;

	@Nullable
	Object wrappedObject;

	private String nestedPath = "";

	@Nullable
	Object rootObject;

	/** Map with cached nested Accessors: nested path -> Accessor instance. */
	@Nullable
	private Map<String, AbstractNestablePropertyAccessor> nestedPropertyAccessors;


	/**
	 * Create a new empty accessor. Wrapped instance needs to be set afterwards.
	 * Registers default editors.
	 * @see #setWrappedInstance
	 */
	protected AbstractNestablePropertyAccessor() {
		// 默认会注册属性编辑器: defaultEditors  它几乎处理了所有的Java内置类型  包括基本类型、包装类型以及对应数组类型~~~
		this(true);
	}

	/**
	 * Create a new empty accessor. Wrapped instance needs to be set afterwards.
	 * @param registerDefaultEditors whether to register default editors
	 * (can be suppressed if the accessor won't need any type conversion)
	 * @see #setWrappedInstance
	 */
	protected AbstractNestablePropertyAccessor(boolean registerDefaultEditors) {
		// 核心：无参构造器，会注册默认编辑器
		if (registerDefaultEditors) {
			registerDefaultEditors();
		}
		// 指定上层抽象类的 typeConverterDelegate 的具体实现者
		// 注意:这里将this传递给委托者 TypeConverterServiceDelegate
		this.typeConverterDelegate = new TypeConverterDelegate(this);
	}

	/**
	 * Create a new accessor for the given object.
	 * @param object the object wrapped by this accessor
	 */
	protected AbstractNestablePropertyAccessor(Object object) {
		registerDefaultEditors();
		setWrappedInstance(object);
	}

	/**
	 * Create a new accessor, wrapping a new instance of the specified class.
	 * @param clazz class to instantiate and wrap
	 */
	protected AbstractNestablePropertyAccessor(Class<?> clazz) {
		registerDefaultEditors();
		setWrappedInstance(BeanUtils.instantiateClass(clazz));
	}

	/**
	 * Create a new accessor for the given object,
	 * registering a nested path that the object is in.
	 * @param object the object wrapped by this accessor
	 * @param nestedPath the nested path of the object
	 * @param rootObject the root object at the top of the path
	 */
	protected AbstractNestablePropertyAccessor(Object object, String nestedPath, Object rootObject) {
		registerDefaultEditors();
		setWrappedInstance(object, nestedPath, rootObject);
	}

	/**
	 * Create a new accessor for the given object,
	 * registering a nested path that the object is in.
	 * @param object the object wrapped by this accessor
	 * @param nestedPath the nested path of the object
	 * @param parent the containing accessor (must not be {@code null})
	 */
	protected AbstractNestablePropertyAccessor(Object object, String nestedPath, AbstractNestablePropertyAccessor parent) {
		setWrappedInstance(object, nestedPath, parent.getWrappedInstance());
		setExtractOldValueForEditor(parent.isExtractOldValueForEditor());
		setAutoGrowNestedPaths(parent.isAutoGrowNestedPaths());
		setAutoGrowCollectionLimit(parent.getAutoGrowCollectionLimit());
		setConversionService(parent.getConversionService());
	}


	/**
	 * Specify a limit for array and collection auto-growing.
	 * <p>Default is unlimited on a plain accessor.
	 */
	public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
		this.autoGrowCollectionLimit = autoGrowCollectionLimit;
	}

	/**
	 * Return the limit for array and collection auto-growing.
	 */
	public int getAutoGrowCollectionLimit() {
		return this.autoGrowCollectionLimit;
	}

	/**
	 * Switch the target object, replacing the cached introspection results only
	 * if the class of the new object is different to that of the replaced object.
	 * @param object the new target object
	 */
	public void setWrappedInstance(Object object) {
		setWrappedInstance(object, "", null);
	}

	/**
	 * Switch the target object, replacing the cached introspection results only
	 * if the class of the new object is different to that of the replaced object.
	 * @param object the new target object
	 * @param nestedPath the nested path of the object
	 * @param rootObject the root object at the top of the path
	 */
	public void setWrappedInstance(Object object, @Nullable String nestedPath, @Nullable Object rootObject) {
		// 设置
		// wrappedObject 被包装的对象,目标对象、
		// nestedPath	 被包装对象再rootObject中的嵌套路径
		// rootObject 	 根对象在nestedPath为空时就是被包装的对象
		// 以 person.address 为例
		// object就是address,rootObject就是person
		// 根据 person.address 表名object在rootobject的address属性成员中
		this.wrappedObject = ObjectUtils.unwrapOptional(object);
		Assert.notNull(this.wrappedObject, "Target object must not be null");
		this.nestedPath = (nestedPath != null ? nestedPath : "");
		this.rootObject = (!this.nestedPath.isEmpty() ? rootObject : this.wrappedObject);
		this.nestedPropertyAccessors = null;
		this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
	}

	public final Object getWrappedInstance() {
		Assert.state(this.wrappedObject != null, "No wrapped object");
		return this.wrappedObject;
	}

	public final Class<?> getWrappedClass() {
		return getWrappedInstance().getClass();
	}

	/**
	 * Return the nested path of the object wrapped by this accessor.
	 */
	public final String getNestedPath() {
		return this.nestedPath;
	}

	/**
	 * Return the root object at the top of the path of this accessor.
	 * @see #getNestedPath
	 */
	public final Object getRootInstance() {
		Assert.state(this.rootObject != null, "No root object");
		return this.rootObject;
	}

	/**
	 * Return the class of the root object at the top of the path of this accessor.
	 * @see #getNestedPath
	 */
	public final Class<?> getRootClass() {
		return getRootInstance().getClass();
	}

	@Override
	public void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException {
		AbstractNestablePropertyAccessor nestedPa;
		try {
			// 递归导航以返回嵌套属性路径的属性访问器
			// 比如设置 wrappedObject为Address类,nestedPath为person.address,rootObject为Person
			// 这里需啊哟设置 propertyName为city的字段,设置为value值
			// 返回的 nestedPa 返回的就是 person.address.city
			nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		}
		catch (NotReadablePropertyException ex) {
			throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
					"Nested property in path '" + propertyName + "' does not exist", ex);
		}
		// getFinalPath() 获取嵌套在最后的属性
		// getPropertyNameTokens() 将嵌套在最后的属性进行解析为 PropertyTokenHolder
		PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
		// 向这个属性设置值
		nestedPa.setPropertyValue(tokens, new PropertyValue(propertyName, value));
	}

	@Override
	public void setPropertyValue(PropertyValue pv) throws BeansException {
		// pv如果之前解析过，就肯定会有对应的resolvedTokens
		PropertyTokenHolder tokens = (PropertyTokenHolder) pv.resolvedTokens;
		if (tokens == null) {
			// pv之前没有解析过，就需要第一次解析
			String propertyName = pv.getName();
			AbstractNestablePropertyAccessor nestedPa;
			try {
				// 根据属性名获取BeanWrapImpl对象，支持多重属性的递归分析处理
				// 如果不包含 "."、"["、"]" 那么返回的nestedPa就是this对象，因为不存在嵌套路局和集合属性
				nestedPa = getPropertyAccessorForPropertyPath(propertyName);
			}
			catch (NotReadablePropertyException ex) {
				throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
						"Nested property in path '" + propertyName + "' does not exist", ex);
			}
			// getFinalPath(nestedPa, propertyName) 获取属性路径中的最后的嵌套路径，当没有嵌套路径，就是原始值
			// getPropertyNameTokens
			// 简单的name,例如一个Bean有Car属性 {actualName:"car",canonicalName:"car",keys:null}
			// 嵌套的，一个Bean有Hobbies集合属性 {actualName:"hobbies",canonicalName:"hobbie[1]",keys:"1"}
			tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
			if (nestedPa == this) {
				// 没有嵌套属性，就要给当前根this，设置已经解析过的Tokens
				pv.getOriginalPropertyValue().resolvedTokens = tokens;
			}
			// 经过上面的递归后，获取到最终需要操作的属性的对象，下面将根据该属性对象，获取最终要操作的内嵌对象的属性，
			// 生成PropertyTokenHolder，内省设置属性值
			nestedPa.setPropertyValue(tokens, pv); // 开始处理
		}
		// 在setPropertyvalue中，特别设置了resolvedTokens
		else {
			setPropertyValue(tokens, pv);
		}
	}

	protected void setPropertyValue(PropertyTokenHolder tokens, PropertyValue pv) throws BeansException {
		if (tokens.keys != null) {
			// 拥有过key的属性，即集合属性
			// hobbies[1,2,3]
			processKeyedProperty(tokens, pv);
		}
		else {
			// 局部变量，非集合属性
			// car.brand
			processLocalProperty(tokens, pv);
		}
	}

	@SuppressWarnings("unchecked")
	private void processKeyedProperty(PropertyTokenHolder tokens, PropertyValue pv) {
		Object propValue = getPropertyHoldingValue(tokens);
		// 获取对应集合属性的处理器
		PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
		if (ph == null) {
			throw new InvalidPropertyException(
					getRootClass(), this.nestedPath + tokens.actualName, "No property handler found");
		}
		Assert.state(tokens.keys != null, "No token keys");
		String lastKey = tokens.keys[tokens.keys.length - 1];

		// 数组类型
		if (propValue.getClass().isArray()) {
			Class<?> requiredType = propValue.getClass().getComponentType();
			int arrayIndex = Integer.parseInt(lastKey);
			Object oldValue = null;
			try {
				if (isExtractOldValueForEditor() && arrayIndex < Array.getLength(propValue)) {
					oldValue = Array.get(propValue, arrayIndex);
				}
				// 利用 TypeConverterDelegate 完成数据转换
				Object convertedValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
						requiredType, ph.nested(tokens.keys.length));
				int length = Array.getLength(propValue);
				// 如果用户要求设置的索引位置arrayIndex已经超过当前数组属性的长度，并且小于自动扩容长度2147483647的限制
				if (arrayIndex >= length && arrayIndex < this.autoGrowCollectionLimit) {
					// 尝试进行数组扩容
					Class<?> componentType = propValue.getClass().getComponentType();
					Object newArray = Array.newInstance(componentType, arrayIndex + 1);
					System.arraycopy(propValue, 0, newArray, 0, length);
					int lastKeyIndex = tokens.canonicalName.lastIndexOf('[');
					String propName = tokens.canonicalName.substring(0, lastKeyIndex);
					setPropertyValue(propName, newArray);
					propValue = getPropertyValue(propName); // 核心之一：取出元素的值
				}
				// 将数组属性propValue的用户指定位置arrayIndex上设置convertedValue -- 到这里就已经完成了设置value的办法
				Array.set(propValue, arrayIndex, convertedValue);
			}
			catch (IndexOutOfBoundsException ex) {
				throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
						"Invalid array index in property path '" + tokens.canonicalName + "'", ex);
			}
		}

		else if (propValue instanceof List) {
			Class<?> requiredType = ph.getCollectionType(tokens.keys.length);
			List<Object> list = (List<Object>) propValue;
			int index = Integer.parseInt(lastKey);
			Object oldValue = null;
			if (isExtractOldValueForEditor() && index < list.size()) {
				oldValue = list.get(index);
			}
			Object convertedValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
					requiredType, ph.nested(tokens.keys.length));
			int size = list.size();
			if (index >= size && index < this.autoGrowCollectionLimit) {
				for (int i = size; i < index; i++) {
					try {
						list.add(null);
					}
					catch (NullPointerException ex) {
						throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
								"Cannot set element with index " + index + " in List of size " +
								size + ", accessed using property path '" + tokens.canonicalName +
								"': List does not support filling up gaps with null elements");
					}
				}
				list.add(convertedValue);
			}
			else {
				try {
					list.set(index, convertedValue);
				}
				catch (IndexOutOfBoundsException ex) {
					throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
							"Invalid list index in property path '" + tokens.canonicalName + "'", ex);
				}
			}
		}

		else if (propValue instanceof Map) {
			Class<?> mapKeyType = ph.getMapKeyType(tokens.keys.length);
			Class<?> mapValueType = ph.getMapValueType(tokens.keys.length);
			Map<Object, Object> map = (Map<Object, Object>) propValue;
			// IMPORTANT: Do not pass full property name in here - property editors
			// must not kick in for map keys but rather only for map values.
			TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
			Object convertedMapKey = convertIfNecessary(null, null, lastKey, mapKeyType, typeDescriptor);
			Object oldValue = null;
			if (isExtractOldValueForEditor()) {
				oldValue = map.get(convertedMapKey);
			}
			// Pass full property name and old value in here, since we want full
			// conversion ability for map values.
			Object convertedMapValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
					mapValueType, ph.nested(tokens.keys.length));
			map.put(convertedMapKey, convertedMapValue);
		}

		else {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Property referenced in indexed property path '" + tokens.canonicalName +
					"' is neither an array nor a List nor a Map; returned value was [" + propValue + "]");
		}
	}

	private Object getPropertyHoldingValue(PropertyTokenHolder tokens) {
		// Apply indexes and map keys: fetch value for all keys but the last one.
		Assert.state(tokens.keys != null, "No token keys");
		// PropertyTokenHolder类用以 copy tokens 的值
		PropertyTokenHolder getterTokens = new PropertyTokenHolder(tokens.actualName);
		getterTokens.canonicalName = tokens.canonicalName;
		getterTokens.keys = new String[tokens.keys.length - 1];
		System.arraycopy(tokens.keys, 0, getterTokens.keys, 0, tokens.keys.length - 1);

		Object propValue;
		try {
			propValue = getPropertyValue(getterTokens);
		}
		catch (NotReadablePropertyException ex) {
			throw new NotWritablePropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Cannot access indexed value in property referenced " +
					"in indexed property path '" + tokens.canonicalName + "'", ex);
		}

		if (propValue == null) {
			// null map value case
			if (isAutoGrowNestedPaths()) {
				int lastKeyIndex = tokens.canonicalName.lastIndexOf('[');
				getterTokens.canonicalName = tokens.canonicalName.substring(0, lastKeyIndex);
				propValue = setDefaultValue(getterTokens);
			}
			else {
				throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
						"Cannot access indexed value in property referenced " +
						"in indexed property path '" + tokens.canonicalName + "': returned null");
			}
		}
		return propValue;
	}

	private void processLocalProperty(PropertyTokenHolder tokens, PropertyValue pv) {
		// 1. 获取属性处理器，ph能够帮助我们判断属性是否可写、获取属性值、设置属性值
		PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
		if (ph == null || !ph.isWritable()) {
			// 1.1 ph为空，或者属性不可写都报错
			if (pv.isOptional()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Ignoring optional value for property '" + tokens.actualName +
							"' - property not found on bean class [" + getRootClass().getName() + "]");
				}
				return;
			}
			if (this.suppressNotWritablePropertyException) {
				// Optimization for common ignoreUnknown=true scenario since the
				// exception would be caught and swallowed higher up anyway...
				// 1.2suppressNotWritablePropertyException为true,表示抑制不可写属性异常,否则后面抛出异常
				return;
			}
			throw createNotWritablePropertyException(tokens.canonicalName);
		}

		Object oldValue = null;
		try {
			Object originalValue = pv.getValue(); // 获取用户注入的原始值
			Object valueToApply = originalValue; // 需要被注入的属性值
			// pv的conversionNecessary一般为null，需要的主动设置
			// 为null时,表示没有进行过转换
			// 为true时,表示已经转换过,且前后值不相同
			// 为false时,表示已经转换过,不过转换后还是原来那个值
			if (!Boolean.FALSE.equals(pv.conversionNecessary)) {
				// 2.1 pv的isConverted默认是false，即还未转换的
				// 若已转换，直接获取转换值
				if (pv.isConverted()) {
					valueToApply = pv.getConvertedValue();
				}
				// 2.2 对于没有转换的
				else {
					if (isExtractOldValueForEditor() && ph.isReadable()) {
						try {
							// 2.2.1 若开启提取oldValue的功能，且ph是可读的；就提取出oldValue,即原始值
							oldValue = ph.getValue();
						}
						catch (Exception ex) {
							if (ex instanceof PrivilegedActionException) {
								ex = ((PrivilegedActionException) ex).getException();
							}
							if (logger.isDebugEnabled()) {
								logger.debug("Could not read previous value of property '" +
										this.nestedPath + tokens.canonicalName + "'", ex);
							}
						}
					}
					// 2.3 核心 -- ph.toTypeDescriptor() 获取的是通过局部变量处理器获取局部变量的类型
					// 这里是获取转换后的结果，用Object接受
					valueToApply = convertForProperty(tokens.canonicalName, oldValue, originalValue, ph.toTypeDescriptor());
				}
				// 2.4 设置conversionNecessary的值，只要转换结果和原始结果不等，就已经转换了
				pv.getOriginalPropertyValue().conversionNecessary = (valueToApply != originalValue);
			}
			// 3. 核心 -- 设置进入
			ph.setValue(valueToApply);
		}
		catch (TypeMismatchException ex) {
			throw ex;
		}
		catch (InvocationTargetException ex) {
			PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(
					getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
			if (ex.getTargetException() instanceof ClassCastException) {
				throw new TypeMismatchException(propertyChangeEvent, ph.getPropertyType(), ex.getTargetException());
			}
			else {
				Throwable cause = ex.getTargetException();
				if (cause instanceof UndeclaredThrowableException) {
					// May happen e.g. with Groovy-generated methods
					cause = cause.getCause();
				}
				throw new MethodInvocationException(propertyChangeEvent, cause);
			}
		}
		catch (Exception ex) {
			PropertyChangeEvent pce = new PropertyChangeEvent(
					getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
			throw new MethodInvocationException(pce, ex);
		}
	}

	@Override
	@Nullable
	public Class<?> getPropertyType(String propertyName) throws BeansException {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.getPropertyType();
			}
			else {
				// Maybe an indexed/mapped property...
				Object value = getPropertyValue(propertyName);
				if (value != null) {
					return value.getClass();
				}
				// Check to see if there is a custom editor,
				// which might give an indication on the desired target type.
				Class<?> editorType = guessPropertyTypeFromEditors(propertyName);
				if (editorType != null) {
					return editorType;
				}
			}
		}
		catch (InvalidPropertyException ex) {
			// Consider as not determinable.
		}
		return null;
	}

	@Override
	@Nullable
	public TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException {
		try {
			AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
			String finalPath = getFinalPath(nestedPa, propertyName);
			PropertyTokenHolder tokens = getPropertyNameTokens(finalPath);
			PropertyHandler ph = nestedPa.getLocalPropertyHandler(tokens.actualName);
			if (ph != null) {
				if (tokens.keys != null) {
					if (ph.isReadable() || ph.isWritable()) {
						return ph.nested(tokens.keys.length);
					}
				}
				else {
					if (ph.isReadable() || ph.isWritable()) {
						return ph.toTypeDescriptor();
					}
				}
			}
		}
		catch (InvalidPropertyException ex) {
			// Consider as not determinable.
		}
		return null;
	}

	@Override
	public boolean isReadableProperty(String propertyName) {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.isReadable();
			}
			else {
				// Maybe an indexed/mapped property...
				getPropertyValue(propertyName);
				return true;
			}
		}
		catch (InvalidPropertyException ex) {
			// Cannot be evaluated, so can't be readable.
		}
		return false;
	}

	@Override
	public boolean isWritableProperty(String propertyName) {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.isWritable();
			}
			else {
				// Maybe an indexed/mapped property...
				getPropertyValue(propertyName);
				return true;
			}
		}
		catch (InvalidPropertyException ex) {
			// Cannot be evaluated, so can't be writable.
		}
		return false;
	}

	@Nullable
	private Object convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
			@Nullable Object newValue, @Nullable Class<?> requiredType, @Nullable TypeDescriptor td)
			throws TypeMismatchException {

		Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");
		try {
			// 核心利用，利用委托的类型转换器进行转换
			// 最终还是用到啦 -- 数据绑定的神仙工具 TypeConverterDelegate
			// TypeConverterDelegate 会先尝试用属性编辑器PropertyEditor进行转换,不行,再用注册的Converter进行转换
			return this.typeConverterDelegate.convertIfNecessary(propertyName, oldValue, newValue, requiredType, td);
		}
		catch (ConverterNotFoundException | IllegalStateException ex) {
			PropertyChangeEvent pce =
					new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
			throw new ConversionNotSupportedException(pce, requiredType, ex);
		}
		catch (ConversionException | IllegalArgumentException ex) {
			PropertyChangeEvent pce =
					new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
			throw new TypeMismatchException(pce, requiredType, ex);
		}
	}

	@Nullable
	protected Object convertForProperty(
			String propertyName, @Nullable Object oldValue, @Nullable Object newValue, TypeDescriptor td)
			throws TypeMismatchException {
		// td是目标属性的类型描述符
		return convertIfNecessary(propertyName, oldValue, newValue, td.getType(), td);
	}

	@Override
	@Nullable
	public Object getPropertyValue(String propertyName) throws BeansException {
		AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
		return nestedPa.getPropertyValue(tokens);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	protected Object getPropertyValue(PropertyTokenHolder tokens) throws BeansException {
		/**
		 * 获取指定属性的目标值
		 */
		String propertyName = tokens.canonicalName;
		String actualName = tokens.actualName;
		PropertyHandler ph = getLocalPropertyHandler(actualName); // 为当前的actualName获取属性处理器
		if (ph == null || !ph.isReadable()) {
			throw new NotReadablePropertyException(getRootClass(), this.nestedPath + propertyName);
		}
		try {
			Object value = ph.getValue();
			// tokens.keys 说明是嵌套属性是数组或集合，例如 map[key].age map结构中的key对应的value的age属性，其中map[key]就是这里处理的tokens
			if (tokens.keys != null) {
				if (value == null) {
					// 有token.keys但解析出的结果value为空，就需要检查是否允许自动增长空的属性，否则就报错
					if (isAutoGrowNestedPaths()) {
						value = setDefaultValue(new PropertyTokenHolder(tokens.actualName));
					}
					else {
						throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
								"Cannot access indexed value of property referenced in indexed " +
										"property path '" + propertyName + "': returned null");
					}
				}
				StringBuilder indexedPropertyName = new StringBuilder(tokens.actualName);
				// apply indexes and map keys
				for (int i = 0; i < tokens.keys.length; i++) {
					String key = tokens.keys[i];
					if (value == null) {
						throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
								"Cannot access indexed value of property referenced in indexed " +
										"property path '" + propertyName + "': returned null");
					}
					// value是数组的话，直接获取对应的数组元素的值
					else if (value.getClass().isArray()) {
						int index = Integer.parseInt(key);
						value = growArrayIfNecessary(value, index, indexedPropertyName.toString());
						value = Array.get(value, index);
					}
					// value是list的话，直接通过get获取
					else if (value instanceof List) {
						int index = Integer.parseInt(key);
						List<Object> list = (List<Object>) value;
						growCollectionIfNecessary(list, index, indexedPropertyName.toString(), ph, i + 1);
						value = list.get(index);
					}
					// value是set的话，只能通过迭代器比较，
					else if (value instanceof Set) {
						// Apply index to Iterator in case of a Set.
						Set<Object> set = (Set<Object>) value;
						int index = Integer.parseInt(key);
						if (index < 0 || index >= set.size()) {
							throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
									"Cannot get element with index " + index + " from Set of size " +
											set.size() + ", accessed using property path '" + propertyName + "'");
						}
						Iterator<Object> it = set.iterator();
						for (int j = 0; it.hasNext(); j++) {
							Object elem = it.next();
							if (j == index) {
								value = elem;
								break;
							}
						}
					}
					// value属于map的话，就ton过map.get()获取对应的值
					else if (value instanceof Map) {
						Map<Object, Object> map = (Map<Object, Object>) value;
						Class<?> mapKeyType = ph.getResolvableType().getNested(i + 1).asMap().resolveGeneric(0);
						// IMPORTANT: Do not pass full property name in here - property editors
						// must not kick in for map keys but rather only for map values.
						TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
						Object convertedMapKey = convertIfNecessary(null, null, key, mapKeyType, typeDescriptor);
						value = map.get(convertedMapKey);
					}
					// 不属于集合或map，但又keys值，就抛出异常
					else {
						throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
								"Property referenced in indexed property path '" + propertyName +
										"' is neither an array nor a List nor a Set nor a Map; returned value was [" + value + "]");
					}
					indexedPropertyName.append(PROPERTY_KEY_PREFIX).append(key).append(PROPERTY_KEY_SUFFIX);
				}
			}
			// ① 没有keys直接返回value
			// ② 有keys，还需要从集合或map中获取对应的value
			return value;
		}
		catch (IndexOutOfBoundsException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Index of out of bounds in property path '" + propertyName + "'", ex);
		}
		catch (NumberFormatException | TypeMismatchException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Invalid index in property path '" + propertyName + "'", ex);
		}
		catch (InvocationTargetException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Getter for property '" + actualName + "' threw exception", ex);
		}
		catch (Exception ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Illegal attempt to get property '" + actualName + "' threw exception", ex);
		}
	}


	/**
	 * Return the {@link PropertyHandler} for the specified {@code propertyName}, navigating
	 * if necessary. Return {@code null} if not found rather than throwing an exception.
	 * @param propertyName the property to obtain the descriptor for
	 * @return the property descriptor for the specified property,
	 * or {@code null} if not found
	 * @throws BeansException in case of introspection failure
	 */
	@Nullable
	protected PropertyHandler getPropertyHandler(String propertyName) throws BeansException {
		Assert.notNull(propertyName, "Property name must not be null");
		AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		return nestedPa.getLocalPropertyHandler(getFinalPath(nestedPa, propertyName));
	}

	/**
	 * Return a {@link PropertyHandler} for the specified local {@code propertyName}.
	 * Only used to reach a property available in the current context.
	 * @param propertyName the name of a local property
	 * @return the handler for that property, or {@code null} if it has not been found
	 */
	@Nullable
	protected abstract PropertyHandler getLocalPropertyHandler(String propertyName);

	/**
	 * Create a new nested property accessor instance.
	 * Can be overridden in subclasses to create a PropertyAccessor subclass.
	 * @param object the object wrapped by this PropertyAccessor
	 * @param nestedPath the nested path of the object
	 * @return the nested PropertyAccessor instance
	 */
	protected abstract AbstractNestablePropertyAccessor newNestedPropertyAccessor(Object object, String nestedPath);

	/**
	 * Create a {@link NotWritablePropertyException} for the specified property.
	 */
	protected abstract NotWritablePropertyException createNotWritablePropertyException(String propertyName);


	private Object growArrayIfNecessary(Object array, int index, String name) {
		if (!isAutoGrowNestedPaths()) {
			return array;
		}
		int length = Array.getLength(array);
		if (index >= length && index < this.autoGrowCollectionLimit) {
			Class<?> componentType = array.getClass().getComponentType();
			Object newArray = Array.newInstance(componentType, index + 1);
			System.arraycopy(array, 0, newArray, 0, length);
			for (int i = length; i < Array.getLength(newArray); i++) {
				Array.set(newArray, i, newValue(componentType, null, name));
			}
			setPropertyValue(name, newArray);
			Object defaultValue = getPropertyValue(name);
			Assert.state(defaultValue != null, "Default value must not be null");
			return defaultValue;
		}
		else {
			return array;
		}
	}

	private void growCollectionIfNecessary(Collection<Object> collection, int index, String name,
			PropertyHandler ph, int nestingLevel) {

		if (!isAutoGrowNestedPaths()) {
			return;
		}
		int size = collection.size();
		if (index >= size && index < this.autoGrowCollectionLimit) {
			Class<?> elementType = ph.getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
			if (elementType != null) {
				for (int i = collection.size(); i < index + 1; i++) {
					collection.add(newValue(elementType, null, name));
				}
			}
		}
	}

	/**
	 * Get the last component of the path. Also works if not nested.
	 * @param pa property accessor to work on
	 * @param nestedPath property path we know is nested
	 * @return last component of the path (the property on the target bean)
	 */
	protected String getFinalPath(AbstractNestablePropertyAccessor pa, String nestedPath) {
		// 没有嵌套属性直接返回
		if (pa == this) {
			return nestedPath;
		}
		// 如果有嵌套属性，从 获取pa的前缀嵌套属性长度+1 开始截取也可以截取到最后的属性
		// 例如: nestedPath=car.brand, car对象的pa嵌套路径就是car. -> 最终结果就是brand
		return nestedPath.substring(PropertyAccessorUtils.getLastNestedPropertySeparatorIndex(nestedPath) + 1);
	}

	/**
	 * Recursively navigate to return a property accessor for the nested property path.
	 * @param propertyPath property path, which may be nested
	 * @return a property accessor for the target bean
	 */
	protected AbstractNestablePropertyAccessor getPropertyAccessorForPropertyPath(String propertyPath) {
		/**
		 * 为指定的属性路径获取属性访问器 -- 这是一个递归方法
		 * 例如 一个对应Man人对象，有一个Car车属性，Car属性本身就是车对象且有一个Brand字段表示车的品牌
		 * 用户通过 new BeanWrapperImpl(new Man()) 表示根对象就是Man
		 * 然后通过 setPropertyValue("car.brand","长安") 那么这里就是递归 car -> brand
		 * 最后找到Car的属性中关于Brand的AbstractNestablePropertyAccessor
		 *
		 * -- 进来的时候是 this是Man对象的AbstractNestablePropertyAccessor
		 * -- 最后返回的是 Car对象的AbstractNestablePropertyAccessor
 		 */

		// 1. 确定给定属性路径中的第一个嵌套属性分隔符，忽略键中的点（如“map[my.key]”）
		// 例如 car.brand 返回的pos就是3,而不是-1
		// 而如 map[my.key] 返回的pos就是-1
		int pos = PropertyAccessorUtils.getFirstNestedPropertySeparatorIndex(propertyPath);
		// pos 不等于-1，表示有嵌套属性，且为第一个嵌套属性
		if (pos > -1) {
			String nestedProperty = propertyPath.substring(0, pos); // 截取嵌套属性之前的值,如car值
			String nestedPath = propertyPath.substring(pos + 1);// 截取嵌套属性,如brand值
			// 截取属性nestedProperty 继续向下查找
			// 即获取嵌套的第一层的属性的 AbstractNestablePropertyAccessor
			// 例如 this 的rootObject就是Mac对象,传入propertyPath为car.brand,那么这里nestedProperty就是car
			// 返回的 nestedPa 就是 Mac对线的car成员变量
			AbstractNestablePropertyAccessor nestedPa = getNestedPropertyAccessor(nestedProperty);
			// 然后通过第一层属性的 AbstractNestablePropertyAccessor 向下第二层属性的getPropertyAccessorForPropertyPath
			return nestedPa.getPropertyAccessorForPropertyPath(nestedPath);
		}
		// pos 等于-1，表示没有其那套属性，直接返回this
		else {
			return this;
		}
	}

	/**
	 * Retrieve a Property accessor for the given nested property.
	 * Create a new one if not found in the cache.
	 * <p>Note: Caching nested PropertyAccessors is necessary now,
	 * to keep registered custom editors for nested properties.
	 * @param nestedProperty property to create the PropertyAccessor for
	 * @return the PropertyAccessor instance, either cached or newly created
	 */
	private AbstractNestablePropertyAccessor getNestedPropertyAccessor(String nestedProperty) {
		if (this.nestedPropertyAccessors == null) {
			this.nestedPropertyAccessors = new HashMap<>();
		}
		// Get value of bean property. 从Bean属性中获取值
		PropertyTokenHolder tokens = getPropertyNameTokens(nestedProperty);// 解析嵌套属性和数组属性
		String canonicalName = tokens.canonicalName;
		Object value = getPropertyValue(tokens); // 从属性中获取指定的属性值
		if (value == null || (value instanceof Optional && !((Optional<?>) value).isPresent())) {
			// 是否允许空的路径属性
			if (isAutoGrowNestedPaths()) {
				value = setDefaultValue(tokens);
			}
			else {
				throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + canonicalName);
			}
		}

		// Lookup cached sub-PropertyAccessor, create new one if not found.
		// 查找缓存的PropertyAccessor，如果找不到，则创建一个新的。
		AbstractNestablePropertyAccessor nestedPa = this.nestedPropertyAccessors.get(canonicalName);
		if (nestedPa == null || nestedPa.getWrappedInstance() != ObjectUtils.unwrapOptional(value)) {
			if (logger.isTraceEnabled()) {
				logger.trace("Creating new nested " + getClass().getSimpleName() + " for property '" + canonicalName + "'");
			}
			// 创建一个BeanWrapperImpl，需要包装的的当前嵌套的属性示例值，然后嵌套路径 car.
			nestedPa = newNestedPropertyAccessor(value, this.nestedPath + canonicalName + NESTED_PROPERTY_SEPARATOR);
			// Inherit all type-specific PropertyEditors.
			copyDefaultEditorsTo(nestedPa); // 将当前父类的默认属性编辑器继承给nestedPa
			copyCustomEditorsTo(nestedPa, canonicalName); // 将定制的
			this.nestedPropertyAccessors.put(canonicalName, nestedPa); // 存入当前的嵌套的属性缓存
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Using cached nested property accessor for property '" + canonicalName + "'");
			}
		}
		return nestedPa;
	}

	private Object setDefaultValue(PropertyTokenHolder tokens) {
		PropertyValue pv = createDefaultPropertyValue(tokens);
		setPropertyValue(tokens, pv);
		Object defaultValue = getPropertyValue(tokens);
		Assert.state(defaultValue != null, "Default value must not be null");
		return defaultValue;
	}

	private PropertyValue createDefaultPropertyValue(PropertyTokenHolder tokens) {
		TypeDescriptor desc = getPropertyTypeDescriptor(tokens.canonicalName);
		if (desc == null) {
			throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Could not determine property type for auto-growing a default value");
		}
		Object defaultValue = newValue(desc.getType(), desc, tokens.canonicalName);
		return new PropertyValue(tokens.canonicalName, defaultValue);
	}

	private Object newValue(Class<?> type, @Nullable TypeDescriptor desc, String name) {
		try {
			if (type.isArray()) {
				Class<?> componentType = type.getComponentType();
				// TODO - only handles 2-dimensional arrays
				if (componentType.isArray()) {
					Object array = Array.newInstance(componentType, 1);
					Array.set(array, 0, Array.newInstance(componentType.getComponentType(), 0));
					return array;
				}
				else {
					return Array.newInstance(componentType, 0);
				}
			}
			else if (Collection.class.isAssignableFrom(type)) {
				TypeDescriptor elementDesc = (desc != null ? desc.getElementTypeDescriptor() : null);
				return CollectionFactory.createCollection(type, (elementDesc != null ? elementDesc.getType() : null), 16);
			}
			else if (Map.class.isAssignableFrom(type)) {
				TypeDescriptor keyDesc = (desc != null ? desc.getMapKeyTypeDescriptor() : null);
				return CollectionFactory.createMap(type, (keyDesc != null ? keyDesc.getType() : null), 16);
			}
			else {
				Constructor<?> ctor = type.getDeclaredConstructor();
				if (Modifier.isPrivate(ctor.getModifiers())) {
					throw new IllegalAccessException("Auto-growing not allowed with private constructor: " + ctor);
				}
				return BeanUtils.instantiateClass(ctor);
			}
		}
		catch (Throwable ex) {
			throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + name,
					"Could not instantiate property type [" + type.getName() + "] to auto-grow nested property path", ex);
		}
	}

	/**
	 * 将给定的属性名解析为相应的属性名Token
	 * @param propertyName the property name to parse
	 * @return representation of the parsed property tokens
	 */
	private PropertyTokenHolder getPropertyNameTokens(String propertyName) {
		// 对brand属性进行风格
		// 这里我们举例为 car.brand
		// 还有一中情况例如 hobbies[1] 或者 maps["xx"]
		// 就需要分割
		// 然后存入到PropertyTokenHolder

		String actualName = null;
		List<String> keys = new ArrayList<>(2);
		int searchIndex = 0;
		while (searchIndex != -1) {
			int keyStart = propertyName.indexOf(PROPERTY_KEY_PREFIX, searchIndex);
			searchIndex = -1;
			if (keyStart != -1) {
				// propertyName 中包含 "[",进入if代码块
				// 调用getPropertyNameKeyEnd查找这个 "[" 对应的 "]" 的位置
				int keyEnd = getPropertyNameKeyEnd(propertyName, keyStart + PROPERTY_KEY_PREFIX.length());
				// 有对应的 "]"
				if (keyEnd != -1) {
					if (actualName == null) {
						// 获取真实名字，例如 propertyName = hobbies[1] 的 actualName = hobbies
						actualName = propertyName.substring(0, keyStart);
					}
					// 同样 propertyName = hobbies[1] 的 key = 1
					// 或者为map结构的获取， propertyName = maps["xx"] 的 key = "xx"
					String key = propertyName.substring(keyStart + PROPERTY_KEY_PREFIX.length(), keyEnd);
					if (key.length() > 1 && (key.startsWith("'") && key.endsWith("'")) ||
							(key.startsWith("\"") && key.endsWith("\""))) {
						key = key.substring(1, key.length() - 1);
					}
					keys.add(key); //  添加到keys中
					searchIndex = keyEnd + PROPERTY_KEY_SUFFIX.length(); // 继续递归
				}
			}
		}
		PropertyTokenHolder tokens = new PropertyTokenHolder(actualName != null ? actualName : propertyName);
		if (!keys.isEmpty()) {
			tokens.canonicalName += PROPERTY_KEY_PREFIX +
					StringUtils.collectionToDelimitedString(keys, PROPERTY_KEY_SUFFIX + PROPERTY_KEY_PREFIX) +
					PROPERTY_KEY_SUFFIX;
			tokens.keys = StringUtils.toStringArray(keys);
		}
		return tokens;
	}

	private int getPropertyNameKeyEnd(String propertyName, int startIndex) {
		int unclosedPrefixes = 0;
		int length = propertyName.length();
		for (int i = startIndex; i < length; i++) {
			switch (propertyName.charAt(i)) {
				case PropertyAccessor.PROPERTY_KEY_PREFIX_CHAR:
					// 属性name包含 [
					unclosedPrefixes++; // [ 括号数量
					break;
				case PropertyAccessor.PROPERTY_KEY_SUFFIX_CHAR:
					if (unclosedPrefixes == 0) {
						// [ 和 ] 数量相同
						return i; // 返回 ] 的位置
					} else {
						// 发现了一个 ]，但是和已经找到的 ] 不匹配，继续递减
						unclosedPrefixes--;
					}
					break;
			}
		}
		return -1;
	}


	@Override
	public String toString() {
		String className = getClass().getName();
		if (this.wrappedObject == null) {
			return className + ": no wrapped object set";
		}
		return className + ": wrapping object [" + ObjectUtils.identityToString(this.wrappedObject) + ']';
	}


	/**
	 * A handler for a specific property.
	 */
	protected abstract static class PropertyHandler {
		/**
		 * 对于具体属性的处理器
		 */

		private final Class<?> propertyType; // 属性类型

		private final boolean readable; // 可写

		private final boolean writable; // 可读

		public PropertyHandler(Class<?> propertyType, boolean readable, boolean writable) {
			this.propertyType = propertyType;
			this.readable = readable;
			this.writable = writable;
		}

		public Class<?> getPropertyType() {
			return this.propertyType;
		}

		public boolean isReadable() {
			return this.readable;
		}

		public boolean isWritable() {
			return this.writable;
		}

		public abstract TypeDescriptor toTypeDescriptor(); // 转为TypeDescriptor

		public abstract ResolvableType getResolvableType(); // 获取解析的Type

		@Nullable
		public Class<?> getMapKeyType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(0);
		}

		@Nullable
		public Class<?> getMapValueType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(1);
		}

		@Nullable
		public Class<?> getCollectionType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
		}

		@Nullable
		public abstract TypeDescriptor nested(int level);

		@Nullable
		public abstract Object getValue() throws Exception; // 抽象方法 getValue()

		public abstract void setValue(@Nullable Object value) throws Exception; // 抽象方法 setValue()
	}


	/**
	 * Holder class used to store property tokens.
	 */
	protected static class PropertyTokenHolder {
		/*
		 * 持有实际name、canonicalName、keys
		 */

		public PropertyTokenHolder(String name) {
			this.actualName = name;
			this.canonicalName = name;
		}

		// 以 car.brand 为例
		// 最终 actualName="brand",canonicalName=null,keys=null
		// 以 car.list[1][2]

		// PropertyTokenHolder的作用是什么？
		// 这个类的作用是对属性访问表达式的细化和归类。比如这句代码：
		//
		// propertyAccessor.setPropertyValue("listMap[0][0]", "listMapValue00");
		//
		// 这句代码的含义是：为Apple的成员变量listMap的第0个元素：即为Map。然后向该Map里放入键值对：0(key)和listMapValue00(value)。所以listMap[0][0]一个属性访问表达式，它在PropertyTokenHolder类里存储如下：
		//
		//		canonicalName:listMap[0][0]：代表整个属性访问表达式
		//		actualName:listMap：仅包含最外层的属性名称
		//		keys:[0, 0]：数组的长度代表索引深度，各元素代表索引值

		public String actualName; // 真实的name

		public String canonicalName; // 候选的key

		@Nullable
		public String[] keys; // keys
	}

}
