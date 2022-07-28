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

package org.springframework.web.client;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

/**
 * Common base class for exceptions that contain actual HTTP response data.
 *
 * @author Rossen Stoyanchev
 * @since 4.3
 */
public class RestClientResponseException extends RestClientException {
	// ❗️❗️❗️
	// 位于:  org.springframework.web.client
	
	// 作用: 
	// 包含实际 HTTP 响应数据的异常的通用基类。
	// 使用在RestTemplate中
	// 当RestTemplate发出的请求通过DefaultResponseErrorHandler检查后发现,属于4xx和5xx系列的错误代码时就会抛出其异常
	
	// 实现类:
	//	HttpStatusCodeException
	//		HttpClientErrorException: response的int型的rawStatusCode对应的HttpStatus为4xx系列
	//			....
	//		HttpServerErrorException: response的int型的rawStatusCode对应的HttpStatus为5xx系列
	//			... 
	//	UnknowHttpStatusCodeException: response中错误码无法解析为Spring已知的HttpStatus时,就用该异常 

	private static final long serialVersionUID = -8803556342728481792L;

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;


	// 错误码
	private final int rawStatusCode;

	// 错误文本
	private final String statusText;

	// 响应体消息
	private final byte[] responseBody;

	// 响应头
	@Nullable
	private final HttpHeaders responseHeaders;

	// 响应体的字符集
	@Nullable
	private final String responseCharset;


	/**
	 * Construct a new instance of with the given response data.
	 * @param statusCode the raw status code value
	 * @param statusText the status text
	 * @param responseHeaders the response headers (may be {@code null})
	 * @param responseBody the response body content (may be {@code null})
	 * @param responseCharset the response body charset (may be {@code null})
	 */
	public RestClientResponseException(String message, int statusCode, String statusText,
			@Nullable HttpHeaders responseHeaders, @Nullable byte[] responseBody, @Nullable Charset responseCharset) {

		super(message);
		this.rawStatusCode = statusCode;
		this.statusText = statusText;
		this.responseHeaders = responseHeaders;
		this.responseBody = (responseBody != null ? responseBody : new byte[0]);
		this.responseCharset = (responseCharset != null ? responseCharset.name() : null);
	}


	/**
	 * Return the raw HTTP status code value.
	 */
	public int getRawStatusCode() {
		return this.rawStatusCode;
	}

	/**
	 * Return the HTTP status text.
	 */
	public String getStatusText() {
		return this.statusText;
	}

	/**
	 * Return the HTTP response headers.
	 */
	@Nullable
	public HttpHeaders getResponseHeaders() {
		return this.responseHeaders;
	}

	/**
	 * Return the response body as a byte array.
	 */
	public byte[] getResponseBodyAsByteArray() {
		return this.responseBody;
	}

	/**
	 * Return the response body converted to String. The charset used is that
	 * of the response "Content-Type" or otherwise {@code "UTF-8"}.
	 */
	public String getResponseBodyAsString() {
		// ❗️❗️❗️ -> 当RestTemplate抛出UnKnownHttpStatusCodeException时可以使用该方法getResponseBodyAsString(..)获取消息体格式
		return getResponseBodyAsString(DEFAULT_CHARSET);
	}

	/**
	 * Return the response body converted to String. The charset used is that
	 * of the response "Content-Type" or otherwise the one given.
	 * @param fallbackCharset the charset to use on if the response doesn't specify.
	 * @since 5.1.11
	 */
	public String getResponseBodyAsString(Charset fallbackCharset) {
		if (this.responseCharset == null) {
			return new String(this.responseBody, fallbackCharset);
		}
		try {
			return new String(this.responseBody, this.responseCharset);
		}
		catch (UnsupportedEncodingException ex) {
			// should not occur
			throw new IllegalStateException(ex);
		}
	}

}
