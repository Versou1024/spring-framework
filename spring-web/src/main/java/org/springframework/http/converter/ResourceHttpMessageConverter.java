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

package org.springframework.http.converter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

/**
 * Implementation of {@link HttpMessageConverter} that can read/write {@link Resource Resources}
 * and supports byte range requests.
 *
 * <p>By default, this converter can read all media types. The {@link MediaTypeFactory} is used
 * to determine the {@code Content-Type} of written resources.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Kazuki Shimizu
 * @since 3.0.2
 */
public class ResourceHttpMessageConverter extends AbstractHttpMessageConverter<Resource> {
	// 负责读取资源文件和写出资源文件数据
	// 可以读取/写入Resources并支持字节范围请求的HttpMessageConverter的实现。
	// 默认情况下，此转换器可以读取所有媒体类型。 MediaTypeFactory用于确定写入资源的Content-Type

	private final boolean supportsReadStreaming;


	/**
	 * Create a new instance of the {@code ResourceHttpMessageConverter}
	 * that supports read streaming, i.e. can convert an
	 * {@code HttpInputMessage} to {@code InputStreamResource}.
	 */
	public ResourceHttpMessageConverter() {
		// 调用超类的构造器
		// 表明默认是支持 MediaType.ALL 类型,也就是不对MediaType做限制哦

		super(MediaType.ALL);
		this.supportsReadStreaming = true;
	}

	/**
	 * Create a new instance of the {@code ResourceHttpMessageConverter}.
	 * @param supportsReadStreaming whether the converter should support
	 * read streaming, i.e. convert to {@code InputStreamResource}
	 * @since 5.0
	 */
	public ResourceHttpMessageConverter(boolean supportsReadStreaming) {
		super(MediaType.ALL);
		this.supportsReadStreaming = supportsReadStreaming;
	}


	@Override
	protected boolean supports(Class<?> clazz) {
		// 重写超类的supports()
		// 仅仅支持 Resource 类型

		return Resource.class.isAssignableFrom(clazz);
	}

	@Override
	protected Resource readInternal(Class<? extends Resource> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		// 重写超类的readInternal()
		// 直观感受：读的时候也只支持InputStreamResource和ByteArrayResource这两种resource的直接封装

		// 1. InputStreamResource类型
		if (this.supportsReadStreaming && InputStreamResource.class == clazz) {
			return new InputStreamResource(inputMessage.getBody()) {
				@Override
				public String getFilename() {
					// 1.1 文件name
					return inputMessage.getHeaders().getContentDisposition().getFilename();
				}
				@Override
				public long contentLength() throws IOException {
					// 1.2 content-length
					long length = inputMessage.getHeaders().getContentLength();
					return (length != -1 ? length : super.contentLength());
				}
			};
		}
		// 2. ByteArrayResource 类型
		else if (Resource.class == clazz || ByteArrayResource.class.isAssignableFrom(clazz)) {
			// 2.1 将请求体的输入流转换为ByteArray
			byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
			// 2.2 创建一个 ByteArrayResource 对象
			return new ByteArrayResource(body) {
				@Override
				@Nullable
				public String getFilename() {
					// 2.3 获取文件名的方法: 获取headers中content-disposition请求头中的fileName参数值
					return inputMessage.getHeaders().getContentDisposition().getFilename();
				}
			};
		}
		else {
			throw new HttpMessageNotReadableException("Unsupported resource class: " + clazz, inputMessage);
		}
	}

	@Override
	protected MediaType getDefaultContentType(Resource resource) {
		// 获取响应时的mediaType
		return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
	}

	@Override
	protected Long getContentLength(Resource resource, @Nullable MediaType contentType) throws IOException {
		// Don't try to determine contentLength on InputStreamResource - cannot be read afterwards...
		// Note: custom InputStreamResource subclasses could provide a pre-calculated content length!
		// 不要尝试确定 InputStreamResource 上的 contentLength - 之后无法读取...
		// 注意：自定义 InputStreamResource 子类可以提供预先计算的内容长度！
		if (InputStreamResource.class == resource.getClass()) {
			return null;
		}
		long contentLength = resource.contentLength();
		return (contentLength < 0 ? null : contentLength);
	}

	@Override
	protected void writeInternal(Resource resource, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {

		writeContent(resource, outputMessage);
	}

	protected void writeContent(Resource resource, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		// 写就很简单啦

		try {
			// 1. 获取InputStream
			InputStream in = resource.getInputStream();
			try {
				// 2. 复制到body上
				// 从InputStream写到body的OutputStream上
				StreamUtils.copy(in, outputMessage.getBody());
			}
			catch (NullPointerException ex) {
				// ignore, see SPR-13620
			}
			finally {
				try {
					// 3. 关闭流
					in.close();
				}
				catch (Throwable ex) {
					// ignore, see SPR-12999
				}
			}
		}
		catch (FileNotFoundException ex) {
			// ignore, see SPR-12999
		}
	}

}
