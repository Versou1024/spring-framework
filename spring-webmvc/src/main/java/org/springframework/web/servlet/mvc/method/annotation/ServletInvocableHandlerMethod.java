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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

/**
 * Extends {@link InvocableHandlerMethod} with the ability to handle return
 * values through a registered {@link HandlerMethodReturnValueHandler} and
 * also supports setting the response status based on a method-level
 * {@code @ResponseStatus} annotation.
 *
 * <p>A {@code null} return value (including void) may be interpreted as the
 * end of request processing in combination with a {@code @ResponseStatus}
 * annotation, a not-modified check condition
 * (see {@link ServletWebRequest#checkNotModified(long)}), or
 * a method argument that provides access to the response stream.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {
	// 它是对InvocableHandlerMethod的扩展，它增加了返回值和响应状态码的处理，
	// 另外在ServletInvocableHandlerMethod有个内部类ConcurrentResultHandlerMethod继承于它，支持异常调用结果处理，
	// Servlet容器下Controller在查找适配器时发起调用的最终就是ServletInvocableHandlerMethod。

	// ServletInvocableHandlerMethod 返回值的处理\响应状态码的处理
	// InvocableHandlerMethod 请求参数的处理,以及HandlerMethod的invoke执行

	// HandlerMethod用于封装Handler和处理请求的Method；InvocableHandlerMethod增加了方法参数解析和调用方法的能力；ServletInvocableHandlerMethod在此基础上在增加了如下三个能力：
	//
	//	对@ResponseStatus注解的支持
	//		1.当一个方法注释了@ResponseStatus后，响应码就是注解上的响应码。 并且，并且如果returnValue=null或者reason不为空（不为null且不为“”），将中断处理直接返回(不再渲染页面)
	//	对返回值returnValue的处理
	//		1. 对返回值的处理是使用HandlerMethodReturnValueHandlerComposite完成的
	//	对异步处理结果的处理

	private static final Method CALLABLE_METHOD = ClassUtils.getMethod(Callable.class, "call");

	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers; // Handler的返回值处理器 -- 组合模式


	/**
	 * Creates an instance from the given handler and method.
	 */
	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
	}

	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		// 核心 --
		// 因为在 RequestMappingHandlerAdapter#createInvocableHandlerMethod()时就是使用的这个构造器哦

		super(handlerMethod);
	}


	/**
	 * Register {@link HandlerMethodReturnValueHandler} instances to use to
	 * handle return values.
	 */
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		// 在 RequestMappingHandlerAdapter#invokeHandlerMethod() 创建了 ServletInvocableHandlerMethod后
		// 就会调用方法设置 返回值处理器[组合模式]

		this.returnValueHandlers = returnValueHandlers;
	}


	/**
	 * Invoke the method and handle the return value through one of the
	 * configured {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}.
	 * @param webRequest the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type (not resolved)
	 */
	public void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {
		// ️request的方法已经准备完毕，即将开始执行
		// 注意在RequestMappingHandlerAdapter#InvocableHandlerMethod()中providedArgs为null值

		// 1. 调用超类的 InvocableHandlerMethod#invokeForRequest() 处理请求参,执行请求，并返回结果
		// 职责分明
		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
		// 2. 设置响应状态码 -- 与 @ResponseStatus 注解有关
		// 设置HttpServletResponse返回状态码 这里面还是有点意思的  因为@ResponseStatus#code()在父类已经解析了  但是子类才用
		setResponseStatus(webRequest);

		// 3. 返回值为null
		// 重点是这一句话：mavContainer.setRequestHandled(true); 表示该请求已经被处理过了
		if (returnValue == null) {
			// 3.1 request请求的内容没有被修改 或者 响应状态不为nulk 或 mavContainer已经设置为请求处理完毕
			if (isRequestNotModified(webRequest) || getResponseStatus() != null || mavContainer.isRequestHandled()) {
				disableContentCachingIfNecessary(webRequest);
				// 3.2 设置请求已被处理,返回Void
				mavContainer.setRequestHandled(true);
				return;
			}
		} else if (StringUtils.hasText(getResponseStatusReason())) {
			// 4. 返回值不是null,同时有设置responseStatusReason响应状态原因
			// 这个接口被@ResponseStatus标记为异常,request处理完成比,返回一个void值
			mavContainer.setRequestHandled(true);
			return;
		}

		// 前边都不成立,则设置RequestHandled=false即请求未完成
		// 继续交给HandlerMethodReturnValueHandlerComposite处理
		// 可见@ResponseStatus的优先级还是蛮高的~~~~~

		// 5. 请求没有处理完
		mavContainer.setRequestHandled(false);
		Assert.state(this.returnValueHandlers != null, "No return value handlers");
		try {
			// 6. 使用组合模式的返回值处理器对返回值进行处理
			this.returnValueHandlers.handleReturnValue(returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(formatErrorForReturnValue(returnValue), ex);
			}
			throw ex;
		}
	}

	/**
	 * Set the response status according to the {@link ResponseStatus} annotation.
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		// 1. 调用超类的 InvocableHandlerMethod 解析出来的响应状态码 -- 前提是有方法上\类上\超类上有@ResponseStatus注解才会有这值
		HttpStatus status = getResponseStatus();
		if (status == null) {
			// 2. 方法/类/超类上没有@ResponseStatus的注解,也就没有status直接返回
			return;
		}

		// 3. 当前HandlerMethod/类/超类上有@ResponseStatus注解，那么还可以对respone的responseReason设置状态和错误原因
		// 即 setStatus、setError
		HttpServletResponse response = webRequest.getResponse();
		if (response != null) {
			String reason = getResponseStatusReason();
			if (StringUtils.hasText(reason)) {
				response.sendError(status.value(), reason); // ❗️❗️此处务必注意：若有reason，那就是sendError  哪怕你是200哦~
			}
			else {
				response.setStatus(status.value());
			}
		}

		// 设置请求属性 key = View.class.getName() + ".responseStatus" ， value = status
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, status);
	}

	/**
	 * Does the given request qualify as "not modified"?
	 * @see ServletWebRequest#checkNotModified(long)
	 * @see ServletWebRequest#checkNotModified(String)
	 */
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		return webRequest.isNotModified();
	}

	private void disableContentCachingIfNecessary(ServletWebRequest webRequest) {
		if (isRequestNotModified(webRequest)) {
			HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
			Assert.notNull(response, "Expected HttpServletResponse");
			if (StringUtils.hasText(response.getHeader(HttpHeaders.ETAG))) {
				HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
				Assert.notNull(request, "Expected HttpServletRequest");
			}
		}
	}

	private String formatErrorForReturnValue(@Nullable Object returnValue) {
		return "Error handling return value=[" + returnValue + "]" +
				(returnValue != null ? ", type=" + returnValue.getClass().getName() : "") +
				" in " + toString();
	}

	/**
	 * Create a nested ServletInvocableHandlerMethod subclass that returns the
	 * the given value (or raises an Exception if the value is one) rather than
	 * actually invoking the controller method. This is useful when processing
	 * async return values (e.g. Callable, DeferredResult, ListenableFuture).
	 */
	ServletInvocableHandlerMethod wrapConcurrentResult(Object result) {
		return new ConcurrentResultHandlerMethod(result, new ConcurrentResultMethodParameter(result));
	}


	/**
	 * A nested subclass of {@code ServletInvocableHandlerMethod} that uses a
	 * simple {@link Callable} instead of the original controller as the handler in
	 * order to return the fixed (concurrent) result value given to it. Effectively
	 * "resumes" processing with the asynchronously produced return value.
	 */
	private class ConcurrentResultHandlerMethod extends ServletInvocableHandlerMethod {
		// 内部类们
		// ServletInvocableHandlerMethod的嵌套子类，它使用简单的Callable而不是原始控制器作为处理程序，以便返回给定的固定（并发）结果值。
		// 使用异步生成的返回值有效地“恢复”处理。

		private final MethodParameter returnType;

		public ConcurrentResultHandlerMethod(final Object result, ConcurrentResultMethodParameter returnType) {
			super((Callable<Object>) () -> {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				return result;
			}, CALLABLE_METHOD);

			if (ServletInvocableHandlerMethod.this.returnValueHandlers != null) {
				setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
			}
			this.returnType = returnType;
		}

		/**
		 * Bridge to actual controller type-level annotations.
		 */
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}

		/**
		 * Bridge to actual return value or generic type within the declared
		 * async return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
		 */
		@Override
		public MethodParameter getReturnValueType(@Nullable Object returnValue) {
			return this.returnType;
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.hasMethodAnnotation(annotationType);
		}
	}


	/**
	 * MethodParameter subclass based on the actual return value type or if
	 * that's null falling back on the generic type within the declared async
	 * return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
	 */
	private class ConcurrentResultMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		private final ResolvableType returnType;

		public ConcurrentResultMethodParameter(Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
			this.returnType = (returnValue instanceof ReactiveTypeHandler.CollectedValuesList ?
					((ReactiveTypeHandler.CollectedValuesList) returnValue).getReturnType() :
					ResolvableType.forType(super.getGenericParameterType()).getGeneric());
		}

		public ConcurrentResultMethodParameter(ConcurrentResultMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
			this.returnType = original.returnType;
		}

		@Override
		public Class<?> getParameterType() {
			if (this.returnValue != null) {
				return this.returnValue.getClass();
			}
			if (!ResolvableType.NONE.equals(this.returnType)) {
				return this.returnType.toClass();
			}
			return super.getParameterType();
		}

		@Override
		public Type getGenericParameterType() {
			return this.returnType.getType();
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			// Ensure @ResponseBody-style handling for values collected from a reactive type
			// even if actual return type is ResponseEntity<Flux<T>>
			return (super.hasMethodAnnotation(annotationType) ||
					(annotationType == ResponseBody.class &&
							this.returnValue instanceof ReactiveTypeHandler.CollectedValuesList));
		}

		@Override
		public ConcurrentResultMethodParameter clone() {
			return new ConcurrentResultMethodParameter(this);
		}
	}

}
