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

	// 参数类型是Map的解析器我认为是对第一类的有些处理器的一种补充，它依赖上面的相关注解。
	// 你是否想过通过@RequestParam一次性全给封装进一个Map里，然后再自己分析？
	// 同样的本类处理器给@RequestHeader、@PathVariable、@MatrixVariable都赋予了这种能力~

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 1. 形参上有指定注解@PathVariable
		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		// 2. 注解不为空,形参是Map类型的,注意同时注解的value必须是空的
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
			// 直接将其转换为LinkedHashMap传递出去
			// 传递出 uriTemplateVars
			return new LinkedHashMap<>(uriTemplateVars);
		} else {
			return Collections.emptyMap();
		}
	}

}
