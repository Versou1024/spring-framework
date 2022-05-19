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

package org.springframework.web.method.support;

import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Strategy for contributing to the building of a {@link UriComponents} by
 * looking at a method parameter and an argument value and deciding what
 * part of the target URL should be updated.
 *
 * @author Oliver Gierke
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface UriComponentsContributor {
	// 通过查看方法参数和参数值并决定应该更新目标 URL 的哪一部分
	// 来为UriComponents的build做出

	/**
	 * Whether this contributor supports the given method parameter.
	 */
	boolean supportsParameter(MethodParameter parameter);
	// 是否支持解析这个参数parameter
	// 和 HandlerMethodArgumentResolver的supportsParameter()一样

	/**
	 * Process the given method argument and either update the
	 * {@link UriComponentsBuilder} or add to the map with URI variables
	 * to use to expand the URI after all arguments are processed.
	 * @param parameter the controller method parameter (never {@code null})
	 * @param value the argument value (possibly {@code null})
	 * @param builder the builder to update (never {@code null})
	 * @param uriVariables a map to add URI variables to (never {@code null})
	 * @param conversionService a ConversionService to format values as Strings
	 */
	void contributeMethodArgument(MethodParameter parameter, Object value, UriComponentsBuilder builder,
			Map<String, Object> uriVariables, ConversionService conversionService);
	// 处理给定的方法参数并更新UriComponentsBuilder或使用 URI 变量添加到映射中，以便在处理完所有参数后扩展 URI。
	//参数：
	//		parameter - 控制器方法的形参（从不为null ）
	//		value – 传入的参数值（可能为null ）
	//		builder – 要更新的url构建器（从不为null ）
	//		uriVariables – 将 URI 变量添加到的映射（从不为null ）
	//		ConversionService – 将值格式化为字符串的 ConversionService

}
