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

package org.springframework.web.method.annotation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link Map} method arguments annotated with {@code @RequestHeader}.
 * For individual header values annotated with {@code @RequestHeader} see
 * {@link RequestHeaderMethodArgumentResolver} instead.
 *
 * <p>The created {@link Map} contains all request header name/value pairs.
 * The method parameter type may be a {@link MultiValueMap} to receive all
 * values for a header, not only the first one.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class RequestHeaderMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 1. 有@RequestHeader注解,同时形参类型是Map的
		return (parameter.hasParameterAnnotation(RequestHeader.class) &&
				Map.class.isAssignableFrom(parameter.getParameterType()));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
		// MultiValueMap 继承的 Map
		// HttpHeaders 继承的 MultiValueMap

		// 1. 形参类型
		Class<?> paramType = parameter.getParameterType();
		// 2.1 MultiValueMap 多值value的map类型
		if (MultiValueMap.class.isAssignableFrom(paramType)) {
			MultiValueMap<String, String> result;
			// 2.1.1 HttpHeaders 类型的
			if (HttpHeaders.class.isAssignableFrom(paramType)) {
				result = new HttpHeaders();
			}
			// 2.1.2 LinkedHashMap 类型的
			else {
				result = new LinkedMultiValueMap<>();
			}
			// 2.1.3 遍历 RequestHeader ,存入到 多值Map result中然后返回
			for (Iterator<String> iterator = webRequest.getHeaderNames(); iterator.hasNext();) {
				String headerName = iterator.next();
				String[] headerValues = webRequest.getHeaderValues(headerName);
				if (headerValues != null) {
					for (String headerValue : headerValues) {
						result.add(headerName, headerValue);
					}
				}
			}
			return result;
		}
		// 2.2  就是普通的Map类型
		else {
			Map<String, String> result = new LinkedHashMap<>();
			for (Iterator<String> iterator = webRequest.getHeaderNames(); iterator.hasNext();) {
				String headerName = iterator.next();
				String headerValue = webRequest.getHeader(headerName);
				if (headerValue != null) {
					// 2.2.1 只需要标头的第一个值
					// 因为Headers是多值Map
					// 而形参是普通Map,就不得不考虑只放一个值
					result.put(headerName, headerValue);
				}
			}
			return result;
		}
	}

}
