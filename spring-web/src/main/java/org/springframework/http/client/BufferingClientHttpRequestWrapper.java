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
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

/**
 * Simple implementation of {@link ClientHttpRequest} that wraps another request.
 *
 * @author Arjen Poutsma
 * @since 3.1
 */
final class BufferingClientHttpRequestWrapper extends AbstractBufferingClientHttpRequest {
	// 命名:
	// Buffering ClientHttpRequestWrapper -> 返回带有缓存能力的ClientHttpRequest
	
	// 模式:
	// 包装器模式,目标类ClientHttpRequest存在request字段上,实际的缓存操作是AbstractBufferingClientHttpRequest提供的哦

	private final ClientHttpRequest request;


	BufferingClientHttpRequestWrapper(ClientHttpRequest request) {
		this.request = request;
	}
	
	// ---------------------
	// 实现超类中没有实现的方法吧: getMethod(..) getMethodValue(..) getURI(..) executeInternal(..)
	// ---------------------


	@Override
	@Nullable
	public HttpMethod getMethod() {
		return this.request.getMethod();
	}

	@Override
	public String getMethodValue() {
		return this.request.getMethodValue();
	}

	@Override
	public URI getURI() {
		return this.request.getURI();
	}

	@Override
	protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
		// ❗️❗️❗️ -> 执行请求
		
		// 1. 设置请求头
		this.request.getHeaders().putAll(headers);
		// 2. 将缓存的请求体的数据写入request.body字段中
		StreamUtils.copy(bufferedOutput, this.request.getBody());
		// 3. 执行请求
		ClientHttpResponse response = this.request.execute();
		// 4. 封装为: BufferingClientHttpResponseWrapper [❗️❗️❗️ 注意:这里是Response的Wrapper]
		return new BufferingClientHttpResponseWrapper(response);
	}

}
