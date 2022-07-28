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

package org.springframework.http.client.support;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;

/**
 * Base class for {@link org.springframework.web.client.RestTemplate}
 * and other HTTP accessing gateway helpers, defining common properties
 * such as the {@link ClientHttpRequestFactory} to operate on.
 *
 * <p>Not intended to be used directly.
 *
 * <p>See {@link org.springframework.web.client.RestTemplate} for an entry point.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see ClientHttpRequestFactory
 * @see org.springframework.web.client.RestTemplate
 */
public abstract class HttpAccessor {
	// 位于: org.springframework.http.client.support 即 spring-web模块下的http.client.support包下
	
	// 命名:
	// Http Accessor = Http 访问器
	
	// 作用:
	// RestTemplate和其他 HTTP 访问网关助手的基类，定义了要操作的ClientHttpRequestFactory等通用属性。
	// 有关入口点，请参见org.springframework.web.client.RestTemplate 。

	protected final Log logger = HttpLogging.forLogName(getClass());

	// 持有: ClientHttpRequestFactory
	private ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

	// 且持有: ClientHttpRequestInitializer
	private final List<ClientHttpRequestInitializer> clientHttpRequestInitializers = new ArrayList<>();


	/**
	 * Set the request factory that this accessor uses for obtaining client request handles.
	 * <p>The default is a {@link SimpleClientHttpRequestFactory} based on the JDK's own
	 * HTTP libraries ({@link java.net.HttpURLConnection}).
	 * <p><b>Note that the standard JDK HTTP library does not support the HTTP PATCH method.
	 * Configure the Apache HttpComponents or OkHttp request factory to enable PATCH.</b>
	 * @see #createRequest(URI, HttpMethod)
	 * @see SimpleClientHttpRequestFactory
	 * @see org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory
	 * @see org.springframework.http.client.OkHttp3ClientHttpRequestFactory
	 */
	public void setRequestFactory(ClientHttpRequestFactory requestFactory) {
		Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
		this.requestFactory = requestFactory;
	}

	/**
	 * Return the request factory that this accessor uses for obtaining client request handles.
	 */
	public ClientHttpRequestFactory getRequestFactory() {
		return this.requestFactory;
	}


	/**
	 * Set the request initializers that this accessor should use.
	 * <p>The initializers will get immediately sorted according to their
	 * {@linkplain AnnotationAwareOrderComparator#sort(List) order}.
	 * @since 5.2
	 */
	public void setClientHttpRequestInitializers(
			List<ClientHttpRequestInitializer> clientHttpRequestInitializers) {

		if (this.clientHttpRequestInitializers != clientHttpRequestInitializers) {
			this.clientHttpRequestInitializers.clear();
			this.clientHttpRequestInitializers.addAll(clientHttpRequestInitializers);
			AnnotationAwareOrderComparator.sort(this.clientHttpRequestInitializers);
		}
	}

	/**
	 * Get the request initializers that this accessor uses.
	 * <p>The returned {@link List} is active and may be modified. Note,
	 * however, that the initializers will not be resorted according to their
	 * {@linkplain AnnotationAwareOrderComparator#sort(List) order} before the
	 * {@link ClientHttpRequest} is initialized.
	 * @since 5.2
	 * @see #setClientHttpRequestInitializers(List)
	 */
	public List<ClientHttpRequestInitializer> getClientHttpRequestInitializers() {
		return this.clientHttpRequestInitializers;
	}

	/**
	 * Create a new {@link ClientHttpRequest} via this template's {@link ClientHttpRequestFactory}.
	 * @param url the URL to connect to
	 * @param method the HTTP method to execute (GET, POST, etc)
	 * @return the created request
	 * @throws IOException in case of I/O errors
	 * @see #getRequestFactory()
	 * @see ClientHttpRequestFactory#createRequest(URI, HttpMethod)
	 */
	protected ClientHttpRequest createRequest(URI url, HttpMethod method) throws IOException {
		// 基于 ClientHttpRequestFactory#createRequest(..) 方法提供给子类:创建ClientHttpRequest的方法
		// 被: RestTemplate#doExecute(..) 方法调用
		// 以为例
		// 	@Bean
		//	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		//		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		//		BufferingClientHttpRequestFactory bufferingClientHttpRequestFactory = new BufferingClientHttpRequestFactory(factory);
		//		factory.setReadTimeout(180000);
		//		factory.setConnectTimeout(180000);
		//		RestTemplate restTemplate = new RestTemplate(bufferingClientHttpRequestFactory);
		//		restTemplate.getInterceptors().add(new AuthHttpRequestInterceptor());
		//		restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		//		return restTemplate;
		//	}
		
		// 1. 调用: ClientHttpRequestFactory#createRequest(..)
		// 以上面为例举例: 
		// 最终获取到的ClientHttpRequestFactory就是BufferingClientHttpRequestFactory,其中BufferingClientHttpRequestFactory封装有HttpComponentsClientHttpRequestFactory
		// BufferingClientHttpRequestFactory将HttpComponentsClientHttpRequestFactory创建出来HttpComponentsClientHttpRequest包装到BufferingClientHttpRequestWrapper中
		// bug ❗️❗️❗️ ❗️❗️❗️ 
		// 子类 -> InterceptingHttpAccessor重写了 getRequestFactory()方法
		// 使得上面的 BufferingClientHttpRequestFactory 在有拦截器[比如打印日志]的情况下 加入到 InterceptingClientHttpRequestFactory 中
		// 因此 getRequestFactory() 拿到的工厂就是 -> InterceptingClientHttpRequestFactory 包装有 BufferingClientHttpRequestFactory 包装有 HttpComponentsClientHttpRequestFactory
		// getRequestFactory().createRequest(url, method) 拿到的请求时 -> InterceptingClientHttpRequest 包装有 BufferingClientHttpRequestWrapper 包装有 HttpComponentsClientHttpRequest
		// ❗️❗️❗️❗️❗️❗️
		ClientHttpRequest request = getRequestFactory().createRequest(url, method);
		// 2. ❗️❗️❗️ 在 ClientHttpRequest 还未使用之前,就可以使用clientHttpRequestInitializers就行初始化动作
		initialize(request);
		if (logger.isDebugEnabled()) {
			logger.debug("HTTP " + method.name() + " " + url);
		}
		// 3. 返回: request对象
		return request;
	}

	private void initialize(ClientHttpRequest request) {
		this.clientHttpRequestInitializers.forEach(initializer -> initializer.initialize(request));
	}

}
