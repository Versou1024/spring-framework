/*
 * Copyright 2002-2018 the original author or authors.
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;

/**
 * Abstract base class for most {@link GenericHttpMessageConverter} implementations.
 *
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @since 4.2
 * @param <T> the converted object type
 */
public abstract class AbstractGenericHttpMessageConverter<T> extends AbstractHttpMessageConverter<T> implements GenericHttpMessageConverter<T> {
	// 位于: org.springframework.http.converter
	
	// 作用: 
	// 在 AbstractHttpMessageConverter 的基础下, 兼容 GenericHttpMessageConverter接口的定义
	// 将 GenericHttpMessageConverter 接口的定义都利用其超类AbstractHttpMessageConverter的能力实现

	/**
	 * Construct an {@code AbstractGenericHttpMessageConverter} with no supported media types.
	 * @see #setSupportedMediaTypes
	 */
	protected AbstractGenericHttpMessageConverter() {
	}

	/**
	 * Construct an {@code AbstractGenericHttpMessageConverter} with one supported media type.
	 * @param supportedMediaType the supported media type
	 */
	protected AbstractGenericHttpMessageConverter(MediaType supportedMediaType) {
		super(supportedMediaType);
	}

	/**
	 * Construct an {@code AbstractGenericHttpMessageConverter} with multiple supported media type.
	 * @param supportedMediaTypes the supported media types
	 */
	protected AbstractGenericHttpMessageConverter(MediaType... supportedMediaTypes) {
		super(supportedMediaTypes);
	}


	@Override
	protected boolean supports(Class<?> clazz) {
		// ❗️❗️❗️❗️❗️❗️❗️❗️❗️
		// 支持任何clazz
		// 原因在于是泛型的,因此这里会支持任何类型的clazz,因此返回true
		return true;
	}

	@Override
	public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
		// ❗️❗️❗️
		// GenericHttpMessageConverter#canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType)
		// 不同于
		// HttpMessageConverter#canRead(Class<?> clazz, @Nullable MediaType mediaType)

		// type 不属于class就表示带有泛型信息的哦
		// 1. canRead((Class<?>) type, mediaType) 使用超类 AbstractHttpMessageConverter#canRead(..)的能力
		// 2. canRead(mediaType) 			      使用超类 AbstractHttpMessageConverter#canRead(..)只对mediaType进行检查
		return (type instanceof Class ? canRead((Class<?>) type, mediaType) : canRead(mediaType));
	}

	@Override
	public boolean canWrite(@Nullable Type type, Class<?> clazz, @Nullable MediaType mediaType) {
		return canWrite(clazz, mediaType);
	}

	/**
	 * This implementation sets the default headers by calling {@link #addDefaultHeaders},
	 * and then calls {@link #writeInternal}.
	 */
	@Override
	public final void write(final T t, @Nullable final Type type, @Nullable MediaType contentType,
			HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
		// 实现 GenericHttpMessageConverter#write(final T t, @Nullable final Type type, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
		// 作用: 
		// 将对象t填入请求体的输出流中 -> 即 type类型的t对象 填充到 HttpOutputMessage#getBody() 输出流中

		// 1. 获取请求头
		final HttpHeaders headers = outputMessage.getHeaders();
		// 2. 老规矩: 添加默认的请求头: content-type + content-length [调用超类 AbstractHttpMessageConverter#addDefaultHeaders(..)方法即可]
		addDefaultHeaders(headers, t, contentType);

		// 3.1 StreamingHttpOutputMessage
		if (outputMessage instanceof StreamingHttpOutputMessage) {
			StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) outputMessage;
			streamingOutputMessage.setBody(outputStream -> writeInternal(t, type, new HttpOutputMessage() {
				@Override
				public OutputStream getBody() {
					return outputStream;
				}
				@Override
				public HttpHeaders getHeaders() {
					return headers;
				}
			}));
		}
		// 3.2 非StreamingHttpOutputMessage类型的
		else {
			writeInternal(t, type, outputMessage);
			outputMessage.getBody().flush();
		}
	}

	@Override
	protected void writeInternal(T t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
		// 将超类的 AbstractHttpMessageConverter.writeInternal(T t, HttpOutputMessage outputMessage) 进行重写
		// 即 改为 writeInternal(t, null, outputMessage)

		writeInternal(t, null, outputMessage);
	}

	/**
	 * Abstract template method that writes the actual body. Invoked from {@link #write}.
	 * @param t the object to write to the output message
	 * @param type the type of object to write (may be {@code null})
	 * @param outputMessage the HTTP output message to write to
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotWritableException in case of conversion errors
	 */
	protected abstract void writeInternal(T t, @Nullable Type type, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException;

	// 重点: writeInternal(T t, @Nullable Type type, HttpOutputMessage outputMessage)
	// 携带有一个泛型消息Type,主要是用于JSON序列化和反序列时需要使用
	// 相比于超类的 writeInternal(T t, HttpOutputMessage outputMessage) 更加适合做序列化操作

}
