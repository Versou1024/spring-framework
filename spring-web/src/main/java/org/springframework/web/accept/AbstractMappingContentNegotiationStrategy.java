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

package org.springframework.web.accept;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Base class for {@code ContentNegotiationStrategy} implementations with the
 * steps to resolve a request to media types.
 *
 * <p>First a key (e.g. "json", "pdf") must be extracted from the request (e.g.
 * file extension, query param). The key must then be resolved to media type(s)
 * through the base class {@link MappingMediaTypeFileExtensionResolver} which
 * stores such mappings.
 *
 * <p>The method {@link #handleNoMatch} allow sub-classes to plug in additional
 * ways of looking up media types (e.g. through the Java Activation framework,
 * or {@link javax.servlet.ServletContext#getMimeType}. Media types resolved
 * via base classes are then added to the base class
 * {@link MappingMediaTypeFileExtensionResolver}, i.e. cached for new lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public abstract class AbstractMappingContentNegotiationStrategy extends MappingMediaTypeFileExtensionResolver implements ContentNegotiationStrategy {
	// ContentNegotiationStrategy实现的基类，包含将请求解析为媒体类型的步骤。
	// 首先必须从请求中提取一个键（例如“json”、“pdf”）（例如文件扩展名、查询参数）。然后必须通过存储此类映射的基类MappingMediaTypeFileExtensionResolver将key解析为媒体类型。
	// 方法handleNoMatch允许子类插入查找媒体类型的其他方法（例如，通过 Java Activation 框架或javax.servlet.ServletContext.getMimeType 。然后将通过基类解析的媒体类型添加到基类MappingMediaTypeFileExtensionResolver ，即缓存用于新的查找。

	protected final Log logger = LogFactory.getLog(getClass());

	// Whether to only use the registered mappings to look up file extensions,
	// or also to use dynamic resolution (e.g. via {@link MediaTypeFactory}.
	// org.springframework.http.MediaTypeFactory是Spring5.0提供的一个工厂类
	// 它会读取/org/springframework/http/mime.types这个文件，里面有记录着对应关系
	private boolean useRegisteredExtensionsOnly = false;

	// Whether to ignore requests with unknown file extension. Setting this to
	// 默认false：即有不认识的扩展名，抛出异常：HttpMediaTypeNotAcceptableException
	private boolean ignoreUnknownExtensions = false;


	/**
	 * Create an instance with the given map of file extensions and media types.
	 */
	public AbstractMappingContentNegotiationStrategy(@Nullable Map<String, MediaType> mediaTypes) {
		// getMediaTypeKey：抽象方法(让子类把扩展名这个key提供出来)
		super(mediaTypes);
	}


	/**
	 * Whether to only use the registered mappings to look up file extensions,
	 * or also to use dynamic resolution (e.g. via {@link MediaTypeFactory}.
	 * <p>By default this is set to {@code false}.
	 */
	public void setUseRegisteredExtensionsOnly(boolean useRegisteredExtensionsOnly) {
		// 是否只使用注册的映射来查找文件扩展名，或者也使用动态解析（例如通过MediaTypeFactory解析)。
		// 默认情况下，这设置为false 。
		this.useRegisteredExtensionsOnly = useRegisteredExtensionsOnly;
	}

	public boolean isUseRegisteredExtensionsOnly() {
		return this.useRegisteredExtensionsOnly;
	}

	/**
	 * Whether to ignore requests with unknown file extension. Setting this to
	 * {@code false} results in {@code HttpMediaTypeNotAcceptableException}.
	 * <p>By default this is set to {@literal false} but is overridden in
	 * {@link PathExtensionContentNegotiationStrategy} to {@literal true}.
	 */
	public void setIgnoreUnknownExtensions(boolean ignoreUnknownExtensions) {
		// 是否忽略具有未知文件扩展名的请求。将此设置为false会导致HttpMediaTypeNotAcceptableException 。
		this.ignoreUnknownExtensions = ignoreUnknownExtensions;
	}

	public boolean isIgnoreUnknownExtensions() {
		return this.ignoreUnknownExtensions;
	}


	@Override
	public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest)
			throws HttpMediaTypeNotAcceptableException {
		// 重写 resolveMediaType
		// getMediaTypeKey(webRequest) 从request中提取表示暗示MediaType的key


		return resolveMediaTypeKey(webRequest, getMediaTypeKey(webRequest));
	}

	/**
	 * An alternative to {@link #resolveMediaTypes(NativeWebRequest)} that accepts
	 * an already extracted key.
	 * @since 3.2.16
	 */
	public List<MediaType> resolveMediaTypeKey(NativeWebRequest webRequest, @Nullable String key)
			throws HttpMediaTypeNotAcceptableException {

		// 1. 有提取出key
		if (StringUtils.hasText(key)) {
			// 1.1 根据key查询mediaType
			MediaType mediaType = lookupMediaType(key); // 调用超类MappingMediaTypeFileExtensionResolver的lookupMediaType()方法
			if (mediaType != null) {
				// 1.2 处理 key 和 mediaType
				handleMatch(key, mediaType);
				return Collections.singletonList(mediaType);
			}
			// 1.3 根据key查mediaType失败
			mediaType = handleNoMatch(webRequest, key);
			if (mediaType != null) {
				// 1.4 添加到mapping映射中
				addMapping(key, mediaType);
				return Collections.singletonList(mediaType);
			}
		}
		// 2. 没有key是,就默认是 */* 对所有都ok
		return MEDIA_TYPE_ALL_LIST;
	}


	/**
	 * Extract a key from the request to use to look up media types.
	 * @return the lookup key, or {@code null} if none
	 */
	@Nullable
	protected abstract String getMediaTypeKey(NativeWebRequest request);
	// getMediaTypeKey(webRequest) 从request中提取表示暗示MediaType的key
	// 例如文件扩展名\请求参数
	// 从请求中提取key以用于查找媒体类型

	/**
	 * Override to provide handling when a key is successfully resolved via
	 * {@link #lookupMediaType}.
	 */
	protected void handleMatch(String key, MediaType mediaType) {
		// 当通过lookupMediaType()成功解析key时，重写以提供处理
	}

	/**
	 * Override to provide handling when a key is not resolved via.
	 * {@link #lookupMediaType}. Sub-classes can take further steps to
	 * determine the media type(s). If a MediaType is returned from
	 * this method it will be added to the cache in the base class.
	 */
	@Nullable
	protected MediaType handleNoMatch(NativeWebRequest request, String key)
			throws HttpMediaTypeNotAcceptableException {
		// 覆盖以在未通过解析key时提供处理。
		// lookupMediaType() 子类可以采取进一步的步骤来确定媒体类型。
		// 如果从此方法返回 MediaType，它将被添加到基类的缓存中。

		// 1. 不仅仅使用注册的Extensions,还可以使用MediaTypeFactory尝试解析处MediaType
		if (!isUseRegisteredExtensionsOnly()) {
			Optional<MediaType> mediaType = MediaTypeFactory.getMediaType("file." + key);
			// 1.1 存在就返回
			if (mediaType.isPresent()) {
				return mediaType.get();
			}
		}
		// 2. 是否忽略位置文件后缀/key
		if (isIgnoreUnknownExtensions()) {
			return null;
		}
		// 3. 否则报错
		throw new HttpMediaTypeNotAcceptableException(getAllMediaTypes());
	}

}
