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

package org.springframework.web.method.annotation;

import java.util.Map;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Resolves method arguments annotated with {@code @RequestHeader} except for
 * {@link Map} arguments. See {@link RequestHeaderMapMethodArgumentResolver} for
 * details on {@link Map} arguments annotated with {@code @RequestHeader}.
 *
 * <p>An {@code @RequestHeader} is a named value resolved from a request header.
 * It has a required flag and a default value to fall back on when the request
 * header does not exist.
 *
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved
 * request header values that don't yet match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class RequestHeaderMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {
	// 实现超类 AbstractNamedValueMethodArgumentResolver 剩余共走
	// 		根据MethodParameter创建NameValueInfo（子类的实现可继承自NameValueInfo，就是对应注解的属性们）
	//		根据方法参数名称name从HttpServletRequest, Http Headers, URI template variables等等中获取属性值
	//		对arg == null这种情况的处理（非必须）
	//		实现 supportsParameter() 方法

	/**
	 * Create a new {@link RequestHeaderMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory to use for resolving  ${...}
	 * placeholder and #{...} SpEL expressions in default values;
	 * or {@code null} if default values are not expected to have expressions
	 */
	public RequestHeaderMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		super(beanFactory);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 支持形参上有个@ReuqestHeader注解,且形参不能是Map类型的
		// 有的小伙伴会说：`@RequestHeader Map headers`这样可以接收到所有的请求头啊
		// 其实不是本类的功劳，是`RequestHeaderMapMethodArgumentResolver`的作用

		return (parameter.hasParameterAnnotation(RequestHeader.class) &&
				!Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType()));
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		// 创建 NamedValueInfo

		RequestHeader ann = parameter.getParameterAnnotation(RequestHeader.class);
		Assert.state(ann != null, "No RequestHeader annotation");
		return new RequestHeaderNamedValueInfo(ann);
	}

	@Override
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		// 解析name

		// 从请求中获取对应的header
		String[] headerValues = request.getHeaderValues(name);
		if (headerValues != null) {
			return (headerValues.length == 1 ? headerValues[0] : headerValues);
		}
		else {
			return null;
		}
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
		throw new MissingRequestHeaderException(name, parameter);
	}


	private static final class RequestHeaderNamedValueInfo extends NamedValueInfo {

		private RequestHeaderNamedValueInfo(RequestHeader annotation) {
			// 根据 @ReuqestHeader 注解创建 NameValueInfo
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
