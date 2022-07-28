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
import java.util.List;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

/**
 * Strategy interface for converting from and to HTTP requests and responses.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 * @param <T> the converted object type
 */
public interface HttpMessageConverter<T> {
	/*
	 * 对 HttpMessage 接口的实现类进行转换
	 * 定义方法：
	 * 1、目标clazz是否可以被当前转换器read或者write
	 * 2、当前转换器所支持的MediaType媒体类型
	 * 3、开始read、开始write
	 *
	 * 对于HttpServletRequest和HttpServletResponse，可以分别调用getInputStream和getOutputStream来直接获取body。但是获取到的仅仅只是一段字符串
	 * 而对于java来说，处理一个对象肯定比处理一个字符串要方便得多，也好理解得多。
	 * 所以根据Content-Type头部，将body字符串转换为java对象是常有的事。
	 * 反过来，根据Accept头部，将java对象转换客户端期望格式的字符串也是必不可少的工作。这就是我们本文所讲述的消息转换器的工作~
	 *
	 * 消息转换器它能屏蔽你对底层转换的实现，分离你的关注点，让你专心操作java对象，其余的事情你就交给我Spring MVC吧~大大提高你的编码效率(可议说比源生Servlet开发高级太多了)
	 * Spring内置了很多HttpMessageConverter，比如MappingJackson2HttpMessageConverter，StringHttpMessageConverter，甚至还有FastJsonHttpMessageConverter（需导包和自己配置）
	 */

	/**
	 * Indicates whether the given class can be read by this converter.
	 * @param clazz the class to test for readability
	 * @param mediaType the media type to read (can be {@code null} if not specified);
	 * typically the value of a {@code Content-Type} header.
	 * @return {@code true} if readable; {@code false} otherwise
	 */
	boolean canRead(Class<?> clazz, @Nullable MediaType mediaType);
	// 指示此转换器是否可以读取给定的类。
	// mediaType – 要读取的媒体类型（如果未指定，可以为null ）；通常是Content-Type标头的值。
	// read -> 针对的是响应体的读取

	/**
	 * Indicates whether the given class can be written by this converter.
	 * @param clazz the class to test for writability
	 * @param mediaType the media type to write (can be {@code null} if not specified);
	 * typically the value of an {@code Accept} header.
	 * @return {@code true} if writable; {@code false} otherwise
	 */
	boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType);
	// 指示此转换器是否可以编写给定的类。
	// mediaType – 要写入的媒体类型（如果未指定，可以为null ）；通常是Accept标头的值
	// write - 针对的请求体的输出

	/**
	 * Return the list of {@link MediaType} objects supported by this converter.
	 * @return the list of supported media types, potentially an immutable copy
	 */
	List<MediaType> getSupportedMediaTypes();
	// 返回此转换器支持的MediaType对象列表。
	// return：支持的媒体类型列表，可能是不可变的副本

	/**
	 * Read an object of the given type from the given input message, and returns it.
	 * @param clazz the type of object to return. This type must have previously been passed to the
	 * {@link #canRead canRead} method of this interface, which must have returned {@code true}.
	 * @param inputMessage the HTTP input message to read from
	 * @return the converted object
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotReadableException in case of conversion errors
	 */
	T read(Class<? extends T> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException;
	// 从给定的输入消息 HttpInputMessage#getBody(..) 拿到InputStream输入流
	// 从中中读取给定类型clazz的对象，并返回它

	/**
	 * Write an given object to the given output message.
	 * @param t the object to write to the output message. The type of this object must have previously been
	 * passed to the {@link #canWrite canWrite} method of this interface, which must have returned {@code true}.
	 * @param contentType the content type to use when writing. May be {@code null} to indicate that the
	 * default content type of the converter must be used. If not {@code null}, this media type must have
	 * previously been passed to the {@link #canWrite canWrite} method of this interface, which must have
	 * returned {@code true}.
	 * @param outputMessage the message to write to
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotWritableException in case of conversion errors
	 */
	void write(T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException;
	// 将给定对象写入给定的输出消息 HttpOutputMessage#getBody(..) 请求体的输出流哦

}
