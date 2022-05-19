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

package org.springframework.web.servlet.view.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;

import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.servlet.view.AbstractView;

/**
 * Abstract base class for Jackson based and content type independent
 * {@link AbstractView} implementations.
 *
 * <p>Compatible with Jackson 2.6 and higher, as of Spring 4.3.
 *
 * @author Jeremy Grelle
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 4.1
 */
public abstract class AbstractJackson2View extends AbstractView {
	// AbstractJackson2View
	// 这个是一个比较新的Viw（@since 4.1），它是基于Jackson渲染的视图。

	private ObjectMapper objectMapper;

	private JsonEncoding encoding = JsonEncoding.UTF8;

	@Nullable
	private Boolean prettyPrint;

	private boolean disableCaching = true;

	protected boolean updateContentLength = false;


	// 唯一构造函数，并且还是protected的~~
	protected AbstractJackson2View(ObjectMapper objectMapper, String contentType) {
		this.objectMapper = objectMapper;
		configurePrettyPrint();
		setContentType(contentType);
		setExposePathVariables(false);
	}

	/**
	 * Set the {@code ObjectMapper} for this view.
	 * If not set, a default {@link ObjectMapper#ObjectMapper() ObjectMapper} will be used.
	 * <p>Setting a custom-configured {@code ObjectMapper} is one way to take further control of
	 * the JSON serialization process. The other option is to use Jackson's provided annotations
	 * on the types to be serialized, in which case a custom-configured ObjectMapper is unnecessary.
	 */
	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		configurePrettyPrint();
	}

	/**
	 * Return the {@code ObjectMapper} for this view.
	 */
	public final ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	/**
	 * Set the {@code JsonEncoding} for this view.
	 * By default, {@linkplain JsonEncoding#UTF8 UTF-8} is used.
	 */
	public void setEncoding(JsonEncoding encoding) {
		Assert.notNull(encoding, "'encoding' must not be null");
		this.encoding = encoding;
	}

	/**
	 * Return the {@code JsonEncoding} for this view.
	 */
	public final JsonEncoding getEncoding() {
		return this.encoding;
	}

	/**
	 * Whether to use the default pretty printer when writing the output.
	 * This is a shortcut for setting up an {@code ObjectMapper} as follows:
	 * <pre class="code">
	 * ObjectMapper mapper = new ObjectMapper();
	 * mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	 * </pre>
	 * <p>The default value is {@code false}.
	 */
	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
		configurePrettyPrint();
	}

	private void configurePrettyPrint() {
		// 在构造器中被调用或setPrettyPrint()中设置
		if (this.prettyPrint != null) {
			this.objectMapper.configure(SerializationFeature.INDENT_OUTPUT, this.prettyPrint);
		}
	}

	/**
	 * Disables caching of the generated JSON.
	 * <p>Default is {@code true}, which will prevent the client from caching the generated JSON.
	 */
	public void setDisableCaching(boolean disableCaching) {
		// 禁用生成的 JSON 的缓存。
		// 默认为true ，这将阻止客户端缓存生成的 JSON。


		this.disableCaching = disableCaching;
	}

	/**
	 * Whether to update the 'Content-Length' header of the response. When set to
	 * {@code true}, the response is buffered in order to determine the content
	 * length and set the 'Content-Length' header of the response.
	 * <p>The default setting is {@code false}.
	 */
	public void setUpdateContentLength(boolean updateContentLength) {
		// 是否更新响应的“Content-Length”标头。当设置为true时，响应被缓冲以确定内容长度并设置响应的“Content-Length”标头。
		// 默认设置为false 。
		this.updateContentLength = updateContentLength;
	}

	@Override
	protected void prepareResponse(HttpServletRequest request, HttpServletResponse response) {
		// 复写了父类的此方法~~~   setResponseContentType是父类的哟~~~~
		setResponseContentType(request, response);
		response.setCharacterEncoding(this.encoding.getJavaName());
		if (this.disableCaching) {
			response.addHeader("Cache-Control", "no-store");
		}
	}

	@Override
	protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		// 实现了父类的渲染方法~~~~

		ByteArrayOutputStream temporaryStream = null;
		OutputStream stream;

		// 1. 获取响应的输出流
		// 注意此处：updateContentLength默认值是false,所以会直接从response里面把输出流拿出来,而不用temp流
		if (this.updateContentLength) {
			temporaryStream = createTemporaryOutputStream();
			stream = temporaryStream;
		}
		else {
			stream = response.getOutputStream();
		}

		// 2. 从model中过滤出需要的value,并包装MappingJacksonValue后发那会
		Object value = filterAndWrapModel(model, request);

		// 3. 将MappingJacksonValue的value写入到模型中
		// 先通过stream得到一个JsonGenerator，然后先writePrefix(generator, object)
		// 然后objectMapper.writerWithView
		// 最后writeSuffix(generator, object);  然后flush即可~
		writeContent(stream, value);

		if (temporaryStream != null) {
			writeToResponse(response, temporaryStream);
		}
	}

	/**
	 * Filter and optionally wrap the model in {@link MappingJacksonValue} container.
	 * @param model the model, as passed on to {@link #renderMergedOutputModel}
	 * @param request current HTTP request
	 * @return the wrapped or unwrapped value to be rendered
	 */
	protected Object filterAndWrapModel(Map<String, Object> model, HttpServletRequest request) {
		// 从model中过滤出需要被当前视图使用的value,并将其包装到MappingJacksonValue中

		// 1. filterModel抽象方法，从指定的model中筛选出需要展示在当前视图的属性值
		// 一个Object对象
		Object value = filterModel(model);

		// 2. 把这两个属性值从model中取出来，选择性的放进container容器里面  最终返回~~~~
		Class<?> serializationView = (Class<?>) model.get(JsonView.class.getName());
		FilterProvider filters = (FilterProvider) model.get(FilterProvider.class.getName());
		if (serializationView != null || filters != null) {
			MappingJacksonValue container = new MappingJacksonValue(value);
			if (serializationView != null) {
				// 2.1 设置序列化的视图view
				container.setSerializationView(serializationView);
			}
			if (filters != null) {
				// 2.2 设置过滤器
				container.setFilters(filters);
			}
			value = container;
		}
		// 2.3 返回 MappingJacksonValue
		return value;
	}

	/**
	 * Write the actual JSON content to the stream.
	 * @param stream the output stream to use
	 * @param object the value to be rendered, as returned from {@link #filterModel}
	 * @throws IOException if writing failed
	 */
	protected void writeContent(OutputStream stream, Object object) throws IOException {
		// 将实际的 JSON 内容写入流。

		// 1. 根据响应输出流\JSONEncoding来获取JsonGenerator
		try (JsonGenerator generator = this.objectMapper.getFactory().createGenerator(stream, this.encoding)) {
			// 1.1 在主要内容之前写一个前缀 [抽象]
			writePrefix(generator, object);

			Object value = object;
			Class<?> serializationView = null;
			FilterProvider filters = null;

			// 1.2 MappingJacksonValue类型
			if (value instanceof MappingJacksonValue) {
				MappingJacksonValue container = (MappingJacksonValue) value;
				value = container.getValue();
				serializationView = container.getSerializationView();
				filters = container.getFilters();
			}

			// 1.3 配置 objectWriter
			ObjectWriter objectWriter = (serializationView != null ? this.objectMapper.writerWithView(serializationView) : this.objectMapper.writer());
			if (filters != null) {
				objectWriter = objectWriter.with(filters);
			}
			// 1.3 开始写vlaue
			objectWriter.writeValue(generator, value);

			// 1.4 写一个后缀 [抽象]
			writeSuffix(generator, object);
			// 1.5 从缓冲区刷新出去
			generator.flush();
		}
	}


	/**
	 * Set the attribute in the model that should be rendered by this view.
	 * When set, all other model attributes will be ignored.
	 */
	public abstract void setModelKey(String modelKey);

	/**
	 * Filter out undesired attributes from the given model.
	 * The return value can be either another {@link Map} or a single value object.
	 * @param model the model, as passed on to {@link #renderMergedOutputModel}
	 * @return the value to be rendered
	 */
	protected abstract Object filterModel(Map<String, Object> model);

	/**
	 * Write a prefix before the main content.
	 * @param generator the generator to use for writing content.
	 * @param object the object to write to the output message.
	 */
	protected void writePrefix(JsonGenerator generator, Object object) throws IOException {
	}

	/**
	 * Write a suffix after the main content.
	 * @param generator the generator to use for writing content.
	 * @param object the object to write to the output message.
	 */
	protected void writeSuffix(JsonGenerator generator, Object object) throws IOException {
	}

}
