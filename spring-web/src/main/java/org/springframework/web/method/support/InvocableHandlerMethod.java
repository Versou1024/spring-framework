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

package org.springframework.web.method.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Extension of {@link HandlerMethod} that invokes the underlying method with
 * argument values resolved from the current HTTP request through a list of
 * {@link HandlerMethodArgumentResolver}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class InvocableHandlerMethod extends HandlerMethod {

	// 是对HandlerMethod的扩展，增加了调用能力。
	// 这个能力在Spring MVC可是非常非常重要的，
	// 它能够在调用的时候，把方法入参的参数都封装进来（从HTTP request\header等，当然借助的必然是HandlerMethodArgumentResolver）

	// 这个子类主要提供的能力就是提供了invoke调用目标Bean的目标方法的能力，在这个调用过程中可大有文章可为，
	// 当然最为核心的逻辑可是各种各样的HandlerMethodArgumentResolver来完成的
	// InvocableHandlerMethod 这个子类虽然它提供了调用能力，但是它却依旧还没有和Servlet的API绑定起来，毕
	// 竟使用的是Spring自己通用的的NativeWebRequest，so很容易想到它还有一个子类就是干这事的~
	// 那就是 ServletInvocableHandlerMethod

	private static final Object[] EMPTY_ARGS = new Object[0];


	private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite(); // 参数解析器 -- 需要外部传递进来

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer(); // 参数名发现

	@Nullable
	private WebDataBinderFactory dataBinderFactory; // 数据绑定工厂


	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	/**
	 * Create an instance from a bean instance and a method.
	 */
	public InvocableHandlerMethod(Object bean, Method method) {
		super(bean, method);
	}

	/**
	 * Construct a new handler method with the given bean instance, method name and parameters.
	 * @param bean the object bean
	 * @param methodName the method name
	 * @param parameterTypes the method parameter types
	 * @throws NoSuchMethodException when the method cannot be found
	 */
	public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
			throws NoSuchMethodException {

		super(bean, methodName, parameterTypes);
	}


	/**
	 * Set {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}
	 * to use for resolving method argument values.
	 */
	public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
		// 在 RequestMappingHandlerAdapter#invokeHandlerMethod() 中会对 创建的ServletInvocableHandlerMethod#setHandlerMethodArgumentResolvers
		// 将参数解析器传递进来

		this.resolvers = argumentResolvers;
	}

	/**
	 * Set the ParameterNameDiscoverer for resolving parameter names when needed
	 * (e.g. default request attribute name).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		// 在 RequestMappingHandlerAdapter#invokeHandlerMethod() 中会对 创建的ServletInvocableHandlerMethod#setParameterNameDiscoverer() 调用
		// 将参数名字发现器传递进来

		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Set the {@link WebDataBinderFactory} to be passed to argument resolvers allowing them
	 * to create a {@link WebDataBinder} for data binding and type conversion purposes.
	 */
	public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
		// 在 RequestMappingHandlerAdapter#invokeHandlerMethod() 中会对 创建的ServletInvocableHandlerMethod#setDataBinderFactory() 调用
		// 将数据绑定工厂传递进来


		this.dataBinderFactory = dataBinderFactory;
	}


	/**
	 * Invoke the method after resolving its argument values in the context of the given request.
	 * <p>Argument values are commonly resolved through
	 * {@link HandlerMethodArgumentResolver HandlerMethodArgumentResolvers}.
	 * The {@code providedArgs} parameter however may supply argument values to be used directly,
	 * i.e. without argument resolution. Examples of provided argument values include a
	 * {@link WebDataBinder}, a {@link SessionStatus}, or a thrown exception instance.
	 * Provided argument values are checked before argument resolvers.
	 * <p>Delegates to {@link #getMethodArgumentValues} and calls {@link #doInvoke} with the
	 * resolved arguments.
	 * @param request the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type, not resolved
	 * @return the raw value returned by the invoked method
	 * @throws Exception raised if no suitable argument resolver can be found,
	 * or if the method raised an exception
	 * @see #getMethodArgumentValues
	 * @see #doInvoke
	 */
	@Nullable
	public Object invokeForRequest(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer, Object... providedArgs) throws Exception {
		// 在给定请求的上下文中解析其参数值后调用该方法。
		// 参数值通常通过HandlerMethodArgumentResolvers解析。
		// 然而 -- providedArgs参数可以提供要直接使用的参数值，即没有参数解析。
		// 提供的参数值的示例包括 WebDataBinder 、 SessionStatus或抛出的异常实例。在参数解析器之前检查提供的参数值。

		// 1.获取方法请求值
		// 虽然它是最重要的方法，但是此处不讲，因为核心原来还是`HandlerMethodArgumentResolver`
		// 它只是把解析好的放到对应位置里去~~~
		// 说明：这里传入了ParameterNameDiscoverer，它是能够获取到形参名的。
		// 这就是为何注解里我们不写value值，通过形参名字来匹配也是ok的核心原因~
		Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
		if (logger.isTraceEnabled()) {
			logger.trace("Arguments: " + Arrays.toString(args));
		}
		// 2. 执行Controller方法 --
		// doInvoke()方法就不说了，就是个普通的方法调用
		// ReflectionUtils.makeAccessible(getBridgedMethod());
		// return getBridgedMethod().invoke(getBean(), args);
		return doInvoke(args);
	}

	/**
	 * Get the method argument values for the current request, checking the provided
	 * argument values and falling back to the configured argument resolvers.
	 * <p>The resulting array will be passed into {@link #doInvoke}.
	 * @since 5.1.2
	 */
	protected Object[] getMethodArgumentValues(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {
		// 核心 -- > 参数解析 -- 完整过程

		// 1. 获取方法请求参数
		MethodParameter[] parameters = getMethodParameters();
		if (ObjectUtils.isEmpty(parameters)) {
			return EMPTY_ARGS;
		}

		// args 用来存储解析出来的结果
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			// 向parameter设置参数名发现器
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
			args[i] = findProvidedArgument(parameter, providedArgs);
			if (args[i] != null) { // 为false
				continue;
			}
			// 通过合成参数解析器查看是否支持支持当前请求参数
			// 注意这里的resolvers是组合模式的HandlerMethodArgumentResolverComposite类
			if (!this.resolvers.supportsParameter(parameter)) {
				// 不支持就报错
				throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
			}
			try {
				// 支持的话，就调用方法来解析
				args[i] = this.resolvers.resolveArgument(parameter, mavContainer, request, this.dataBinderFactory);
			}
			catch (Exception ex) {
				// Leave stack trace for later, exception may actually be resolved and handled...
				if (logger.isDebugEnabled()) {
					String exMsg = ex.getMessage();
					if (exMsg != null && !exMsg.contains(parameter.getExecutable().toGenericString())) {
						logger.debug(formatArgumentError(parameter, exMsg));
					}
				}
				throw ex;
			}
		}
		return args;
	}

	/**
	 * Invoke the handler method with the given argument values.
	 */
	@Nullable
	protected Object doInvoke(Object... args) throws Exception {
		ReflectionUtils.makeAccessible(getBridgedMethod());
		try {
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(getBridgedMethod(), getBean(), args);
			String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
			throw new IllegalStateException(formatInvokeError(text, args), ex);
		}
		catch (InvocationTargetException ex) {
			// Unwrap for HandlerExceptionResolvers ...
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			else if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				throw new IllegalStateException(formatInvokeError("Invocation failure", args), targetException);
			}
		}
	}

}
