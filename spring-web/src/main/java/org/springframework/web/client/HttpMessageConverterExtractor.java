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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * Response extractor that uses the given {@linkplain HttpMessageConverter entity converters}
 * to convert the response into a type {@code T}.
 *
 * @author Arjen Poutsma
 * @author Sam Brannen
 * @since 3.0
 * @param <T> the data type
 * @see RestTemplate
 */
public class HttpMessageConverterExtractor<T> implements ResponseExtractor<T> {
	
	// 作用:
	// 使用给定实体转换器HttpMessageConverter将响应转换为类型T的响应提取器

	// 用户希望获取到的返回值类型的Type
	private final Type responseType;

	// 用户希望获取到的返回值类型的Class
	@Nullable
	private final Class<T> responseClass;

	// RestTemplate持有的HttpMessageConverter
	private final List<HttpMessageConverter<?>> messageConverters;

	private final Log logger;


	/**
	 * Create a new instance of the {@code HttpMessageConverterExtractor} with the given response
	 * type and message converters. The given converters must support the response type.
	 */
	public HttpMessageConverterExtractor(Class<T> responseType, List<HttpMessageConverter<?>> messageConverters) {
		this((Type) responseType, messageConverters);
	}

	/**
	 * Creates a new instance of the {@code HttpMessageConverterExtractor} with the given response
	 * type and message converters. The given converters must support the response type.
	 */
	public HttpMessageConverterExtractor(Type responseType, List<HttpMessageConverter<?>> messageConverters) {
		this(responseType, messageConverters, LogFactory.getLog(HttpMessageConverterExtractor.class));
	}

	@SuppressWarnings("unchecked")
	HttpMessageConverterExtractor(Type responseType, List<HttpMessageConverter<?>> messageConverters, Log logger) {
		// 在RestTemplate经常使用功能的构造器
		
		Assert.notNull(responseType, "'responseType' must not be null");
		Assert.notEmpty(messageConverters, "'messageConverters' must not be empty");
		Assert.noNullElements(messageConverters, "'messageConverters' must not contain null elements");
		this.responseType = responseType;
		this.responseClass = (responseType instanceof Class ? (Class<T>) responseType : null);
		this.messageConverters = messageConverters;
		this.logger = logger;
	}


	@Override
	@SuppressWarnings({"unchecked", "rawtypes", "resource"})
	public T extractData(ClientHttpResponse response) throws IOException {
		// 定义: 如何从响应ClientHttpResponse提取到用户期望的实体类型
		// 从下列代码:不难看出,实际的数据转换工作还是交给HttpMessageConverter来处理
		// HttpMessageConverterExtractor当前类只是用来管理messageConverters,并在转换过程中加以控制,比如
		// 	 1. 转换前检查response的响应体是否存在,是否为空
		// 	 2. 管理并遍历HttpMessageConverter,根据是否GenericHttpMessageConverter做出相应的变换
		// 	 3. 转换失败后,还需要报出异常
		
		// 1. 包装为: MessageBodyClientHttpResponseWrapper 对象
		MessageBodyClientHttpResponseWrapper responseWrapper = new MessageBodyClientHttpResponseWrapper(response);
		// 2. 检查response中是否有请求体,且请求体非空 [❗️❗️❗️ -> 这就是利用MessageBodyClientHttpResponseWrapper装饰的额外能力]
		if (!responseWrapper.hasMessageBody() || responseWrapper.hasEmptyMessageBody()) {
			return null;
		}
		// 3. 拿到响应的contentType类型
		MediaType contentType = getContentType(responseWrapper);

		try {
			// 4.1 开始遍历 messageConverters
			for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
				// 4.1.1 GenericHttpMessageConverter类型的HttpMessageConverter
				if (messageConverter instanceof GenericHttpMessageConverter) {
					GenericHttpMessageConverter<?> genericMessageConverter = (GenericHttpMessageConverter<?>) messageConverter;
					// a: 检查是否支持read用户指定的responseType类型以及contentType
					if (genericMessageConverter.canRead(this.responseType, null, contentType)) {
						if (logger.isDebugEnabled()) {
							ResolvableType resolvableType = ResolvableType.forType(this.responseType);
							logger.debug("Reading to [" + resolvableType + "]");
						}
						// b: ❗️❗️❗️ 支持的话,就使用  HttpMessageConverter#read(..) 读取出实体类对象
						return (T) genericMessageConverter.read(this.responseType, null, responseWrapper);
					}
				}
				// 4.1.2 不属于GenericHttpMessageConverter类型的HttpMessageConverter
				if (this.responseClass != null) {
					// a: 检查是否支持read用户指定的responseType类型以及contentType
					if (messageConverter.canRead(this.responseClass, contentType)) {
						if (logger.isDebugEnabled()) {
							String className = this.responseClass.getName();
							logger.debug("Reading to [" + className + "] as \"" + contentType + "\"");
						}
						// b:  ❗️❗️❗️ 支持的话,就使用  HttpMessageConverter#read(..) 读取出实体类对象
						return (T) messageConverter.read((Class) this.responseClass, responseWrapper);
					}
				}
			}
		}
		catch (IOException | HttpMessageNotReadableException ex) {
			throw new RestClientException("Error while extracting response for type [" +
					this.responseType + "] and content type [" + contentType + "]", ex);
		}

		// 5. 啥也不是 -> 报错吧
		throw new UnknownContentTypeException(this.responseType, contentType,
				response.getRawStatusCode(), response.getStatusText(), response.getHeaders(),
				getResponseBody(response));
	}

	/**
	 * Determine the Content-Type of the response based on the "Content-Type"
	 * header or otherwise default to {@link MediaType#APPLICATION_OCTET_STREAM}.
	 * @param response the response
	 * @return the MediaType, or "application/octet-stream"
	 */
	protected MediaType getContentType(ClientHttpResponse response) {
		// response中指定的contentType > 默认的 'application/octet-stream'
		
		MediaType contentType = response.getHeaders().getContentType();
		if (contentType == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No content-type, using 'application/octet-stream'");
			}
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}
		return contentType;
	}

	private static byte[] getResponseBody(ClientHttpResponse response) {
		try {
			return FileCopyUtils.copyToByteArray(response.getBody());
		}
		catch (IOException ex) {
			// ignore
		}
		return new byte[0];
	}
}
