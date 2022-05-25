/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.cors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by classes (usually HTTP request handlers) that
 * provides a {@link CorsConfiguration} instance based on the provided request.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 */
public interface CorsConfigurationSource {
	// 跨域配置源 -- CorsConfiguration Source -- 什么什么源的接口,一般都会提供一个getXxx的接口
	// 提供getCorsConfiguration()方法

	// 此接口方法的调用处有三个地方：
	//		AbstractHandlerMapping.getHandler()/getCorsConfiguration()	-- HandlerMapping
	//		CorsFilter.doFilterInternal()								-- 过滤器
	//		HandlerMappingIntrospector.getCorsConfiguration()			-- HandlerMapping 内省器

	/**
	 * Return a {@link CorsConfiguration} based on the incoming request.
	 * @return the associated {@link CorsConfiguration}, or {@code null} if none
	 */
	@Nullable
	CorsConfiguration getCorsConfiguration(HttpServletRequest request);
	// 因为它可以根据request返回一个CORS配置。可以把这个接口理解为：存储request与跨域配置信息的容器

}
