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

package org.springframework.web.reactive.config;

import java.util.Arrays;

import org.springframework.web.cors.CorsConfiguration;

/**
 * Assists with the creation of a {@link CorsConfiguration} instance for a given
 * URL path pattern.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see CorsConfiguration
 * @see CorsRegistry
 */
public class CorsRegistration {
	// 模式匹配的跨域配置项
	// spring 5.0 才出来哦,需要注意一下
	// 结合了: pathPattern + config

	private final String pathPattern; // 匹配的路径模式

	private final CorsConfiguration config; // 匹配上的Url将使用的跨域配置


	public CorsRegistration(String pathPattern) {
		this.pathPattern = pathPattern;
		// Same implicit default values as the @CrossOrigin annotation + allows simple methods
		// 这里调用了applyPermitDefaultValues()做一个默认的跨域的配置化操作
		this.config = new CorsConfiguration().applyPermitDefaultValues();
	}


	/**
	 * The list of allowed origins that be specific origins, e.g.
	 * {@code "https://domain1.com"}, or {@code "*"} for all origins.
	 * <p>A matched origin is listed in the {@code Access-Control-Allow-Origin}
	 * response header of preflight actual CORS requests.
	 * <p>By default all origins are allowed.
	 * <p><strong>Note:</strong> CORS checks use values from "Forwarded"
	 * (<a href="https://tools.ietf.org/html/rfc7239">RFC 7239</a>),
	 * "X-Forwarded-Host", "X-Forwarded-Port", and "X-Forwarded-Proto" headers,
	 * if present, in order to reflect the client-originated address.
	 * Consider using the {@code ForwardedHeaderFilter} in order to choose from a
	 * central place whether to extract and use, or to discard such headers.
	 * See the Spring Framework reference for more on this filter.
	 */
	public CorsRegistration allowedOrigins(String... origins) {
		// 设置 origins

		this.config.setAllowedOrigins(Arrays.asList(origins));
		return this;
	}

	/**
	 * Set the HTTP methods to allow, e.g. {@code "GET"}, {@code "POST"}, etc.
	 * <p>The special value {@code "*"} allows all methods.
	 * <p>By default "simple" methods {@code GET}, {@code HEAD}, and {@code POST}
	 * are allowed.
	 */
	public CorsRegistration allowedMethods(String... methods) {
		// 设置 methods

		this.config.setAllowedMethods(Arrays.asList(methods));
		return this;
	}

	/**
	 * Set the list of headers that a pre-flight request can list as allowed
	 * for use during an actual request.
	 * <p>The special value {@code "*"} may be used to allow all headers.
	 * <p>A header name is not required to be listed if it is one of:
	 * {@code Cache-Control}, {@code Content-Language}, {@code Expires},
	 * {@code Last-Modified}, or {@code Pragma} as per the CORS spec.
	 * <p>By default all headers are allowed.
	 */
	public CorsRegistration allowedHeaders(String... headers) {
		// 设置允许浏览器传过来的headers

		this.config.setAllowedHeaders(Arrays.asList(headers));
		return this;
	}

	/**
	 * Set the list of response headers other than "simple" headers, i.e.
	 * {@code Cache-Control}, {@code Content-Language}, {@code Content-Type},
	 * {@code Expires}, {@code Last-Modified}, or {@code Pragma}, that an
	 * actual response might have and can be exposed.
	 * <p>The special value {@code "*"} allows all headers to be exposed for
	 * non-credentialed requests.
	 * <p>By default this is not set.
	 */
	public CorsRegistration exposedHeaders(String... headers) {
		// 允许浏览器读取的响应头

		this.config.setExposedHeaders(Arrays.asList(headers));
		return this;
	}

	/**
	 * Whether the browser should send credentials, such as cookies along with
	 * cross domain requests, to the annotated endpoint. The configured value is
	 * set on the {@code Access-Control-Allow-Credentials} response header of
	 * preflight requests.
	 * <p><strong>NOTE:</strong> Be aware that this option establishes a high
	 * level of trust with the configured domains and also increases the surface
	 * attack of the web application by exposing sensitive user-specific
	 * information such as cookies and CSRF tokens.
	 * <p>By default this is not set in which case the
	 * {@code Access-Control-Allow-Credentials} header is also not set and
	 * credentials are therefore not allowed.
	 */
	public CorsRegistration allowCredentials(boolean allowCredentials) {
		// 允许浏览器使用Cookie

		this.config.setAllowCredentials(allowCredentials);
		return this;
	}

	/**
	 * Configure how long in seconds the response from a pre-flight request
	 * can be cached by clients.
	 * <p>By default this is set to 1800 seconds (30 minutes).
	 */
	public CorsRegistration maxAge(long maxAge) {
		// 允许浏览器缓存预检请求,不必每次发送

		this.config.setMaxAge(maxAge);
		return this;
	}

	protected String getPathPattern() {
		return this.pathPattern;
	}

	protected CorsConfiguration getCorsConfiguration() {
		return this.config;
	}

}
