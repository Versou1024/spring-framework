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

package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Handles method return values by delegating to a list of registered {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}.
 * Previously resolved return types are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {
	// HandlerMethodReturnValueHandlerComposite：处理器组合模式
	// 这是个厉害角色。其实它就是提供的所有的HandlerMethodReturnValueHandler集合，
	// 它定义了一个链表用于存储所有实现的HandlerMethodReturnValueHandler。
	//
	// 它用在RequestMappingHandlerAdapter和ExceptionHandlerExceptionResolver里，此
	// 处以RequestMappingHandlerAdapter为例：

	protected final Log logger = LogFactory.getLog(getClass());

	// 组合模式
	private final List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();


	/**
	 * Return a read-only list with the registered handlers, or an empty list.
	 */
	public List<HandlerMethodReturnValueHandler> getHandlers() {
		// 不可变集合
		return Collections.unmodifiableList(this.returnValueHandlers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method return type} is supported by any registered
	 * {@link HandlerMethodReturnValueHandler}.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		// 支持的返回类型
		return getReturnValueHandler(returnType) != null;
	}

	@Nullable
	private HandlerMethodReturnValueHandler getReturnValueHandler(MethodParameter returnType) {
		// returnValueHandlers中只要任何一个HandlerMethodReturnValueHandler支持即可

		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;
	}

	/**
	 * Iterate over registered {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers} and invoke the one that supports it.
	 * @throws IllegalStateException if no suitable {@link HandlerMethodReturnValueHandler} is found.
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		// 1. 选择支持处理当前返回值的HandlerMethodReturnValueHandler
		HandlerMethodReturnValueHandler handler = selectHandler(returnValue, returnType);
		if (handler == null) {
			throw new IllegalArgumentException("Unknown return value type: " + returnType.getParameterType().getName());
		}
		// 2.调用其处理方法,处理的结果会写入mavContainer
		handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
	}

	@Nullable
	private HandlerMethodReturnValueHandler selectHandler(@Nullable Object value, MethodParameter returnType) {
		// 这个和我们上面的就对应上了  第一步去判断这个返回值是不是一个异步的value（AsyncHandlerMethodReturnValueHandler实现类只能我们自己来写~）
		boolean isAsyncValue = isAsyncReturnValue(value, returnType);
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			// 如果判断发现这个值是异步的value，那它显然就只能交给你自己定义的异步处理器处理了，别的处理器肯定就靠边站~~~~~ 忽略,一般不会使用MVC的异步模式
			if (isAsyncValue && !(handler instanceof AsyncHandlerMethodReturnValueHandler)) {
				continue;
			}
			// 返回支持的处理这个返回值的handler
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;

		// 我们可以看到，它内的逻辑其实非常的简单。重点在于我们需要关心下调用栈：
		//
		// 求的入口处在这：
		// doDispatcher里会找到一个HandlerAdapter会调用@handle方法来真正执行Spring MVC的Handler。
		// 扔给ServletInvocableHandlerMethod#invokeAndHandle去执行处理器，从而拿到方法返回值：returnValue。
		// 最终交给HandlerMethodReturnValueHandlerComposite#handleReturnValue它去处理~~~
		// 上面看了源码处理过程，这就简单了，其实最终做事的是我们的具体的找到唯一的一个HandlerMethodReturnValueHandler~
	}

	private boolean isAsyncReturnValue(@Nullable Object value, MethodParameter returnType) {
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler instanceof AsyncHandlerMethodReturnValueHandler &&
					((AsyncHandlerMethodReturnValueHandler) handler).isAsyncReturnValue(value, returnType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandler(HandlerMethodReturnValueHandler handler) {
		this.returnValueHandlers.add(handler);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandlers(
			@Nullable List<? extends HandlerMethodReturnValueHandler> handlers) {

		if (handlers != null) {
			this.returnValueHandlers.addAll(handlers);
		}
		return this;
	}

}
