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

package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * Wrapper for a {@link ClientHttpRequest} that has support for {@link ClientHttpRequestInterceptor
 * ClientHttpRequestInterceptors}.
 *
 * @author Arjen Poutsma
 * @since 3.1
 */
class InterceptingClientHttpRequest extends AbstractBufferingClientHttpRequest {
	
	// 使用:
	// 支持ClientHttpRequestInterceptors的ClientHttpRequest的包装器 -> 包装器对象
	

	// 包装的目标: ClientHttpRequestFactory
	private final ClientHttpRequestFactory requestFactory;

	// 包装的目标: 拦截器 ->
	private final List<ClientHttpRequestInterceptor> interceptors;

	private HttpMethod method;

	private URI uri;


	protected InterceptingClientHttpRequest(ClientHttpRequestFactory requestFactory,
			List<ClientHttpRequestInterceptor> interceptors, URI uri, HttpMethod method) {
		// 唯一构造器
		this.requestFactory = requestFactory;
		this.interceptors = interceptors;
		this.method = method;
		this.uri = uri;
	}


	@Override
	public HttpMethod getMethod() {
		return this.method;
	}

	@Override
	public String getMethodValue() {
		return this.method.name();
	}

	@Override
	public URI getURI() {
		return this.uri;
	}

	@Override
	protected final ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
		// ❗️❗️❗️ -> 创建的请求的拦截器链 -> InterceptingRequestExecution
		
		// 1. 创建拦截器 
		InterceptingRequestExecution requestExecution = new InterceptingRequestExecution();
		// 2. 使用 InterceptingRequestExecution#excute(..) 执行拦截方法
		return requestExecution.execute(this, bufferedOutput);
	}


	private class InterceptingRequestExecution implements ClientHttpRequestExecution {
		// 位于: 内部类 
		
		// 定义:
		// 拦截器链,持有ClientHttpRequestInterceptor集合的迭代器

		private final Iterator<ClientHttpRequestInterceptor> iterator;

		public InterceptingRequestExecution() {
			this.iterator = interceptors.iterator();
		}

		@Override
		public ClientHttpResponse execute(HttpRequest request, byte[] body) throws IOException {
			// 1. 还有下一个拦截器需要执行
			if (this.iterator.hasNext()) {
				ClientHttpRequestInterceptor nextInterceptor = this.iterator.next();
				// 1.1 执行遍历到的拦截器
				return nextInterceptor.intercept(request, body, this);
			}
			// 2. 没有拦截器 -> 也就说拦截器执行完毕 [❗️❗️❗️ -> 开始准备执行正式的方法]
			else {
				HttpMethod method = request.getMethod();
				Assert.state(method != null, "No standard HTTP method");
				// 2.1 拿到委托的请求对象
				ClientHttpRequest delegate = requestFactory.createRequest(request.getURI(), method);
				// 2.2 将ClientHttpRequest的headers添加到委托的对象中
				request.getHeaders().forEach((key, value) -> delegate.getHeaders().addAll(key, value));
				// 2.3 有执行的请求体时
				if (body.length > 0) {
					// 2.3.1 有执行的请求体时,但使用Stream流式的输出消息
					if (delegate instanceof StreamingHttpOutputMessage) {
						// 设置为一个流
						StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) delegate;
						streamingOutputMessage.setBody(outputStream -> StreamUtils.copy(body, outputStream));
					}
					// 2.3.2 有执行的请求体时,不能使用Stream流式的输出消息 -> 那就讲body缓存到delegate.getBody()中
					else {
						// ❗️❗️❗️❗️❗️❗️-> 当 delegate 是 BufferingClientHttpRequestWrapper 时,就会将请求体缓冲起来哦
						StreamUtils.copy(body, delegate.getBody());
					}
				}
				return delegate.execute();
			}
		}
	}

}
