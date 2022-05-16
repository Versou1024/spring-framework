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

package org.springframework.http.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

/**
 * Implementation of {@link HttpMessageConverter} that can read and write byte arrays.
 *
 * <p>By default, this converter supports all media types (<code>&#42;/&#42;</code>), and
 * writes with a {@code Content-Type} of {@code application/octet-stream}. This can be
 * overridden by setting the {@link #setSupportedMediaTypes supportedMediaTypes} property.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 */
public class ByteArrayHttpMessageConverter extends AbstractHttpMessageConverter<byte[]> {

	/**
	 * Create a new instance of the {@code ByteArrayHttpMessageConverter}.
	 */
	public ByteArrayHttpMessageConverter() {
		// 支持  application/octet-stream 以及 */*
		super(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL);
	}


	@Override
	public boolean supports(Class<?> clazz) {
		// 支持 byte[].class 类型
		return byte[].class == clazz;
	}

	@Override
	public byte[] readInternal(Class<? extends byte[]> clazz, HttpInputMessage inputMessage) throws IOException {
		long contentLength = inputMessage.getHeaders().getContentLength();
		// 1. 构造bos
		ByteArrayOutputStream bos = new ByteArrayOutputStream(contentLength >= 0 ? (int) contentLength : StreamUtils.BUFFER_SIZE);
		// 2. 将 inputStream 写入到 outputSteam中
		StreamUtils.copy(inputMessage.getBody(), bos);
		// 3. 转为字节数组
		return bos.toByteArray();
	}

	@Override
	protected Long getContentLength(byte[] bytes, @Nullable MediaType contentType) {
		// 重写 getContentLength()
		return (long) bytes.length;
	}

	@Override
	protected void writeInternal(byte[] bytes, HttpOutputMessage outputMessage) throws IOException {
		// 写操作

		// 直接 将bytes写入outputStream总共
		StreamUtils.copy(bytes, outputMessage.getBody());
	}

}
