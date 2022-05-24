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

package org.springframework.web.bind.support;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * A factory for creating a {@link WebDataBinder} instance for a named target object.
 *
 * @author Arjen Poutsma
 * @since 3.1
 */
public interface WebDataBinderFactory {
	/*
	 * 专门提供给web环境的数据绑定工厂
	 *
	 * 方法：
	 * 1、createBinder
	 *
	 * 一般 WebDataBinderFactory + WebBindingInitializer 一起继承或者继承组合使用
	 * 原因：WebDataBinderFactory 用于定义创建的WebDataBinder的方法，但具体的初始化操作需要交给WebBindingInitializer
	 *
	 * 顾名思义它就是来创造一个WebDataBinder的工厂。
	 */

	/**
	 * Create a {@link WebDataBinder} for the given object.
	 * @param webRequest the current request
	 * @param target the object to create a data binder for,
	 * or {@code null} if creating a binder for a simple type
	 * @param objectName the name of the target object
	 * @return the created {@link WebDataBinder} instance, never null
	 * @throws Exception raised if the creation and initialization of the data binder fails
	 */
	WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName)
			throws Exception;

}
