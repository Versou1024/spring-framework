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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;

/**
 * Implementation of {@link ClientHttpResponse} that can not only check if
 * the response has a message body, but also if its length is 0 (i.e. empty)
 * by actually reading the input stream.
 *
 * @author Brian Clozel
 * @since 4.1.5
 * @see <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230 Section 3.3.3</a>
 */
class MessageBodyClientHttpResponseWrapper implements ClientHttpResponse {
	// 位于: org.springframework.web.client
	
	// 设计:
	// 属于包装器,持有一个ClientHttpResponse目标对象
	// Message Body ClientHttpResponse Wrapper = 不仅可以通过实际读取输入流来检查响应是否有消息体，还可以检查其长度是否为0（即空）。
	
	// 重点:
	// ❗️提供 hasMessageBody() 检查是否有消息体 \ hasEmptyMessageBody() 检查消息体是否为空 两个重要的方法

	private final ClientHttpResponse response;

	@Nullable
	private PushbackInputStream pushbackInputStream;


	public MessageBodyClientHttpResponseWrapper(ClientHttpResponse response) throws IOException {
		this.response = response;
	}


	/**
	 * Indicates whether the response has a message body.
	 * <p>Implementation returns {@code false} for:
	 * <ul>
	 * <li>a response status of {@code 1XX}, {@code 204} or {@code 304}</li>
	 * <li>a {@code Content-Length} header of {@code 0}</li>
	 * </ul>
	 * @return {@code true} if the response has a message body, {@code false} otherwise
	 * @throws IOException in case of I/O errors
	 */
	public boolean hasMessageBody() throws IOException {
		// ❗️❗️❗️指示响应是否具有消息正文。
		//	实现返回false ：
		//		1. 响应状态为1XX 、 204或304
		//		2. Content-Length为0的标头
		// return： 如果响应有消息正文，则为true ，否则为false
		
		HttpStatus status = HttpStatus.resolve(getRawStatusCode());
		if (status != null && (status.is1xxInformational() || status == HttpStatus.NO_CONTENT || status == HttpStatus.NOT_MODIFIED)) {
			return false;
		}
		if (getHeaders().getContentLength() == 0) {
			return false;
		}
		return true;
	}

	/**
	 * Indicates whether the response has an empty message body.
	 * <p>Implementation tries to read the first bytes of the response stream:
	 * <ul>
	 * <li>if no bytes are available, the message body is empty</li>
	 * <li>otherwise it is not empty and the stream is reset to its start for further reading</li>
	 * </ul>
	 * @return {@code true} if the response has a zero-length message body, {@code false} otherwise
	 * @throws IOException in case of I/O errors
	 */
	@SuppressWarnings("ConstantConditions")
	public boolean hasEmptyMessageBody() throws IOException {
		// 指示响应是否具有空消息正文。
		// 实现尝试读取响应流的第一个字节：
		//		1. 如果没有可用字节，则消息正文为空
		//		2. 否则它不为空并且流被重置到它的开始以供进一步阅读
		
		// 1. body为null返回true
		InputStream body = this.response.getBody();
		if (body == null) {
			return true;
		}
		// 2. 检查此输入流是支持mark和reset方法
		if (body.markSupported()) {
			// 2.1 标记此输入流中的当前位置为1,复原
			body.mark(1);
			// 2.2.1 如果无法从输入流中读取数据的下一个字节,返回true
			if (body.read() == -1) {
				return true;
			}
			// 2.2.2 否则返回false
			else {
				body.reset();
				return false;
			}
		}
		// 3. 检查此输入流是不支持mark和reset方法
		else {
			// 3.1 使用推回输入流PushbackInputStream封装响应体的输入流InputStream
			this.pushbackInputStream = new PushbackInputStream(body);
			int b = this.pushbackInputStream.read();
			if (b == -1) {
				return true;
			}
			else {
				this.pushbackInputStream.unread(b);
				return false;
			}
		}
	}


	@Override
	public HttpHeaders getHeaders() {
		return this.response.getHeaders();
	}

	@Override
	public InputStream getBody() throws IOException {
		return (this.pushbackInputStream != null ? this.pushbackInputStream : this.response.getBody());
	}

	@Override
	public HttpStatus getStatusCode() throws IOException {
		return this.response.getStatusCode();
	}

	@Override
	public int getRawStatusCode() throws IOException {
		return this.response.getRawStatusCode();
	}

	@Override
	public String getStatusText() throws IOException {
		return this.response.getStatusText();
	}

	@Override
	public void close() {
		this.response.close();
	}

}
