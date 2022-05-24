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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * {@link ConfigurablePropertyAccessor} implementation that directly accesses
 * instance fields. Allows for direct binding to fields instead of going through
 * JavaBean setters.
 *
 * <p>As of Spring 4.2, the vast majority of the {@link BeanWrapper} features have
 * been merged to {@link AbstractPropertyAccessor}, which means that property
 * traversal as well as collections and map access is now supported here as well.
 *
 * <p>A DirectFieldAccessor's default for the "extractOldValueForEditor" setting
 * is "true", since a field can always be read without side effects.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.0
 * @see #setExtractOldValueForEditor
 * @see BeanWrapper
 * @see org.springframework.validation.DirectFieldBindingResult
 * @see org.springframework.validation.DataBinder#initDirectFieldAccess()
 */
public class DirectFieldAccessor extends AbstractNestablePropertyAccessor {
	// DirectFieldAccessor
	// 它继承自AbstractNestablePropertyAccessor，所以它肯定也就可以处理级联属性和集合数组值了。
	// （请注意，在Spring4.2之后支持，之前是不支持的~）

	// 缓存着每个字段的处理器FieldPropertyHandler
	// ReflectionUtils.findField()根据String去找到Field对象的
	private final Map<String, FieldPropertyHandler> fieldMap = new HashMap<>();


	/**
	 * Create a new DirectFieldAccessor for the given object.
	 * @param object the object wrapped by this DirectFieldAccessor
	 */
	public DirectFieldAccessor(Object object) {
		super(object);
	}

	/**
	 * Create a new DirectFieldAccessor for the given object,
	 * registering a nested path that the object is in.
	 * @param object the object wrapped by this DirectFieldAccessor
	 * @param nestedPath the nested path of the object
	 * @param parent the containing DirectFieldAccessor (must not be {@code null})
	 */
	protected DirectFieldAccessor(Object object, String nestedPath, DirectFieldAccessor parent) {
		super(object, nestedPath, parent);
	}


	@Override
	@Nullable
	protected FieldPropertyHandler getLocalPropertyHandler(String propertyName) {
		FieldPropertyHandler propertyHandler = this.fieldMap.get(propertyName);
		if (propertyHandler == null) {
			Field field = ReflectionUtils.findField(getWrappedClass(), propertyName);
			if (field != null) {
				propertyHandler = new FieldPropertyHandler(field);
				this.fieldMap.put(propertyName, propertyHandler);
			}
		}
		return propertyHandler;
	}

	@Override
	protected DirectFieldAccessor newNestedPropertyAccessor(Object object, String nestedPath) {
		return new DirectFieldAccessor(object, nestedPath, this);
	}

	@Override
	protected NotWritablePropertyException createNotWritablePropertyException(String propertyName) {
		PropertyMatches matches = PropertyMatches.forField(propertyName, getRootClass());
		throw new NotWritablePropertyException(getRootClass(), getNestedPath() + propertyName,
				matches.buildErrorMessage(), matches.getPossibleMatches());
	}


	private class FieldPropertyHandler extends PropertyHandler {
		// // 字段field属性处理器，使用内部类实现PropertyHandler ~~~

		private final Field field;

		public FieldPropertyHandler(Field field) {
			super(field.getType(), true, true);
			this.field = field;
		}

		@Override
		public TypeDescriptor toTypeDescriptor() {
			return new TypeDescriptor(this.field);
		}

		@Override
		public ResolvableType getResolvableType() {
			return ResolvableType.forField(this.field);
		}

		@Override
		@Nullable
		public TypeDescriptor nested(int level) {
			return TypeDescriptor.nested(this.field, level);
		}

		// 它的功能是直接操作Bean的属性值，而代替使用get/set方法去操作Bean。它的实现原理就是简单的field.get(getWrappedInstance())和field.set(getWrappedInstance(), value)等。
		// 它处理级联属性的大致步骤是：
		//
		//遇上级联属性，先找出canonicalName
		//根据此canonicalName调用其field.get()拿到此字段的值~
		//若不为null（有初始值），那就继续解析此类型，循而往复即可~

		@Override
		@Nullable
		public Object getValue() throws Exception {
			try {
				ReflectionUtils.makeAccessible(this.field);
				return this.field.get(getWrappedInstance());
			}

			catch (IllegalAccessException ex) {
				throw new InvalidPropertyException(getWrappedClass(),
						this.field.getName(), "Field is not accessible", ex);
			}
		}

		@Override
		public void setValue(@Nullable Object value) throws Exception {
			try {
				ReflectionUtils.makeAccessible(this.field);
				this.field.set(getWrappedInstance(), value);
			}
			catch (IllegalAccessException ex) {
				throw new InvalidPropertyException(getWrappedClass(), this.field.getName(),
						"Field is not accessible", ex);
			}
		}
	}

}
