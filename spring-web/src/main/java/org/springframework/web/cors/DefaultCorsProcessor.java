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

package org.springframework.web.cors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * The default implementation of {@link CorsProcessor}, as defined by the
 * <a href="https://www.w3.org/TR/cors/">CORS W3C recommendation</a>.
 *
 * <p>Note that when input {@link CorsConfiguration} is {@code null}, this
 * implementation does not reject simple or actual requests outright but simply
 * avoid adding CORS headers to the response. CORS processing is also skipped
 * if the response already contains CORS headers.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public class DefaultCorsProcessor implements CorsProcessor {

	private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);


	@Override
	@SuppressWarnings("resource")
	public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		// 1. 略过 VARY 请求头
		Collection<String> varyHeaders = response.getHeaders(HttpHeaders.VARY);
		if (!varyHeaders.contains(HttpHeaders.ORIGIN)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
		}
		if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
		}
		if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
		}

		// 2. 若不是跨域请求，不处理
		// 这个判断极其简单：请求中是否有Origin请求头,有这个头就是跨域请求
		if (!CorsUtils.isCorsRequest(request)) {
			return true;
		}

		// 3. 若响应头里已经设置好了Access-Control-Allow-Origin这个响应头，此处理器也不管了
		if (response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) != null) {
			logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
			return true;
		}

		// 4. 是否是预检请求，判断标准如下：
		// 是跨域请求 && 是`OPTIONS`请求 && 有Access-Control-Request-Method这个请求头
		boolean preFlightRequest = CorsUtils.isPreFlightRequest(request);
		// 若config == null，分两种case：
		// 是预检请求but木有给config，那就拒绝：给出状态码403
		//   response.setStatusCode(HttpStatus.FORBIDDEN)
		//   response.getBody().write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
		if (config == null) {
			// 5. 没有给定跨域配置

			// 5.1 预检请求,就直接拒绝请求 -- 直接决绝掉403并return false。都合法的话：就在response设置上一些头信息~~~
			if (preFlightRequest) {
				rejectRequest(new ServletServerHttpResponse(response));
				// 告诉后面的处理器不用再处理了 -- 例如拦截器
				// CorsInterceptor -- 返回false,就不需要到Handler上运行啦
				return false;
			}
			// 5.2 非预检请求,直接return true;表示自己处理不了,交给后面的处理吧
			else {
				// PreFlightHandler -- 返回
				// 虽然没给config，但不是预检请求（是真是请求，返回true）
				return true;
			}
		}

		// 真正的跨域处理逻辑~~~~
		// 它的处理逻辑比较简单，立即了W3C规范理解它起来非常简单，本文略
		// checkOrigin/checkMethods/checkHeaders等等方法最终都是委托给CorsConfiguration去做的
		// 正常处理CORS请求，大致是如下步骤：
		//1. 判断 origin 是否合法
		//2. 判断 method 是否合法
		//3. 判断 header是否合法
		//4. 若其中有一项不合法，直接决绝掉403并return false。都合法的话：就在response设置上一些头信息~~~
		return handleInternal(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), config, preFlightRequest);
	}

	/**
	 * Invoked when one of the CORS checks failed.
	 * The default implementation sets the response status to 403 and writes
	 * "Invalid CORS request" to the response.
	 */
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		response.setStatusCode(HttpStatus.FORBIDDEN);
		response.getBody().write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
		response.flush();
	}

	/**
	 * Handle the given request.
	 */
	protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
			CorsConfiguration config, boolean preFlightRequest) throws IOException {

		// 1. 检查各种跨域配置是否符合

		String requestOrigin = request.getHeaders().getOrigin();
		String allowOrigin = checkOrigin(config, requestOrigin);
		HttpHeaders responseHeaders = response.getHeaders();

		if (allowOrigin == null) {
			logger.debug("Reject: '" + requestOrigin + "' origin is not allowed");
			rejectRequest(response);
			return false;
		}

		HttpMethod requestMethod = getMethodToUse(request, preFlightRequest);
		List<HttpMethod> allowMethods = checkMethods(config, requestMethod);
		if (allowMethods == null) {
			logger.debug("Reject: HTTP '" + requestMethod + "' is not allowed");
			rejectRequest(response);
			return false;
		}

		List<String> requestHeaders = getHeadersToUse(request, preFlightRequest);
		List<String> allowHeaders = checkHeaders(config, requestHeaders);
		if (preFlightRequest && allowHeaders == null) {
			logger.debug("Reject: headers '" + requestHeaders + "' are not allowed");
			rejectRequest(response);
			return false;
		}

		// 2. 写上各种请求头

		responseHeaders.setAccessControlAllowOrigin(allowOrigin);

		if (preFlightRequest) {
			responseHeaders.setAccessControlAllowMethods(allowMethods);
		}

		if (preFlightRequest && !allowHeaders.isEmpty()) {
			responseHeaders.setAccessControlAllowHeaders(allowHeaders);
		}

		if (!CollectionUtils.isEmpty(config.getExposedHeaders())) {
			responseHeaders.setAccessControlExposeHeaders(config.getExposedHeaders());
		}

		if (Boolean.TRUE.equals(config.getAllowCredentials())) {
			responseHeaders.setAccessControlAllowCredentials(true);
		}

		if (preFlightRequest && config.getMaxAge() != null) {
			responseHeaders.setAccessControlMaxAge(config.getMaxAge());
		}

		response.flush();
		return true;
	}

	/**
	 * Check the origin and determine the origin for the response. The default
	 * implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected String checkOrigin(CorsConfiguration config, @Nullable String requestOrigin) {
		return config.checkOrigin(requestOrigin);
	}

	/**
	 * Check the HTTP method and determine the methods for the response of a
	 * pre-flight request. The default implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkHttpMethod(HttpMethod)}.
	 */
	@Nullable
	protected List<HttpMethod> checkMethods(CorsConfiguration config, @Nullable HttpMethod requestMethod) {
		return config.checkHttpMethod(requestMethod);
	}

	@Nullable
	private HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
		return (isPreFlight ? request.getHeaders().getAccessControlRequestMethod() : request.getMethod());
	}

	/**
	 * Check the headers and determine the headers for the response of a
	 * pre-flight request. The default implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected List<String> checkHeaders(CorsConfiguration config, List<String> requestHeaders) {
		return config.checkHeaders(requestHeaders);
	}

	private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
		HttpHeaders headers = request.getHeaders();
		return (isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.keySet()));
	}

}
