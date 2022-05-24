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

package org.springframework.validation;

import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.lang.Nullable;

/**
 * Special implementation of the Errors and BindingResult interfaces,
 * supporting registration and evaluation of binding errors on value objects.
 * Performs direct field access instead of going through JavaBean getters.
 *
 * <p>Since Spring 4.1 this implementation is able to traverse nested fields.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see DataBinder#getBindingResult()
 * @see DataBinder#initDirectFieldAccess()
 * @see BeanPropertyBindingResult
 */
@SuppressWarnings("serial")
public class DirectFieldBindingResult extends AbstractPropertyBindingResult {

	@Nullable
	private final Object target; // 数据绑定的目标

	private final boolean autoGrowNestedPaths; // 自动增长路径

	@Nullable
	private transient ConfigurablePropertyAccessor directFieldAccessor; // 内部:使用directFieldAccessor做数据绑定


	/**
	 * Create a new DirectFieldBindingResult instance.
	 * @param target the target object to bind onto
	 * @param objectName the name of the target object
	 */
	public DirectFieldBindingResult(@Nullable Object target, String objectName) {
		this(target, objectName, true);
	}

	/**
	 * Create a new DirectFieldBindingResult instance.
	 * @param target the target object to bind onto
	 * @param objectName the name of the target object
	 * @param autoGrowNestedPaths whether to "auto-grow" a nested path that contains a null value
	 */
	public DirectFieldBindingResult(@Nullable Object target, String objectName, boolean autoGrowNestedPaths) {
		super(objectName);
		this.target = target;
		this.autoGrowNestedPaths = autoGrowNestedPaths;
	}


	@Override
	@Nullable
	public final Object getTarget() {
		return this.target;
	}

	/**
	 * Returns the DirectFieldAccessor that this instance uses.
	 * Creates a new one if none existed before.
	 * @see #createDirectFieldAccessor()
	 */
	@Override
	public final ConfigurablePropertyAccessor getPropertyAccessor() {
		// 获取 DirectFieldAccess 并做一些设置
		// 懒加载

		if (this.directFieldAccessor == null) {
			this.directFieldAccessor = createDirectFieldAccessor();
			this.directFieldAccessor.setExtractOldValueForEditor(true);
			this.directFieldAccessor.setAutoGrowNestedPaths(this.autoGrowNestedPaths);
		}
		return this.directFieldAccessor;
	}

	/**
	 * Create a new DirectFieldAccessor for the underlying target object.
	 * @see #getTarget()
	 */
	protected ConfigurablePropertyAccessor createDirectFieldAccessor() {

		// 1. 前提target存在
		if (this.target == null) {
			throw new IllegalStateException("Cannot access fields on null target instance '" + getObjectName() + "'");
		}
		// 2. 创建 DirectFieldAcces
		return PropertyAccessorFactory.forDirectFieldAccess(this.target);
	}

}
