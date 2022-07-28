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

package org.springframework.web.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.InterceptingHttpAccessor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.JsonbHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import org.springframework.web.util.UriTemplateHandler;

/**
 * Synchronous client to perform HTTP requests, exposing a simple, template
 * method API over underlying HTTP client libraries such as the JDK
 * {@code HttpURLConnection}, Apache HttpComponents, and others.
 *
 * <p>The RestTemplate offers templates for common scenarios by HTTP method, in
 * addition to the generalized {@code exchange} and {@code execute} methods that
 * support of less frequent cases.
 *
 * <p><strong>NOTE:</strong> As of 5.0 this class is in maintenance mode, with
 * only minor requests for changes and bugs to be accepted going forward. Please,
 * consider using the {@code org.springframework.web.reactive.client.WebClient}
 * which has a more modern API and supports sync, async, and streaming scenarios.
 *
 * @author Arjen Poutsma
 * @author Brian Clozel
 * @author Roy Clarkson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.0
 * @see HttpMessageConverter
 * @see RequestCallback
 * @see ResponseExtractor
 * @see ResponseErrorHandler
 */
public class RestTemplate extends InterceptingHttpAccessor implements RestOperations {

	private static final boolean romePresent;

	private static final boolean jaxb2Present;

	private static final boolean jackson2Present;

	private static final boolean jackson2XmlPresent;

	private static final boolean jackson2SmilePresent;

	private static final boolean jackson2CborPresent;

	private static final boolean gsonPresent;

	private static final boolean jsonbPresent;

