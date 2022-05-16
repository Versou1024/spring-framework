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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Abstract base class for most {@link HttpMessageConverter} implementations.
 *
 * <p>This base class adds support for setting supported {@code MediaTypes}, through the
 * {@link #setSupportedMediaTypes(List) supportedMediaTypes} bean property. It also adds
 * support for {@code Content-Type} and {@code Content-Length} when writing to output messages.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.0
 * @param <T> the converted object type
 */
public abstract class AbstractHttpMessageConverter<T> implements HttpMessageConverter<T> {
	/*
	 * 大多数HttpMessageConverter实现的抽象基类。
	 * 这个基类通过supportedMediaTypes bean 属性添加了对设置支持的MediaTypes的支持。在写入输出消息时，它还增加了对Content-Type和Content-Length的支持。
	 * AbstractHttpMessageConverter
	 * 一个基础抽象实现，它也还是个泛型类。对于泛型的控制，有如下特点：
	 * 最广的可以选择Object，不过Object并不都是可以序列化的，但是子类可以在覆盖的supports方法中进一步控制，因此选择Object是可以的
	 * 最符合的是Serializable，既完美满足泛型定义，本身也是个Java序列化/反序列化的充要条件
	 * 自定义的基类Bean，有些技术规范要求自己代码中的所有bean都继承自同一个自定义的基类BaseBean，这样可以在Serializable的基础上再进一步控制，满足自己的业务要求
	 * 若我们自己需要自定义一个消息转换器，大多数情况下也是继承抽象类再具体实现
	 */

	/** Logger available to subclasses. */
	protected final Log logger = HttpLogging.forLogName(getClass());

	// 它主要内部维护了这两个属性，可议构造器赋值，也可以set方法赋值~~
	private List<MediaType> supportedMediaTypes = Collections.emptyList();

	@Nullable
	private Charset defaultCharset;


	/**
	 * Construct an {@code AbstractHttpMessageConverter} with no supported media types.
	 * @see #setSupportedMediaTypes
	 */
	protected AbstractHttpMessageConverter() {
	}

	/**
	 * Construct an {@code AbstractHttpMessageConverter} with one supported media type.
	 * @param supportedMediaType the supported media type
	 */
	protected AbstractHttpMessageConverter(MediaType supportedMediaType) {
		setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
	}

	/**
	 * Construct an {@code AbstractHttpMessageConverter} with multiple supported media types.
	 * @param supportedMediaTypes the supported media types
	 */
	protected AbstractHttpMessageConverter(MediaType... supportedMediaTypes) {
		setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
	}

	/**
	 * Construct an {@code AbstractHttpMessageConverter} with a default charset and
	 * multiple supported media types.
	 * @param defaultCharset the default character set
	 * @param supportedMediaTypes the supported media types
	 * @since 4.3
	 */
	protected AbstractHttpMessageConverter(Charset defaultCharset, MediaType... supportedMediaTypes) {
		this.defaultCharset = defaultCharset;
		setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
	}


	/**
	 * Set the list of {@link MediaType} objects supported by this converter.
	 */
	public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
		Assert.notEmpty(supportedMediaTypes, "MediaType List must not be empty");
		this.supportedMediaTypes = new ArrayList<>(supportedMediaTypes);
	}

	@Override
	public List<MediaType> getSupportedMediaTypes() {
		return Collections.unmodifiableList(this.supportedMediaTypes);
	}

	/**
	 * Set the default character set, if any.
	 * @since 4.3
	 */
	public void setDefaultCharset(@Nullable Charset defaultCharset) {
		this.defaultCharset = defaultCharset;
	}

	/**
	 * Return the default character set, if any.
	 * @since 4.3
	 */
	@Nullable
	public Charset getDefaultCharset() {
		return this.defaultCharset;
	}


	/**
	 * This implementation checks if the given class is {@linkplain #supports(Class) supported},
	 * and if the {@linkplain #getSupportedMediaTypes() supported media types}
	 * {@linkplain MediaType#includes(MediaType) include} the given media type.
	 */
	@Override
	public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
		// 将canRead分为两部分，
		// 一部分检查是是否支持Clazz
		// 一部分检查是否支持mediaType
		return supports(clazz) && canRead(mediaType);
	}

	/**
	 * Returns {@code true} if any of the {@linkplain #setSupportedMediaTypes(List)
	 * supported} media types {@link MediaType#includes(MediaType) include} the
	 * given media type.
	 * @param mediaType the media type to read, can be {@code null} if not specified.
	 * Typically the value of a {@code Content-Type} header.
	 * @return {@code true} if the supported media types include the media type,
	 * or if the media type is {@code null}
	 */
	protected boolean canRead(@Nullable MediaType mediaType) {
		// 默认的canRead:

		// 1.如果请求体的mediaType为空,表示支持
		if (mediaType == null) {
			return true;
		}
		// 2. 否则检查supportedMediaType是否包含这个请求体的mediaType
		for (MediaType supportedMediaType : getSupportedMediaTypes()) {
			if (supportedMediaType.includes(mediaType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This implementation checks if the given class is
	 * {@linkplain #supports(Class) supported}, and if the
	 * {@linkplain #getSupportedMediaTypes() supported} media types
	 * {@linkplain MediaType#includes(MediaType) include} the given media type.
	 */
	@Override
	public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
		// 将canWrite分为两部分，
		// 一部分检查是是否支持Clazz
		// 一部分检查是否支持mediaType
		return supports(clazz) && canWrite(mediaType);
	}

	/**
	 * Returns {@code true} if the given media type includes any of the
	 * {@linkplain #setSupportedMediaTypes(List) supported media types}.
	 * @param mediaType the media type to write, can be {@code null} if not specified.
	 * Typically the value of an {@code Accept} header.
	 * @return {@code true} if the supported media types are compatible with the media type,
	 * or if the media type is {@code null}
	 */
	protected boolean canWrite(@Nullable MediaType mediaType) {
		// 默认的canWrite()

		// 1. mediaType为空，或者请求体的accept的content-type为*/* ,都表示支持读
		if (mediaType == null || MediaType.ALL.equalsTypeAndSubtype(mediaType)) {
			return true;
		}
		// 2. supportedMediaType 中的媒体类型是否兼容请求体的accept的mediaType
		// 只要兼容,就返回true
		for (MediaType supportedMediaType : getSupportedMediaTypes()) {
			if (supportedMediaType.isCompatibleWith(mediaType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This implementation simple delegates to {@link #readInternal(Class, HttpInputMessage)}.
	 * Future implementations might add some default behavior, however.
	 */
	@Override
	public final T read(Class<? extends T> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		// 1. 实际调用交给readInternal的抽象保护方法
		return readInternal(clazz, inputMessage);
	}

	/**
	 * This implementation sets the default headers by calling {@link #addDefaultHeaders},
	 * and then calls {@link #writeInternal}.
	 */
	@Override
	public final void write(final T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		//

		// 模板:
		// 1.获取请求头,向其中加入contentType\contentLength
		final HttpHeaders headers = outputMessage.getHeaders();
		addDefaultHeaders(headers, t, contentType);

		// 2. 调用writeInternal()由子类完成实际写入操作
		// 由于已经设置了content-type,因此writeInternal()中没有传mediaType啦
		if (outputMessage instanceof StreamingHttpOutputMessage) {
			// 2.1 对于 StreamingHttpOutputMessage 需要包装统一一下
			StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) outputMessage;
			streamingOutputMessage.setBody(outputStream -> writeInternal(t, new HttpOutputMessage() {
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
		else {
			writeInternal(t, outputMessage);

			// 最后它执行了flush，这也就是为何我们自己一般不需要flush的原因
			outputMessage.getBody().flush();
		}
	}

	/**
	 * Add default headers to the output message.
	 * <p>This implementation delegates to {@link #getDefaultContentType(Object)} if a
	 * content type was not provided, set if necessary the default character set, calls
	 * {@link #getContentLength}, and sets the corresponding headers.
	 * @since 4.2
	 */
	protected void addDefaultHeaders(HttpHeaders headers, T t, @Nullable MediaType contentType) throws IOException {
		// 向headers中组装content-type和content-length

		if (headers.getContentType() == null) {
			MediaType contentTypeToUse = contentType;
			if (contentType == null || !contentType.isConcrete()) {
				contentTypeToUse = getDefaultContentType(t);
			}
			else if (MediaType.APPLICATION_OCTET_STREAM.equals(contentType)) {
				MediaType mediaType = getDefaultContentType(t);
				contentTypeToUse = (mediaType != null ? mediaType : contentTypeToUse);
			}
			if (contentTypeToUse != null) {
				if (contentTypeToUse.getCharset() == null) {
					Charset defaultCharset = getDefaultCharset();
					if (defaultCharset != null) {
						contentTypeToUse = new MediaType(contentTypeToUse, defaultCharset);
					}
				}
				headers.setContentType(contentTypeToUse);
			}
		}
		if (headers.getContentLength() < 0 && !headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
			Long contentLength = getContentLength(t, headers.getContentType());
			if (contentLength != null) {
				headers.setContentLength(contentLength);
			}
		}
	}

	/**
	 * Returns the default content type for the given type. Called when {@link #write}
	 * is invoked without a specified content type parameter.
	 * <p>By default, this returns the first element of the
	 * {@link #setSupportedMediaTypes(List) supportedMediaTypes} property, if any.
	 * Can be overridden in subclasses.
	 * @param t the type to return the content type for
	 * @return the content type, or {@code null} if not known
	 */
	@Nullable
	protected MediaType getDefaultContentType(T t) throws IOException {
		// 获取默认的content-type:用于在输出响应体的时候,指定其content-type
		// 子类可重写

		List<MediaType> mediaTypes = getSupportedMediaTypes();
		return (!mediaTypes.isEmpty() ? mediaTypes.get(0) : null);
	}

	/**
	 * Returns the content length for the given type.
	 * <p>By default, this returns {@code null}, meaning that the content length is unknown.
	 * Can be overridden in subclasses.
	 * @param t the type to return the content length for
	 * @return the content length, or {@code null} if not known
	 */
	@Nullable
	protected Long getContentLength(T t, @Nullable MediaType contentType) throws IOException {
		// 扩展content-length
		// 子类可重写

		return null;
	}


	/**
	 * Indicates whether the given class is supported by this converter.
	 * @param clazz the class to test for support
	 * @return {@code true} if supported; {@code false} otherwise
	 */
	protected abstract boolean supports(Class<?> clazz);
	// 指示此转换器是否支持给定的类。

	/**
	 * Abstract template method that reads the actual object. Invoked from {@link #read}.
	 * @param clazz the type of object to return
	 * @param inputMessage the HTTP input message to read from
	 * @return the converted object
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotReadableException in case of conversion errors
	 */
	protected abstract T readInternal(Class<? extends T> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException;
	// 实际读操作:提供给子类实现

	/**
	 * Abstract template method that writes the actual body. Invoked from {@link #write}.
	 * @param t the object to write to the output message
	 * @param outputMessage the HTTP output message to write to
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotWritableException in case of conversion errors
	 */
	protected abstract void writeInternal(T t, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException;
	// 实际写操作:留给子类

}
