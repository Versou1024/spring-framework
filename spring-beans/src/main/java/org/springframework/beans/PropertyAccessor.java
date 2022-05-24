/*
 * Copyright 2002-2018 the original author or authors.
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

import java.util.Map;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;

/**
 * Common interface for classes that can access named properties
 * (such as bean properties of an object or fields in an object)
 * Serves as base interface for {@link BeanWrapper}.
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see BeanWrapper
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see PropertyAccessorFactory#forDirectFieldAccess
 */
public interface PropertyAccessor {
	/*
	 * 属性访问器接口：
	 * 可以被访问的命名属性（例如对象的属性或对象中的字段）的类的公共接口用作BeanWrapper的基本接口。
	 *
	 * API:
	 * 1、通过PropertyValue、或PropertvValues、或Mao<String,Object>、String+Object设置target的属性值
	 * 2、属性是否可写、是否可读
	 * 3、获取指定的name的属性类型、属性值、属性描述
	 */

	/**
	 * Path separator for nested properties.
	 * Follows normal Java conventions: getFoo().getBar() would be "foo.bar".
	 */
	String NESTED_PROPERTY_SEPARATOR = ".";
	// 简单的说就是级联属性的分隔符。
	// 比如foo.bar最终会调用getFoo().getBar()两个方法

	/**
	 * Path separator for nested properties.
	 * Follows normal Java conventions: getFoo().getBar() would be "foo.bar".
	 */
	char NESTED_PROPERTY_SEPARATOR_CHAR = '.'; // 嵌套属性的分割符号

	/**
	 * Marker that indicates the start of a property key for an
	 * indexed or mapped property like "person.addresses[0]".
	 */
	String PROPERTY_KEY_PREFIX = "[";
	// 代表角标index的符号  如person.addresses[0]  这样就可以把值放进集合/数组/Map里了

	/**
	 * Marker that indicates the start of a property key for an
	 * indexed or mapped property like "person.addresses[0]".
	 */
	char PROPERTY_KEY_PREFIX_CHAR = '[';

	/**
	 * Marker that indicates the end of a property key for an
	 * indexed or mapped property like "person.addresses[0]".
	 */
	String PROPERTY_KEY_SUFFIX = "]";

	/**
	 * Marker that indicates the end of a property key for an
	 * indexed or mapped property like "person.addresses[0]".
	 */
	char PROPERTY_KEY_SUFFIX_CHAR = ']';


	/**
	 * Determine whether the specified property is readable.
	 * <p>Returns {@code false} if the property doesn't exist.
	 * @param propertyName the property to check
	 * (may be a nested path and/or an indexed/mapped property)
	 * @return whether the property is readable
	 */
	boolean isReadableProperty(String propertyName);
	// 此属性是否可读。若属性不存在  返回false

	/**
	 * Determine whether the specified property is writable.
	 * <p>Returns {@code false} if the property doesn't exist.
	 * @param propertyName the property to check
	 * (may be a nested path and/or an indexed/mapped property)
	 * @return whether the property is writable
	 */
	boolean isWritableProperty(String propertyName);
	// 此出行是否可写。若属性不存在，返回false

	/**
	 * Determine the property type for the specified property,
	 * either checking the property descriptor or checking the value
	 * in case of an indexed or mapped element.
	 * @param propertyName the property to check
	 * (may be a nested path and/or an indexed/mapped property)
	 * @return the property type for the particular property,
	 * or {@code null} if not determinable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed
	 */
	@Nullable
	Class<?> getPropertyType(String propertyName) throws BeansException;
	// 获取对应属性的class类型

	/**
	 * Return a type descriptor for the specified property:
	 * preferably from the read method, falling back to the write method.
	 * @param propertyName the property to check
	 * (may be a nested path and/or an indexed/mapped property)
	 * @return the property type for the particular property,
	 * or {@code null} if not determinable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed
	 */
	@Nullable
	TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException;
	// 获取对应属性的类型描述符

	/**
	 * Get the current value of the specified property.
	 * @param propertyName the name of the property to get the value of
	 * (may be a nested path and/or an indexed/mapped property)
	 * @return the value of the property
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't readable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed
	 */
	@Nullable
	Object getPropertyValue(String propertyName) throws BeansException;
	// 获取对应属性的value值