	static {
		ClassLoader classLoader = RestTemplate.class.getClassLoader();
		romePresent = ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", classLoader);
		jaxb2Present = ClassUtils.isPresent("javax.xml.bind.Binder", classLoader);
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) && ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
		jackson2XmlPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader);
		jackson2SmilePresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
		jackson2CborPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory", classLoader);
		gsonPresent = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
		jsonbPresent = ClassUtils.isPresent("javax.json.bind.Jsonb", classLoader);
	}


	private final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();

	private ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();

	private UriTemplateHandler uriTemplateHandler;

	private final ResponseExtractor<HttpHeaders> headersExtractor = new HeadersExtractor();


	/**
	 * Create a new instance of the {@link RestTemplate} using default settings.
	 * Default {@link HttpMessageConverter HttpMessageConverters} are initialized.
	 */
	public RestTemplate() {
		// 无参构造器
		// 支持: byte[] 以及 application/octet-stream 以及 */*
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		// 支持: String 以及 text/plain 与 */* 类型两种类型
		this.messageConverters.add(new StringHttpMessageConverter());
		// 支持: Resource 以及 */* 类型两种类型
		this.messageConverters.add(new ResourceHttpMessageConverter(false));
		try {
			// 支持:  DOMSource\SAXSource\StAXSource\StreamSource\Source 以及 为text/xml application/xml application/*+xml
			this.messageConverters.add(new SourceHttpMessageConverter<>());
		}
		catch (Error err) {
			// Ignore when no TransformerFactory implementation is available
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());

		if (romePresent) {
			this.messageConverters.add(new AtomFeedHttpMessageConverter());
			this.messageConverters.add(new RssChannelHttpMessageConverter());
		}

		if (jackson2XmlPresent) {
			this.messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
		}
		else if (jaxb2Present) {
			this.messageConverters.add(new Jaxb2RootElementHttpMessageConverter());
		}

		if (jackson2Present) {
			// 支持: 所有的Class 只要求 MediaType为application/json  application/*+json 
			this.messageConverters.add(new MappingJackson2HttpMessageConverter());
		}
		else if (gsonPresent) {
			this.messageConverters.add(new GsonHttpMessageConverter());
		}
		else if (jsonbPresent) {
			this.messageConverters.add(new JsonbHttpMessageConverter());
		}

		if (jackson2SmilePresent) {
			this.messageConverters.add(new MappingJackson2SmileHttpMessageConverter());
		}
		if (jackson2CborPresent) {
			this.messageConverters.add(new MappingJackson2CborHttpMessageConverter());
		}

		this.uriTemplateHandler = initUriTemplateHandler();
	}

	/**
	 * Create a new instance of the {@link RestTemplate} based on the given {@link ClientHttpRequestFactory}.
	 * @param requestFactory the HTTP request factory to use
	 * @see org.springframework.http.client.SimpleClientHttpRequestFactory
	 * @see org.springframework.http.client.HttpComponentsClientHttpRequestFactory
	 */
	public RestTemplate(ClientHttpRequestFactory requestFactory) {
		// 有参构造器1: 出入 ClientHttpRequestFactory
		this();
		setRequestFactory(requestFactory);
	}

	/**
	 * Create a new instance of the {@link RestTemplate} using the given list of
	 * {@link HttpMessageConverter} to use.
	 * @param messageConverters the list of {@link HttpMessageConverter} to use
	 * @since 3.2.7
	 */
	public RestTemplate(List<HttpMessageConverter<?>> messageConverters) {
		// 有参构造器2: 传入messageConverters
		validateConverters(messageConverters);
		this.messageConverters.addAll(messageConverters);
		this.uriTemplateHandler = initUriTemplateHandler();
	}


	private static DefaultUriBuilderFactory initUriTemplateHandler() {
		// ❗️❗️❗️ 默认使用的 DefaultUriBuilderFactory
		DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory();
		uriFactory.setEncodingMode(EncodingMode.URI_COMPONENT);  // for backwards compatibility..
		return uriFactory;
	}


	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		validateConverters(messageConverters);
		// Take getMessageConverters() List as-is when passed in here
		if (this.messageConverters != messageConverters) {
			this.messageConverters.clear();
			this.messageConverters.addAll(messageConverters);
		}
	}

	private void validateConverters(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "At least one HttpMessageConverter is required");
		Assert.noNullElements(messageConverters, "The HttpMessageConverter list must not contain null elements");
	}

	/**
	 * Return the list of message body converters.
	 * <p>The returned {@link List} is active and may get appended to.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the error handler.
	 * <p>By default, RestTemplate uses a {@link DefaultResponseErrorHandler}.
	 */
	public void setErrorHandler(ResponseErrorHandler errorHandler) {
		Assert.notNull(errorHandler, "ResponseErrorHandler must not be null");
		this.errorHandler = errorHandler;
	}

	/**
	 * Return the error handler.
	 */
	public ResponseErrorHandler getErrorHandler() {
		return this.errorHandler;
	}

	/**
	 * Configure default URI variable values. This is a shortcut for:
	 * <pre class="code">
	 * DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
	 * handler.setDefaultUriVariables(...);
	 *
	 * RestTemplate restTemplate = new RestTemplate();
	 * restTemplate.setUriTemplateHandler(handler);
	 * </pre>
	 * @param uriVars the default URI variable values
	 * @since 4.3
	 */
	@SuppressWarnings("deprecation")
	public void setDefaultUriVariables(Map<String, ?> uriVars) {
		if (this.uriTemplateHandler instanceof DefaultUriBuilderFactory) {
			((DefaultUriBuilderFactory) this.uriTemplateHandler).setDefaultUriVariables(uriVars);
		}
		else if (this.uriTemplateHandler instanceof org.springframework.web.util.AbstractUriTemplateHandler) {
			((org.springframework.web.util.AbstractUriTemplateHandler) this.uriTemplateHandler)
					.setDefaultUriVariables(uriVars);
		}
		else {
			throw new IllegalArgumentException(
					"This property is not supported with the configured UriTemplateHandler.");
		}
	}

	/**
	 * Configure a strategy for expanding URI templates.
	 * <p>By default, {@link DefaultUriBuilderFactory} is used and for
	 * backwards compatibility, the encoding mode is set to
	 * {@link EncodingMode#URI_COMPONENT URI_COMPONENT}. As of 5.0.8, prefer
	 * using {@link EncodingMode#TEMPLATE_AND_VALUES TEMPLATE_AND_VALUES}.
	 * <p><strong>Note:</strong> in 5.0 the switch from
	 * {@link org.springframework.web.util.DefaultUriTemplateHandler
	 * DefaultUriTemplateHandler} (deprecated in 4.3), as the default to use, to
	 * {@link DefaultUriBuilderFactory} brings in a different default for the
	 * {@code parsePath} property (switching from false to true).
	 * @param handler the URI template handler to use
	 */
	public void setUriTemplateHandler(UriTemplateHandler handler) {
		Assert.notNull(handler, "UriTemplateHandler must not be null");
		this.uriTemplateHandler = handler;
	}

	/**
	 * Return the configured URI template handler.
	 */
	public UriTemplateHandler getUriTemplateHandler() {
		return this.uriTemplateHandler;
	}


	// GET

	@Override
	@Nullable
	public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) throws RestClientException {
		// 1. 制作请求回调-acceptHeaderRequestCallback帮助创建Accept请求头指定接受的数据格式类型
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		// 2. 创建 HttpMessageConverterExtractor
		HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		// 3. 进入核心代码 -> 开始执行请求
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables);
	}
	
	// getForObject(String url, Class<T> responseType, Object... uriVariables) 
	// VS
	// getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables)
	// 前者是按照顺序去插入url变量,后者是按照名字去插入url变量的

	@Override
	@Nullable
	public <T> T getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables);
	}
	
	// getForObject(URI url, Class<T> responseType) 
	// 相比于其他两个: getForObject(..) 特点就是没有路径变量需要填充哦

	@Override
	@Nullable
	public <T> T getForObject(URI url, Class<T> responseType) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor);
	}
	
	// getForObject(..)  VS  getForEntity(..)
	// 前者返回执行类型的对象 VS 后者返回使用ResponseEntity包装后的对象,其中包括其他信息
	// 源码不同点: 前者使用HttpMessageConverterExtractor,后者使用ResponseExtractor

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor));
	}


	// HEAD

	@Override
	public HttpHeaders headForHeaders(String url, Object... uriVariables) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor(), uriVariables));
	}

	@Override
	public HttpHeaders headForHeaders(String url, Map<String, ?> uriVariables) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor(), uriVariables));
	}

	@Override
	public HttpHeaders headForHeaders(URI url) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor()));
	}


	// POST

	@Override
	@Nullable
	public URI postForLocation(String url, @Nullable Object request, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor(), uriVariables);
		return (headers != null ? headers.getLocation() : null);
	}

	@Override
	@Nullable
	public URI postForLocation(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor(), uriVariables);
		return (headers != null ? headers.getLocation() : null);
	}

	@Override
	@Nullable
	public URI postForLocation(URI url, @Nullable Object request) throws RestClientException {
		RequestCallback requestCallback = httpEntityCallback(request);
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor());
		return (headers != null ? headers.getLocation() : null);
	}

	@Override
	@Nullable
	public <T> T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T postForObject(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters());
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request,
			Class<T> responseType, Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request,
			Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor));
	}


	// PUT

	@Override
	public void put(String url, @Nullable Object request, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
	}

	@Override
	public void put(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
	}

	@Override
	public void put(URI url, @Nullable Object request) throws RestClientException {
		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null);
	}


	// PATCH

	@Override
	@Nullable
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T patchForObject(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters());
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor);
	}


	// DELETE

	@Override
	public void delete(String url, Object... uriVariables) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null, uriVariables);
	}

	@Override
	public void delete(String url, Map<String, ?> uriVariables) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null, uriVariables);
	}

	@Override
	public void delete(URI url) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null);
	}


	// OPTIONS

	@Override
	public Set<HttpMethod> optionsForAllow(String url, Object... uriVariables) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}

	@Override
	public Set<HttpMethod> optionsForAllow(String url, Map<String, ?> uriVariables) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}

	@Override
	public Set<HttpMethod> optionsForAllow(URI url) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}


	// exchange

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
			@Nullable HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
			@Nullable HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, method, requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Object... uriVariables) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(doExecute(requestEntity.getUrl(), requestEntity.getMethod(), requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType)
			throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(doExecute(requestEntity.getUrl(), requestEntity.getMethod(), requestCallback, responseExtractor));
	}


	// General execution

	/**
	 * {@inheritDoc}
	 * <p>To provide a {@code RequestCallback} or {@code ResponseExtractor} only,
	 * but not both, consider using:
	 * <ul>
	 * <li>{@link #acceptHeaderRequestCallback(Class)}
	 * <li>{@link #httpEntityCallback(Object)}
	 * <li>{@link #httpEntityCallback(Object, Type)}
	 * <li>{@link #responseEntityExtractor(Type)}
	 * </ul>
	 */
	@Override
	@Nullable
	public <T> T execute(String url, HttpMethod method, @Nullable RequestCallback requestCallback, @Nullable ResponseExtractor<T> responseExtractor, Object... uriVariables) throws RestClientException {
		// 1. 获取最终的url
		// 拿到UriTemplateHandler处理器 -> 将url中{}中指定的占位符根据uriVariables给处理调
		URI expanded = getUriTemplateHandler().expand(url, uriVariables);
		// 2. 开始执行
		return doExecute(expanded, method, requestCallback, responseExtractor);
	}

	/**
	 * {@inheritDoc}
	 * <p>To provide a {@code RequestCallback} or {@code ResponseExtractor} only,
	 * but not both, consider using:
	 * <ul>
	 * <li>{@link #acceptHeaderRequestCallback(Class)}
	 * <li>{@link #httpEntityCallback(Object)}
	 * <li>{@link #httpEntityCallback(Object, Type)}
	 * <li>{@link #responseEntityExtractor(Type)}
	 * </ul>
	 */
	@Override
	@Nullable
	public <T> T execute(String url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor, Map<String, ?> uriVariables)
			throws RestClientException {

		URI expanded = getUriTemplateHandler().expand(url, uriVariables);
		return doExecute(expanded, method, requestCallback, responseExtractor);
	}

	/**
	 * {@inheritDoc}
	 * <p>To provide a {@code RequestCallback} or {@code ResponseExtractor} only,
	 * but not both, consider using:
	 * <ul>
	 * <li>{@link #acceptHeaderRequestCallback(Class)}
	 * <li>{@link #httpEntityCallback(Object)}
	 * <li>{@link #httpEntityCallback(Object, Type)}
	 * <li>{@link #responseEntityExtractor(Type)}
	 * </ul>
	 */
	@Override
	@Nullable
	public <T> T execute(URI url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {

		return doExecute(url, method, requestCallback, responseExtractor);
	}

	/**
	 * Execute the given method on the provided URI.
	 * <p>The {@link ClientHttpRequest} is processed using the {@link RequestCallback};
	 * the response with the {@link ResponseExtractor}.
	 * @param url the fully-expanded URL to connect to
	 * @param method the HTTP method to execute (GET, POST, etc.)
	 * @param requestCallback object that prepares the request (can be {@code null})
	 * @param responseExtractor object that extracts the return value from the response (can be {@code null})
	 * @return an arbitrary object, as returned by the {@link ResponseExtractor}
	 */
	@Nullable
	protected <T> T doExecute(URI url, @Nullable HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {

		Assert.notNull(url, "URI is required");
		Assert.notNull(method, "HttpMethod is required");
		ClientHttpResponse response = null;
		try {
			// 1. 创建请求 -> 调用超类提供的 HttpAccessor#createRequest(..) ❗️❗️❗️ -> 注意:通话里面含有丰富的装饰哦
			ClientHttpRequest request = createRequest(url, method);
			// 2. 是否有RequestCallback -> 有的话,处理一下
			// 常见1: getForObject(..) / getForEntity(..) 通常使用 AcceptHeaderRequestCallback
			if (requestCallback != null) {
				requestCallback.doWithRequest(request);
			}
			// 3. 开始执行 execute()
			// ❗️❗️❗️ -> 如果以: 
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
			//	} 为例
			// request 就是 InterceptingClientHttpRequest 包装有 BufferingClientHttpRequestWrapper 包装有 HttpComponentsClientHttpRequest
			// 因此先去执行所有的 ClientHttpRequestInterceptor ,然后执行
			response = request.execute();
			// 4. 处理返回值 -> ❗️❗️❗️ 默认情况下: 当response是4xx或者5xx,会抛出异常,来自ClientHttpResponse异常体系结构哦
			handleResponse(url, method, response);
			// 5. 当第四步没有报出哦,使用 ResponseExtractor#extractData(..) 处理返回值的结果哦
			return (responseExtractor != null ? responseExtractor.extractData(response) : null);
		}
		catch (IOException ex) {
			String resource = url.toString();
			String query = url.getRawQuery();
			resource = (query != null ? resource.substring(0, resource.indexOf('?')) : resource);
			throw new ResourceAccessException("I/O error on " + method.name() +
					" request for \"" + resource + "\": " + ex.getMessage(), ex);
		}
		finally {
			if (response != null) {
				response.close();
			}
		}
	}

	/**
	 * Handle the given response, performing appropriate logging and
	 * invoking the {@link ResponseErrorHandler} if necessary.
	 * <p>Can be overridden in subclasses.
	 * @param url the fully-expanded URL to connect to
	 * @param method the HTTP method to execute (GET, POST, etc.)
	 * @param response the resulting {@link ClientHttpResponse}
	 * @throws IOException if propagated from {@link ResponseErrorHandler}
	 * @since 4.1.6
	 * @see #setErrorHandler
	 */
	protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
		// 处理给定的响应，执行适当的日志记录并在必要时调用ResponseErrorHandler
		
		// 1. 获取ResponseErrorHandler -> 默认使用的是: DefaultResponseErrorHandler
		ResponseErrorHandler errorHandler = getErrorHandler();
		// 2. 检查response是否有错误
		boolean hasError = errorHandler.hasError(response);
		if (logger.isDebugEnabled()) {
			try {
				int code = response.getRawStatusCode();
				HttpStatus status = HttpStatus.resolve(code);
				logger.debug("Response " + (status != null ? status : code));
			}
			catch (IOException ex) {
				// ignore
			}
		}
		// 3. 有错误的话,就使用 ResponseErrorHandler#handleError(..) 处理错误
		if (hasError) {
			errorHandler.handleError(url, method, response);
		}
	}

	/**
	 * Return a {@code RequestCallback} that sets the request {@code Accept}
	 * header based on the given response type, cross-checked against the
	 * configured message converters.
	 */
	public <T> RequestCallback acceptHeaderRequestCallback(Class<T> responseType) {
		// 返回一个RequestCallback
		// 它根据给定的响应类型设置请求Accept标头，并与配置的消息转换器进行交叉检查。
		return new AcceptHeaderRequestCallback(responseType);
	}

	/**
	 * Return a {@code RequestCallback} implementation that writes the given
	 * object to the request stream.
	 */
	public <T> RequestCallback httpEntityCallback(@Nullable Object requestBody) {
		return new HttpEntityRequestCallback(requestBody);
	}

	/**
	 * Return a {@code RequestCallback} implementation that:
	 * <ol>
	 * <li>Sets the request {@code Accept} header based on the given response
	 * type, cross-checked against the configured message converters.
	 * <li>Writes the given object to the request stream.
	 * </ol>
	 */
	public <T> RequestCallback httpEntityCallback(@Nullable Object requestBody, Type responseType) {
		return new HttpEntityRequestCallback(requestBody, responseType);
	}

	/**
	 * Return a {@code ResponseExtractor} that prepares a {@link ResponseEntity}.
	 */
	public <T> ResponseExtractor<ResponseEntity<T>> responseEntityExtractor(Type responseType) {
		// 返回: ResponseEntity<T> 将会使用 ResponseEntityResponseExtractor 提取
		return new ResponseEntityResponseExtractor<>(responseType);
	}

	/**
	 * Return a response extractor for {@link HttpHeaders}.
	 */
	protected ResponseExtractor<HttpHeaders> headersExtractor() {
		return this.headersExtractor;
	}

	private static <T> T nonNull(@Nullable T result) {
		Assert.state(result != null, "No result");
		return result;
	}


	/**
	 * Request callback implementation that prepares the request's accept headers.
	 */
	private class AcceptHeaderRequestCallback implements RequestCallback {
		// RequestCallback 的内部实现类之一
		
		// 命名:
		// AcceptHeader RequestCallback = 用来给request接受accept请求头的回调RequestCallback
		// ❗️❗️❗️ -> 帮助request指定请求方式.比如application/json等等

		@Nullable
		private final Type responseType; // 用户需要返回的数据类型

		public AcceptHeaderRequestCallback(@Nullable Type responseType) {
			this.responseType = responseType;
		}

		@Override
		public void doWithRequest(ClientHttpRequest request) throws IOException {
			// 当用户设置的需要返回的数据类型responseType非空时执行
			if (this.responseType != null) {
				// 1. 拿到所有的MessageConverter,检查哪一个支持读取整个responseType
				// 如果支持就拿到这个MessageConverter支持的MediaType类型,然后去重,然后进行排序 -> 最后收集起来即可
				List<MediaType> allSupportedMediaTypes = getMessageConverters().stream()
						// 1. 注意: canReadResponse(..)
						.filter(converter -> canReadResponse(this.responseType, converter))
						// 2. 100%的情况就是MappingJackson2HttpMessageConverter可以通过
						// 而 MappingJackson2HttpMessageConverter#getSupportedMediaTypes(..) 返回的就是 application/json 和 application/+json格式
						.flatMap(this::getSupportedMediaTypes)
						.distinct()
						.sorted(MediaType.SPECIFICITY_COMPARATOR)
						.collect(Collectors.toList());
				if (logger.isDebugEnabled()) {
					logger.debug("Accept=" + allSupportedMediaTypes);
				}
				// 2. 设置请求头中的accept的类型
				request.getHeaders().setAccept(allSupportedMediaTypes);
			}
		}

		private boolean canReadResponse(Type responseType, HttpMessageConverter<?> converter) {
			// 检查: HttpMessageConverter 是否支持 read 指定的 responseType 
			// canRead(..) 表示是否支持到时候读取响应体的中的信息,并转换为responseClass或responseType
			// note: ❗️❗️❗️ 上面的canRead()传入的mediaType都是null哦
			// 比如: -> 以这里为例
			//		ByteArrayHttpMessageConverter 支持byte[]
			//		StringHttpMessageConverter	支持String
			//		ResourceHttpMessageConverter 支持Resource
			//		SourceHttpMessageConverter 支持Source
			// 		...
			//      MappingJackson2HttpMessageConverter ❗️支持任何类型的 MappingJackson2HttpMessageConverter -> 支持的是 application/json 和 application/+json格式哦
			Class<?> responseClass = (responseType instanceof Class ? (Class<?>) responseType : null);
			if (responseClass != null) {
				return converter.canRead(responseClass, null);
			}
			else if (converter instanceof GenericHttpMessageConverter) {
				GenericHttpMessageConverter<?> genericConverter = (GenericHttpMessageConverter<?>) converter;
				return genericConverter.canRead(responseType, null, null);
			}
			return false;
		}

		private Stream<MediaType> getSupportedMediaTypes(HttpMessageConverter<?> messageConverter) {
			return messageConverter.getSupportedMediaTypes()
					.stream()
					.map(mediaType -> {
						if (mediaType.getCharset() != null) {
							return new MediaType(mediaType.getType(), mediaType.getSubtype());
						}
						return mediaType;
					});
		}
	}


	/**
	 * Request callback implementation that writes the given object to the request stream.
	 */
	private class HttpEntityRequestCallback extends AcceptHeaderRequestCallback {

		private final HttpEntity<?> requestEntity;

		public HttpEntityRequestCallback(@Nullable Object requestBody) {
			this(requestBody, null);
		}

		public HttpEntityRequestCallback(@Nullable Object requestBody, @Nullable Type responseType) {
			super(responseType);
			if (requestBody instanceof HttpEntity) {
				this.requestEntity = (HttpEntity<?>) requestBody;
			}
			else if (requestBody != null) {
				this.requestEntity = new HttpEntity<>(requestBody);
			}
			else {
				this.requestEntity = HttpEntity.EMPTY;
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public void doWithRequest(ClientHttpRequest httpRequest) throws IOException {
			super.doWithRequest(httpRequest);
			Object requestBody = this.requestEntity.getBody();
			if (requestBody == null) {
				HttpHeaders httpHeaders = httpRequest.getHeaders();
				HttpHeaders requestHeaders = this.requestEntity.getHeaders();
				if (!requestHeaders.isEmpty()) {
					requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>(values)));
				}
				if (httpHeaders.getContentLength() < 0) {
					httpHeaders.setContentLength(0L);
				}
			}
			else {
				Class<?> requestBodyClass = requestBody.getClass();
				Type requestBodyType = (this.requestEntity instanceof RequestEntity ?
						((RequestEntity<?>)this.requestEntity).getType() : requestBodyClass);
				HttpHeaders httpHeaders = httpRequest.getHeaders();
				HttpHeaders requestHeaders = this.requestEntity.getHeaders();
				MediaType requestContentType = requestHeaders.getContentType();
				for (HttpMessageConverter<?> messageConverter : getMessageConverters()) {
					if (messageConverter instanceof GenericHttpMessageConverter) {
						GenericHttpMessageConverter<Object> genericConverter =
								(GenericHttpMessageConverter<Object>) messageConverter;
						if (genericConverter.canWrite(requestBodyType, requestBodyClass, requestContentType)) {
							if (!requestHeaders.isEmpty()) {
								requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>(values)));
							}
							logBody(requestBody, requestContentType, genericConverter);
							genericConverter.write(requestBody, requestBodyType, requestContentType, httpRequest);
							return;
						}
					}
					else if (messageConverter.canWrite(requestBodyClass, requestContentType)) {
						if (!requestHeaders.isEmpty()) {
							requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>(values)));
						}
						logBody(requestBody, requestContentType, messageConverter);
						((HttpMessageConverter<Object>) messageConverter).write(
								requestBody, requestContentType, httpRequest);
						return;
					}
				}
				String message = "No HttpMessageConverter for " + requestBodyClass.getName();
				if (requestContentType != null) {
					message += " and content type \"" + requestContentType + "\"";
				}
				throw new RestClientException(message);
			}
		}

		private void logBody(Object body, @Nullable MediaType mediaType, HttpMessageConverter<?> converter) {
			if (logger.isDebugEnabled()) {
				if (mediaType != null) {
					logger.debug("Writing [" + body + "] as \"" + mediaType + "\"");
				}
				else {
					logger.debug("Writing [" + body + "] with " + converter.getClass().getName());
				}
			}
		}
	}


	/**
	 * Response extractor for {@link HttpEntity}.
	 */
	private class ResponseEntityResponseExtractor<T> implements ResponseExtractor<ResponseEntity<T>> {
		// ResponseExtractor<T>的内部实现类

		// 作用:
		// 实际的响应体转为实体Bean的T的工作交给deletegate
		// ResponseEntityResponseExtractor只是将上面拿到的结果设置到ResponseEntity中 ~~ 就是如此easy
		@Nullable
		private final HttpMessageConverterExtractor<T> delegate;

		
		public ResponseEntityResponseExtractor(@Nullable Type responseType) {
			if (responseType != null && Void.class != responseType) {
				this.delegate = new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
			}
			else {
				this.delegate = null;
			}
		}

		@Override
		public ResponseEntity<T> extractData(ClientHttpResponse response) throws IOException {
			if (this.delegate != null) {
				T body = this.delegate.extractData(response);
				return ResponseEntity.status(response.getRawStatusCode()).headers(response.getHeaders()).body(body);
			}
			else {
				return ResponseEntity.status(response.getRawStatusCode()).headers(response.getHeaders()).build();
			}
		}
	}


	/**
	 * Response extractor that extracts the response {@link HttpHeaders}.
	 */
	private static class HeadersExtractor implements ResponseExtractor<HttpHeaders> {

		@Override
		public HttpHeaders extractData(ClientHttpResponse response) {
			return response.getHeaders();
		}
	}

}
