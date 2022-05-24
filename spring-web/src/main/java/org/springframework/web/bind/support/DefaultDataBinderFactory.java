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
 * Create a {@link WebRequestDataBinder} instance and initialize it with a
 * {@link WebBindingInitializer}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class DefaultDataBinderFactory implements WebDataBinderFactory {
	/*
	 * 实现 WebDataBinderFactory 作为默认的web数据绑定工厂
	 *
	 * 作用：
	 * 1、聚合用来初始化的WebDataBinder的WebBindingInitializer
	 */

	@Nullable
	private final WebBindingInitializer initializer; // 用来初始化加载DataBinder的初始化器


	/**
	 * Create a new {@code DefaultDataBinderFactory} instance.
	 * @param initializer for global data binder initialization
	 * (or {@code null} if none)
	 */
	public DefaultDataBinderFactory(@Nullable WebBindingInitializer initializer) {
		// 注意：这是唯一构造函数
		// 因此必须给定DataBinder的初始化器,完成配置的初始化操作
		this.initializer = initializer;
	}


	/**
	 * Create a new {@link WebDataBinder} for the given target object and
	 * initialize it through a {@link WebBindingInitializer}.
	 * @throws Exception in case of invalid state or arguments
	 */
	@Override
	@SuppressWarnings("deprecation")
	public final WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName) throws Exception {

		// 1.创建一个WebRequestDataBinder
		WebDataBinder dataBinder = createBinderInstance(target, objectName, webRequest);
		// 2. 如果初始化器initializer不为空,就用WebBindingInitializer来初始化WebRequestDataBinder的各项配置
		if (this.initializer != null) {
			this.initializer.initBinder(dataBinder, webRequest);
		}
		// 3. 留给子类的扩展方法 - 初始化@InitBinder方法
		initBinder(dataBinder, webRequest);
		// 4. 返回dataBinder
		return dataBinder;
	}

	/**
	 * Extension point to create the WebDataBinder instance.
	 * By default this is {@code WebRequestDataBinder}.
	 * @param target the binding target or {@code null} for type conversion only
	 * @param objectName the binding target object name
	 * @param webRequest the current request
	 * @throws Exception in case of invalid state or arguments
	 */
	protected WebDataBinder createBinderInstance(
			@Nullable Object target, String objectName, NativeWebRequest webRequest) throws Exception {
		//  子类可以复写，默认实现是WebRequestDataBinder
		//  比如子类ServletRequestDataBinderFactory就复写了，使用的new ExtendedServletRequestDataBinder(target, objectName)
		return new WebRequestDataBinder(target, objectName);
	}

	/**
	 * Extension point to further initialize the created data binder instance
	 * (e.g. with {@code @InitBinder} methods) after "global" initialization
	 * via {@link WebBindingInitializer}.
	 * @param dataBinder the data binder instance to customize
	 * @param webRequest the current request
	 * @throws Exception if initialization fails
	 */
	protected void initBinder(WebDataBinder dataBinder, NativeWebRequest webRequest)
			throws Exception {

	}

}
