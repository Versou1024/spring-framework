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
	
	// 作用:
	// 读取和写入字节数组的HttpMessageConverter的实现。
	// 默认情况下，此转换器支持所有媒体类型 ( */* )，并使用Content-Type的application/octet-stream进行写入。
	// 可以通过设置supportedMediaTypes属性来覆盖。

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
		// 将响应体转换为byte[] -> 即从 HttpInputMessage#getBody() 输入流获取的数据转为 byte[]
		
		// 0. 确定响应体的content-length
		long contentLength = inputMessage.getHeaders().getContentLength();
		// 1. 构造bos
		ByteArrayOutputStream bos = new ByteArrayOutputStream(contentLength >= 0 ? (int) contentLength : StreamUtils.BUFFER_SIZE);
		// 2. 将 响应体的输入流InputStream 复制到 ByteArrayOutputStream 中
		StreamUtils.copy(inputMessage.getBody(), bos);
		// 3. 转为字节数组 -> so easy
		return bos.toByteArray();
	}

	@Override
	protected Long getContentLength(byte[] bytes, @Nullable MediaType contentType) {
		// 用于在 write()方法中 向请求头中确定请求体的content-length
		return (long) bytes.length;
	}

	@Override
	protected void writeInternal(byte[] bytes, HttpOutputMessage outputMessage) throws IOException {
		// 写操作
		// 将对象即byte[]填入请求体的输出流中 -> 即  byte[] 填充到 HttpOutputMessage#getBody() 输出流中


		// 直接 将bytes写入outputStream总共
		StreamUtils.copy(bytes, outputMessage.getBody());
	}

}
