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

package org.springframework.web.servlet.mvc.method.annotation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Resolves {@link Map} method arguments annotated with an @{@link PathVariable}
 * where the annotation does not specify a path variable name. The created
 * {@link Map} contains all URI template name/value pairs.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 * @see PathVariableMethodArgumentResolver
 */
public class PathVariableMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * PathVariableMapMethodArgumentResolver 支持
	 * @PathVariable 不带任何value时，且形参类型时map的，就将所有的路径参数传给map形参
	 */

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 支持前提 -- 形参上有指定注解@PathVariable
		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		// 注解不为空，同时要求，要求@PathVariable注解的属性是Map的子类，且@PathVariable的注解值不能有任何值
		return (ann != null && Map.class.isAssignableFrom(parameter.getParameterType()) && !StringUtils.hasText(ann.value()));
	}

	/**
	 * Return a Map with all URI template variables or an empty map.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
		// 从request中获取属性 uriTemplateVariables
		// key为path路径名，value为路径属性值
		@SuppressWarnings("unchecked")
		Map<String, String> uriTemplateVars = (Map<String, String>) webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

		if (!CollectionUtils.isEmpty(uriTemplateVars)) {
			// 传递出 uriTemplateVars
			return new LinkedHashMap<>(uriTemplateVars);
		} else {
			return Collections.emptyMap();
		}
	}

}
