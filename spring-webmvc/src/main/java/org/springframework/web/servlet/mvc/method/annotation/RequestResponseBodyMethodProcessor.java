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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * Resolves method arguments annotated with {@code @RequestBody} and handles return
 * values from methods annotated with {@code @ResponseBody} by reading and writing
 * to the body of the request or response with an {@link HttpMessageConverter}.
 *
 * <p>An {@code @RequestBody} method argument is also validated if it is annotated
 * with {@code @javax.validation.Valid}. In case of validation failure,
 * {@link MethodArgumentNotValidException} is raised and results in an HTTP 400
 * response status code if {@link DefaultHandlerExceptionResolver} is configured.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class RequestResponseBodyMethodProcessor extends AbstractMessageConverterMethodProcessor {
	// 它继承自AbstractMessageConverterMethodProcessor。
	// 从名字或许就能看出来，这个处理器及其重要，因为它处理着我们最为重要的一个注解@ResponseBody（其实它还处理@RequestBody，只是我们这部分不讲请求参数~~~）
	// 并且它在读、写的时候和HttpMessageConverter还有深度结合~~

	/**
	 * Basic constructor with converters only. Suitable for resolving
	 * {@code @RequestBody}. For handling {@code @ResponseBody} consider also
	 * providing a {@code ContentNegotiationManager}.
	 */
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters) {
		super(converters);
	}

	/**
	 * Basic constructor with converters and {@code ContentNegotiationManager}.
	 * Suitable for resolving {@code @RequestBody} and handling
	 * {@code @ResponseBody} without {@code Request~} or
	 * {@code ResponseBodyAdvice}.
	 */
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager) {

		super(converters, manager);
	}

	/**
	 * Complete constructor for resolving {@code @RequestBody} method arguments.
	 * For handling {@code @ResponseBody} consider also providing a
	 * {@code ContentNegotiationManager}.
	 * @since 4.2
	 */
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, null, requestResponseBodyAdvice);
	}

	/**
	 * Complete constructor for resolving {@code @RequestBody} and handling
	 * {@code @ResponseBody}.
	 */
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, manager, requestResponseBodyAdvice);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// ❗️核心支持：支持解析带有@RequestBody的形参
		return parameter.hasParameterAnnotation(RequestBody.class);
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		// ❗️核心支持：支持解析的返回值带有@ResponseBody的返回值
		//  显然可以发现，方法上或者类上标注有@ResponseBody都是可以的~~~~
		//	这也就是为什么现在@RestController可以代替我们的的@Controller + @ResponseBody生效了

		// returnType.getContainingClass() 获取的就是HandlerMethod对应的声明的所在Class
		return (AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), ResponseBody.class) ||
				returnType.hasMethodAnnotation(ResponseBody.class));
	}

	/**
	 * Throws MethodArgumentNotValidException if validation fails.
	 * @throws HttpMessageNotReadableException if {@link RequestBody#required()}
	 * is {@code true} and there is no body content or if there is no suitable
	 * converter to read the content with.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
		// ❗️解析@RequestBody形参的核心方法
		// 入参是支持使用Optional包装一层的~~~
		parameter = parameter.nestedIfOptional();
		// 这个方法就特别重要了，实现就在下面，现在强烈要求吧目光先投入到下面这个方法实现上~~~~
		Object arg = readWithMessageConverters(webRequest, parameter, parameter.getNestedGenericParameterType());
		// 拿到入参的名字
		// 获取到入参的名称,其实不叫形参名字，应该叫objectName给校验时用的
		// 请注意：这里的名称是类名首字母小写，并不是你方法里写的名字。比如本利若形参名写为personAAA，但是name的值还是person
		// 但是注意：`parameter.getParameterName()`的值可是personAAA
		String name = Conventions.getVariableNameForParameter(parameter);

		// 下面就是进行参数绑定、数据适配、转换的逻辑了  这个在Spring MVC处理请求参数这一章会详细讲解
		// 数据校验@Validated也是在此处生效的
		// 只有存在binderFactory才会去完成自动的绑定、校验~
		// 此处web环境为：ServletRequestDataBinderFactory
		if (binderFactory != null) {
			// 创建webDataBinder用于校验数据格式是否正确
			WebDataBinder binder = binderFactory.createBinder(webRequest, arg, name);
			// 显然传了参数才需要去绑定校验嘛
			if (arg != null) {
				// 利用binder进行数据校验
				// Applicable：适合
				validateIfApplicable(binder, parameter);
				// 检查binder的数据绑定结果或者校验结果中是否有errors，
				// 抛出异常
				if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
					throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
				}
			}
			// 把错误消息放进去 证明已经校验出错误了~~~
			// 后续逻辑会判断MODEL_KEY_PREFIX这个key的~~~~
			if (mavContainer != null) {
				mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());
			}
		}
		//
		return adaptArgumentIfNecessary(arg, parameter);
	}

	@Override
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest, MethodParameter parameter, Type paramType)
			throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		Assert.state(servletRequest != null, "No HttpServletRequest");
		ServletServerHttpRequest inputMessage = new ServletServerHttpRequest(servletRequest);

		// 核心是这个方法，它的实现逻辑在父类AbstractMessageConverterMethodArgumentResolver上~~~继续转移目光吧~~
		Object arg = readWithMessageConverters(inputMessage, parameter, paramType);
		// body体为空，而且不是@RequestBody(required = false)，那就抛错呗  请求的body是必须的  这个很好理解
		if (arg == null && checkRequired(parameter)) {
			throw new HttpMessageNotReadableException("Required request body is missing: " +
					parameter.getExecutable().toGenericString(), inputMessage);
		}
		return arg;
	}

	protected boolean checkRequired(MethodParameter parameter) {
		RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
		return (requestBody != null && requestBody.required() && !parameter.isOptional());
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
		// ️处理返回值的核心方法

		// 1. 直接标记request已经被处理了,不需要后续的视图渲染等操作
		// 从方法注解可知，由于@RequestBody会被这里给解析掉，因此设置为true告知mavContainer请求已被处理
		// 下面就会直接从outputMessage中写入需要返回的数据啦
		mavContainer.setRequestHandled(true);
		// 获取请求信息、获取响应信息与Content-Type
		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

		// 利用HttpMessageConverter做消息转换的输出
		// Try even with null return value. ResponseBodyAdvice could get involved.
		// 这个方法是核心，也会处理null值~~~  这里面一些Advice会生效~~~~
		// 会选择到合适的 HttpMessageConverter,然后进行消息转换~~~~（这里只指写~~~）
		// 这个方法在父类上，是非常核心关键自然也是非常复杂的~~~
		writeWithMessageConverters(returnValue, returnType, inputMessage, outputMessage);

		// 你会发现其它的返回值处理器都是不会调用消息转换器的，而只有AbstractMessageConverterMethodProcessor它的两个子类才会这么做。
		// 而刚巧，这种方式（@ResponseBody方式）是我们当下最为流行的处理方式，因此非常有必要进行深入的了解~~~
	}

}
