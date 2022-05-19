/*
 * Copyright 2002-2020 the original author or authors.
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

import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link Map} method arguments and handles {@link Map} return values.
 *
 * <p>A Map return value can be interpreted in more than one ways depending
 * on the presence of annotations like {@code @ModelAttribute} or
 * {@code @ResponseBody}. As of 5.2 this resolver returns false if the
 * parameter is annotated.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class MapMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
	// MapMethodProcessor 支持解析 Map类型的入参 以及 Map类型的返回值
	// 注意一下:
	// Map这种返回值可以被解释为多种途径，依赖于Handler上面的额注解，
	// 如：@ModelAttribute和@ResponseBody这种注解。所以请务必使它放在这些处理器的后面，作为最后的兜底最后执行~~~

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (Map.class.isAssignableFrom(parameter.getParameterType()) && parameter.getParameterAnnotations().length == 0);
	}

	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		Assert.state(mavContainer != null, "ModelAndViewContainer is required for model exposure");
		return mavContainer.getModel();
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		// 优先级很低
		// 支持Map类型
		return Map.class.isAssignableFrom(returnType.getParameterType());
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		// 做的事非常简单，仅仅只是把我们的Map放进Model里面~~~
		// 但是此处需要注意的是：它并没有setViewName，所以它此时是没有视图名称的~~~
		// ModelAndViewContainer#setRequestHandled(true) 所以后续若还有处理器可以继续处理

		if (returnValue instanceof Map){
			mavContainer.addAllAttributes((Map) returnValue);
		}
		else if (returnValue != null) {
			// should not happen
			throw new UnsupportedOperationException("Unexpected return type [" +
					returnType.getParameterType().getName() + "] in method: " + returnType.getMethod());
		}
	}

}
