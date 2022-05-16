/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.RequestToViewNameTranslator;

/**
 * Handles return values of types {@code void} and {@code String} interpreting them
 * as view name reference. As of 4.2, it also handles general {@code CharSequence}
 * types, e.g. {@code StringBuilder} or Groovy's {@code GString}, as view names.
 *
 * <p>A {@code null} return value, either due to a {@code void} return type or
 * as the actual return value is left as-is allowing the configured
 * {@link RequestToViewNameTranslator} to select a view name by convention.
 *
 * <p>A String return value can be interpreted in more than one ways depending on
 * the presence of annotations like {@code @ModelAttribute} or {@code @ResponseBody}.
 * Therefore this handler should be configured after the handlers that support these
 * annotations.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ViewNameMethodReturnValueHandler implements HandlerMethodReturnValueHandler {
	// 可以直接返回一个视图名，最终会交给`RequestToViewNameTranslator`翻译一下~~~

	// Spring4.1之后支持自定义重定向的匹配规则
	// Spring4.1之前还只能支持redirect:这个固定的前缀~~~~
	@Nullable
	private String[] redirectPatterns;


	/**
	 * Configure one more simple patterns (as described in {@link PatternMatchUtils#simpleMatch})
	 * to use in order to recognize custom redirect prefixes in addition to "redirect:".
	 * <p>Note that simply configuring this property will not make a custom redirect prefix work.
	 * There must be a custom View that recognizes the prefix as well.
	 * @since 4.1
	 */
	public void setRedirectPatterns(@Nullable String... redirectPatterns) {
		this.redirectPatterns = redirectPatterns;
	}

	/**
	 * The configured redirect patterns, if any.
	 */
	@Nullable
	public String[] getRedirectPatterns() {
		return this.redirectPatterns;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		// 支持返回类型时void\String类型的
		// 同样的这个在DispatcherServlet#getDefaultReturnValueHandlers()也是非常靠后的一个返回值处理
		// 原因:String这种返回类型,是一个多用途的类型值,不能确定其返回值String就是用viewName
		// 因此需要放在

		Class<?> paramType = returnType.getParameterType();
		return (void.class == paramType || CharSequence.class.isAssignableFrom(paramType));
	}

	// 注意：若返回值是void，此方法都不会进来
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		// 1. 返回值是否为String
		if (returnValue instanceof CharSequence) {
			String viewName = returnValue.toString();
			// 1.1 设置到mavContainer的viewName
			mavContainer.setViewName(viewName);
			// 1.2 检查是否为重定向视图名,如果是的话,就将mavContainer设置为重定向场景吧
			if (isRedirectViewName(viewName)) {
				mavContainer.setRedirectModelScenario(true);
			}
		}
		// 2. 不支持返回值为null的场景
		else if (returnValue != null) {
			// should not happen
			throw new UnsupportedOperationException("Unexpected return type: " + returnType.getParameterType().getName() + " in method: " + returnType.getMethod());
		}
	}

	/**
	 * Whether the given view name is a redirect view reference.
	 * The default implementation checks the configured redirect patterns and
	 * also if the view name starts with the "redirect:" prefix.
	 * @param viewName the view name to check, never {@code null}
	 * @return "true" if the given view name is recognized as a redirect view
	 * reference; "false" otherwise.
	 */
	protected boolean isRedirectViewName(String viewName) {
		// 是否为重定向的viewName
		// 1. 可以根据自定义的redirectPatterns判断重定向,也可以直接铜鼓redirect:前缀判断
		return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
	}

}
