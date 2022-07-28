/*
 * Copyright 2002-2017 the original author or authors.
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
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link ClientHttpRequestFactory} implementation that uses standard JDK facilities.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 * @see java.net.HttpURLConnection
 * @see HttpComponentsClientHttpRequestFactory
 */
@SuppressWarnings("deprecation")
public class SimpleClientHttpRequestFactory implements ClientHttpRequestFactory, AsyncClientHttpRequestFactory {
	// 位于: org.springframework.http.client
	
	// 继承:
	// ClientHttpRequestFactory的简单实现之一
	// AsyncClientHttpRequestFactory接口已经弃用,不需要额外关注

	private static final int DEFAULT_CHUNK_SIZE = 4096;


	@Nullable
	private Proxy proxy;

	// 指示此请求工厂是否应在内部缓冲请求正文。默认为true 。
	// 通过 POST 或 PUT 发送大量数据时，建议将此属性更改为false ，以免内存不足。
	// 这将导致ClientHttpRequest直接流式传输到底层HttpURLConnection 
	// （如果事先知道Content-Length ），或者将使用“分块传输编码”（如果事先不知道Content-Length ）
	private boolean bufferRequestBody = true;

	// 设置在本地不缓冲请求正文时要写入每个块的字节数。
	// 注意，这个参数只在bufferRequestBody设置为false时使用，并且Content-Length是事先不知道的。
	private int chunkSize = DEFAULT_CHUNK_SIZE;

	// 设置底层 URLConnection 的连接超时（以毫秒为单位）。超时值 0 指定无限超时
	private int connectTimeout = -1;

	// 设置底层 URLConnection 的读取超时（以毫秒为单位）。超时值 0 指定无限超时。
	private int readTimeout = -1;

	// 设置底层 URLConnection 是否可以设置为“输出流”模式。默认为true 。
	// 启用输出流时，无法自动处理身份验证和重定向。
	// 如果禁用输出流，则永远不会调用底层连接的HttpURLConnection.setFixedLengthStreamingMode和HttpURLConnection.setChunkedStreamingMode方法。
	private boolean outputStreaming = true;

	// 为此请求工厂设置任务执行器。 创建异步请求时需要设置此属性。
	// 可忽略~~
	@Nullable
	private AsyncListenableTaskExecutor taskExecutor;


	/**
	 * Set the {@link Proxy} to use for this request factory.
	 */
	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	/**
	 * Indicate whether this request factory should buffer the
	 * {@linkplain ClientHttpRequest#getBody() request body} internally.
	 * <p>Default is {@code true}. When sending large amounts of data via POST or PUT,
	 * it is recommended to change this property to {@code false}, so as not to run
	 * out of memory. This will result in a {@link ClientHttpRequest} that either
	 * streams directly to the underlying {@link HttpURLConnection} (if the
	 * {@link org.springframework.http.HttpHeaders#getContentLength() Content-Length}
	 * is known in advance), or that will use "Chunked transfer encoding"
	 * (if the {@code Content-Length} is not known in advance).
	 * @see #setChunkSize(int)
	 * @see HttpURLConnection#setFixedLengthStreamingMode(int)
	 */
	public void setBufferRequestBody(boolean bufferRequestBody) {
		this.bufferRequestBody = bufferRequestBody;
	}

	/**
	 * Set the number of bytes to write in each chunk when not buffering request
	 * bodies locally.
	 * <p>Note that this parameter is only used when
	 * {@link #setBufferRequestBody(boolean) bufferRequestBody} is set to {@code false},
	 * and the {@link org.springframework.http.HttpHeaders#getContentLength() Content-Length}
	 * is not known in advance.
	 * @see #setBufferRequestBody(boolean)
	 */
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	/**
	 * Set the underlying URLConnection's connect timeout (in milliseconds).
	 * A timeout value of 0 specifies an infinite timeout.
	 * <p>Default is the system's default timeout.
	 * @see URLConnection#setConnectTimeout(int)
	 */
	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	/**
	 * Set the underlying URLConnection's read timeout (in milliseconds).
	 * A timeout value of 0 specifies an infinite timeout.
	 * <p>Default is the system's default timeout.
	 * @see URLConnection#setReadTimeout(int)
	 */
	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	/**
	 * Set if the underlying URLConnection can be set to 'output streaming' mode.
	 * Default is {@code true}.
	 * <p>When output streaming is enabled, authentication and redirection cannot be handled automatically.
	 * If output streaming is disabled, the {@link HttpURLConnection#setFixedLengthStreamingMode} and
	 * {@link HttpURLConnection#setChunkedStreamingMode} methods of the underlying connection will never
	 * be called.
	 * @param outputStreaming if output streaming is enabled
	 */
	public void setOutputStreaming(boolean outputStreaming) {
		this.outputStreaming = outputStreaming;
	}