	// 写操作

	/**
	 * Set the specified value as current property value.
	 * @param propertyName the name of the property to set the value of
	 * (may be a nested path and/or an indexed/mapped property)
	 * @param value the new value
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed or a type mismatch occurred
	 */
	void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;


	/**
	 * Set the specified value as current property value.
	 * @param pv an object containing the new property value
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed or a type mismatch occurred
	 */
	void setPropertyValue(PropertyValue pv) throws BeansException;

	/**
	 * Perform a batch update from a Map.
	 * <p>Bulk updates from PropertyValues are more powerful: This method is
	 * provided for convenience. Behavior will be identical to that of
	 * the {@link #setPropertyValues(PropertyValues)} method.
	 * @param map a Map to take properties from. Contains property value objects,
	 * keyed by property name
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyBatchUpdateException if one or more PropertyAccessExceptions
	 * occurred for specific properties during the batch update. This exception bundles
	 * all individual PropertyAccessExceptions. All other properties will have been
	 * successfully updated.
	 */
	void setPropertyValues(Map<?, ?> map) throws BeansException;

	/**
	 * The preferred way to perform a batch update.
	 * <p>Note that performing a batch update differs from performing a single update,
	 * in that an implementation of this class will continue to update properties
	 * if a <b>recoverable</b> error (such as a type mismatch, but <b>not</b> an
	 * invalid field name or the like) is encountered, throwing a
	 * {@link PropertyBatchUpdateException} containing all the individual errors.
	 * This exception can be examined later to see all binding errors.
	 * Properties that were successfully updated remain changed.
	 * <p>Does not allow unknown fields or invalid fields.
	 * @param pvs a PropertyValues to set on the target object
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyBatchUpdateException if one or more PropertyAccessExceptions
	 * occurred for specific properties during the batch update. This exception bundles
	 * all individual PropertyAccessExceptions. All other properties will have been
	 * successfully updated.
	 * @see #setPropertyValues(PropertyValues, boolean, boolean)
	 */
	void setPropertyValues(PropertyValues pvs) throws BeansException; //执行批处理更新，以更好地控制行为。

	/**
	 * Perform a batch update with more control over behavior.
	 * <p>Note that performing a batch update differs from performing a single update,
	 * in that an implementation of this class will continue to update properties
	 * if a <b>recoverable</b> error (such as a type mismatch, but <b>not</b> an
	 * invalid field name or the like) is encountered, throwing a
	 * {@link PropertyBatchUpdateException} containing all the individual errors.
	 * This exception can be examined later to see all binding errors.
	 * Properties that were successfully updated remain changed.
	 * @param pvs a PropertyValues to set on the target object
	 * @param ignoreUnknown should we ignore unknown properties (not found in the bean)
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyBatchUpdateException if one or more PropertyAccessExceptions
	 * occurred for specific properties during the batch update. This exception bundles
	 * all individual PropertyAccessExceptions. All other properties will have been
	 * successfully updated.
	 * @see #setPropertyValues(PropertyValues, boolean, boolean)
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown)
			throws BeansException;

	/**
	 * Perform a batch update with full control over behavior.
	 * <p>Note that performing a batch update differs from performing a single update,
	 * in that an implementation of this class will continue to update properties
	 * if a <b>recoverable</b> error (such as a type mismatch, but <b>not</b> an
	 * invalid field name or the like) is encountered, throwing a
	 * {@link PropertyBatchUpdateException} containing all the individual errors.
	 * This exception can be examined later to see all binding errors.
	 * Properties that were successfully updated remain changed.
	 * @param pvs a PropertyValues to set on the target object
	 * @param ignoreUnknown should we ignore unknown properties (not found in the bean)
	 * @param ignoreInvalid should we ignore invalid properties (found but not accessible)
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyBatchUpdateException if one or more PropertyAccessExceptions
	 * occurred for specific properties during the batch update. This exception bundles
	 * all individual PropertyAccessExceptions. All other properties will have been
	 * successfully updated.
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException;
	// 可控制是否接受非法的字段、value值扽  ignoreUnknown/ignoreInvalid分别对应非法属性和非法value值的处理策略~

}
