/*
 * Copyright 2002-2017 the original author or authors.
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

import java.beans.PropertyDescriptor;

/**
 * The central interface of Spring's low-level JavaBeans infrastructure.
 *
 * <p>Typically not used directly but rather implicitly via a
 * {@link org.springframework.beans.factory.BeanFactory} or a
 * {@link org.springframework.validation.DataBinder}.
 *
 * <p>Provides operations to analyze and manipulate standard JavaBeans:
 * the ability to get and set property values (individually or in bulk),
 * get property descriptors, and query the readability/writability of properties.
 *
 * <p>This interface supports <b>nested properties</b> enabling the setting
 * of properties on subproperties to an unlimited depth.
 *
 * <p>A BeanWrapper's default for the "extractOldValueForEditor" setting
 * is "false", to avoid side effects caused by getter method invocations.
 * Turn this to "true" to expose present property values to custom editors.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 13 April 2001
 * @see PropertyAccessor
 * @see PropertyEditorRegistry
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.validation.BeanPropertyBindingResult
 * @see org.springframework.validation.DataBinder#initBeanPropertyAccess()
 */
public interface BeanWrapper extends ConfigurablePropertyAccessor {
	/*
	 * 实现 ConfigurablePropertyAccessor
	 * Bean包装器扩展API
	 * 1、获取包装的对象、包装对象的CLass、属性描述符
	 *
	 * 如果说上篇文章所说的PropertyAccessor你没有接触过和听过，那么本文即将要说的重点：BeanWrapper你应该多少有所耳闻吧~
	 * BeanWrapper可以简单的把它理解为：一个方便开发人员使用字符串来对Java Bean的属性执行get、set操作的工具。关于它的数据转换使用了如下两种机制：
	 * 		PropertyEditor：隶属于Java Bean规范。PropertyEditor只提供了String <-> Object的转换。
	 * 		ConversionService：Spring自3.0之后提供的替代PropertyEditor的机制（BeanWrapper在Spring的第一个版本就存在了~）
	 * 按照Spring官方文档的说法，当容器内没有注册ConversionService的时候，会退回使用PropertyEditor机制。言外之意：首选方案是ConversionService
	 * 其实了解的伙伴应该知道，这不是BeanWrapper的内容，而是父接口PropertyAccessor的内容
	 */

	/**
	 * Specify a limit for array and collection auto-growing.
	 * <p>Default is unlimited on a plain BeanWrapper.
	 * @since 4.1
	 */
	void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);
	// 指定数组和集合自动增长的限制。
	// 默认在普通 BeanWrapper 上是无限制的

	/**
	 * Return the limit for array and collection auto-growing.
	 * @since 4.1
	 */
	int getAutoGrowCollectionLimit(); // 返回数组和集合自动增长的限制。

	/**
	 * Return the bean instance wrapped by this object.
	 */
	Object getWrappedInstance(); // 返回此对象包装的 bean 实例。

	/**
	 * Return the type of the wrapped bean instance.
	 */
	Class<?> getWrappedClass(); // 返回包装的 bean 实例的类型

	/**
	 * Obtain the PropertyDescriptors for the wrapped object
	 * (as determined by standard JavaBeans introspection).
	 * @return the PropertyDescriptors for the wrapped object
	 */
	PropertyDescriptor[] getPropertyDescriptors(); // 获取包装对象的 PropertyDescriptors（由标准 JavaBeans 自省确定）。

	/**
	 * Obtain the property descriptor for a specific property
	 * of the wrapped object.
	 * @param propertyName the property to obtain the descriptor for
	 * (may be a nested path, but no indexed/mapped property)
	 * @return the property descriptor for the specified property
	 * @throws InvalidPropertyException if there is no such property
	 */
	PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;

}