	/**
	 * Set the task executor for this request factory. Setting this property is required
	 * for {@linkplain #createAsyncRequest(URI, HttpMethod) creating asynchronous requests}.
	 * @param taskExecutor the task executor
	 */
	public void setTaskExecutor(AsyncListenableTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}


	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
		// 1. 根据url拿到连接connection
		HttpURLConnection connection = openConnection(uri.toURL(), this.proxy);
		// 2. 设置连接connection
		prepareConnection(connection, httpMethod.name());

		// 3. 指示此请求工厂是否应在内部缓冲请求正文。默认为true  
		if (this.bufferRequestBody) {
			return new SimpleBufferingClientHttpRequest(connection, this.outputStreaming);
		}
		else {
			return new SimpleStreamingClientHttpRequest(connection, this.chunkSize, this.outputStreaming);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>Setting the {@link #setTaskExecutor taskExecutor} property is required before calling this method.
	 */
	@Override
	public AsyncClientHttpRequest createAsyncRequest(URI uri, HttpMethod httpMethod) throws IOException {
		Assert.state(this.taskExecutor != null, "Asynchronous execution requires TaskExecutor to be set");

		HttpURLConnection connection = openConnection(uri.toURL(), this.proxy);
		prepareConnection(connection, httpMethod.name());

		if (this.bufferRequestBody) {
			return new SimpleBufferingAsyncClientHttpRequest(
					connection, this.outputStreaming, this.taskExecutor);
		}
		else {
			return new SimpleStreamingAsyncClientHttpRequest(
					connection, this.chunkSize, this.outputStreaming, this.taskExecutor);
		}
	}

	/**
	 * Opens and returns a connection to the given URL.
	 * <p>The default implementation uses the given {@linkplain #setProxy(java.net.Proxy) proxy} -
	 * if any - to open a connection.
	 * @param url the URL to open a connection to
	 * @param proxy the proxy to use, may be {@code null}
	 * @return the opened connection
	 * @throws IOException in case of I/O errors
	 */
	protected HttpURLConnection openConnection(URL url, @Nullable Proxy proxy) throws IOException {
		// 打开并返回到给定 URL 的连接。
		// 默认实现使用给定的proxy（如果有）来打开连接
		
		// 1. 代理非空使用url.openConnection(proxy),否则使用url.openConnection()获取连接
		URLConnection urlConnection = (proxy != null ? url.openConnection(proxy) : url.openConnection());
		if (!HttpURLConnection.class.isInstance(urlConnection)) {
			throw new IllegalStateException("HttpURLConnection required for [" + url + "] but got: " + urlConnection);
		}
		return (HttpURLConnection) urlConnection;
	}

	/**
	 * Template method for preparing the given {@link HttpURLConnection}.
	 * <p>The default implementation prepares the connection for input and output, and sets the HTTP method.
	 * @param connection the connection to prepare
	 * @param httpMethod the HTTP request method ({@code GET}, {@code POST}, etc.)
	 * @throws IOException in case of I/O errors
	 */
	protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
		// 1. 设置连接超时时间和读取超时时间 -> 等于小于0表示永久不超时
		if (this.connectTimeout >= 0) {
			connection.setConnectTimeout(this.connectTimeout);
		}
		if (this.readTimeout >= 0) {
			connection.setReadTimeout(this.readTimeout);
		}

		// 2.
		// ClientHttpRequest是用来发送请求的,肯定需要对方的数据
		// 打算使用 URL 连接进行输入，请将 DoInput 标志设置为 true
		connection.setDoInput(true);

		// 3.1 GET请求允许重定向
		if ("GET".equals(httpMethod)) {
			connection.setInstanceFollowRedirects(true);
		}
		else {
			connection.setInstanceFollowRedirects(false);
		}

		// 3.2 POST或者PUT或者PATCH或者DELETE是需要携带请求体的
		// 即打算使用 URL 连接进行输出，将 DoOutput 标志设置为 true
		if ("POST".equals(httpMethod) || "PUT".equals(httpMethod) ||
				"PATCH".equals(httpMethod) || "DELETE".equals(httpMethod)) {
			connection.setDoOutput(true);
		}
		else {
			connection.setDoOutput(false);
		}

		// ❗️❗️❗️ 设置连接最终的请求方法
		connection.setRequestMethod(httpMethod);
	}

}
