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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * Implementation of {@link HttpMessageConverter} that can read and write strings.
 *
 * <p>By default, this converter supports all media types (<code>&#42;/&#42;</code>),
 * and writes with a {@code Content-Type} of {@code text/plain}. This can be overridden
 * by setting the {@link #setSupportedMediaTypes supportedMediaTypes} property.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 */
public class StringHttpMessageConverter extends AbstractHttpMessageConverter<String> {
	
	// StringHttpMessageConverter
	// 这个是使用得非常广泛的一个消息转换器，专门处理请求体和响应体为字符串类型。

	private static final MediaType APPLICATION_PLUS_JSON = new MediaType("application", "*+json");

	/**
	 * The default charset used by the converter.
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;
	// 这就是为何你控制器直接return中文的时候会乱码的原因（若你不设置它的编码的话~）


	@Nullable
	private volatile List<Charset> availableCharsets;

	// 标识是否输出 Response Headers:Accept-Charset(默认true表示输出)
	private boolean writeAcceptCharset = false;


	/**
	 * A default constructor that uses {@code "ISO-8859-1"} as the default charset.
	 * @see #StringHttpMessageConverter(Charset)
	 */
	public StringHttpMessageConverter() {
		this(DEFAULT_CHARSET);
	}

	/**
	 * A constructor accepting a default charset to use if the requested content
	 * type does not specify one.
	 */
	public StringHttpMessageConverter(Charset defaultCharset) {
		// 支持 text/plain 与 */* 类型两种类型

		super(defaultCharset, MediaType.TEXT_PLAIN, MediaType.ALL);
	}


	/**
	 * Whether the {@code Accept-Charset} header should be written to any outgoing
	 * request sourced from the value of {@link Charset#availableCharsets()}.
	 * The behavior is suppressed if the header has already been set.
	 * <p>As of 5.2, by default is set to {@code false}.
	 */
	public void setWriteAcceptCharset(boolean writeAcceptCharset) {
		this.writeAcceptCharset = writeAcceptCharset;
	}


	@Override
	public boolean supports(Class<?> clazz) {
		// 只处理String类型~
		// 即 将String写入到请求体 或者 响应体应该转换为String 都是支持操作的哦

		return String.class == clazz;
	}

	@Override
	protected String readInternal(Class<? extends String> clazz, HttpInputMessage inputMessage) throws IOException {
		// 将响应体转换为String -> 即从 HttpInputMessage#getBody() 输入流获取的数据转为 String
		
		// 编码的原则为：
		// ❗️❗️❗️
		// 1、contentType 响应头中的content-type有指定编码就以指定的为准
		// 2、没指定，但是类型是`application/json`，统一按照UTF_8处理
		// 3、否则使用默认编码：getDefaultCharset  ISO_8859_1
		Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
		// 按照此编码，转换为字符串~~~
		return StreamUtils.copyToString(inputMessage.getBody(), charset);
	}

	@Override
	protected Long getContentLength(String str, @Nullable MediaType contentType) {
		// 显然，ContentLength和编码也是有关的~~~
		// 用于在 write()方法中 向请求头中确定请求体的content-length
		Charset charset = getContentTypeCharset(contentType);
		return (long) str.getBytes(charset).length;
	}


	@Override
	protected void addDefaultHeaders(HttpHeaders headers, String s, @Nullable MediaType type) throws IOException {
		// ❗️❗️❗️ -> 重写超类的 addDefaultHeaders(..) 方法哦
		// 1. 当contentType为空的时候,且type是兼容json格式的,先提前设置一下contentType
		if (headers.getContentType() == null ) {
			if (type != null && type.isConcrete() &&
					(type.isCompatibleWith(MediaType.APPLICATION_JSON) ||
					type.isCompatibleWith(APPLICATION_PLUS_JSON))) {
				// Prevent charset parameter for JSON..
				headers.setContentType(type);
			}
		}
		// 2. 沿用超类的处理方式
		super.addDefaultHeaders(headers, s, type);
	}

	@Override
	protected void writeInternal(String str, HttpOutputMessage outputMessage) throws IOException {
		// 写操作
		// 将对象即String填入请求体的输出流中 -> 即 String 填充到 HttpOutputMessage#getBody() 输出流中
		
		HttpHeaders headers = outputMessage.getHeaders();
		if (this.writeAcceptCharset && headers.get(HttpHeaders.ACCEPT_CHARSET) == null) {
			headers.setAcceptCharset(getAcceptedCharsets());
		}
		// 字符集类型
		Charset charset = getContentTypeCharset(headers.getContentType());
		// 按照charset字符集将str字符串写入到body的outputStream中
		StreamUtils.copy(str, charset, outputMessage.getBody());
	}


	/**
	 * Return the list of supported {@link Charset Charsets}.
	 * <p>By default, returns {@link Charset#availableCharsets()}.
	 * Can be overridden in subclasses.
	 * @return the list of accepted charsets
	 */
	protected List<Charset> getAcceptedCharsets() {
		List<Charset> charsets = this.availableCharsets;
		if (charsets == null) {
			charsets = new ArrayList<>(Charset.availableCharsets().values());
			this.availableCharsets = charsets;
		}
		return charsets;
	}

	private Charset getContentTypeCharset(@Nullable MediaType contentType) {

		// 1.1 请求头的content-type中获取charset
		if (contentType != null && contentType.getCharset() != null) {
			return contentType.getCharset();
		}
		// 1.2 content-type是json类型的,默认返回UTF_8类型的
		else if (contentType != null &&
				(contentType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
						contentType.isCompatibleWith(APPLICATION_PLUS_JSON))) {
			return StandardCharsets.UTF_8;
		}
		// 1.3 获取默认的charset
		else {
			Charset charset = getDefaultCharset();
			Assert.state(charset != null, "No default charset");
			return charset;
		}
	}

}
