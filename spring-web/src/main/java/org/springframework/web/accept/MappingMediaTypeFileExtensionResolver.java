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

package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

/**
 * An implementation of {@code MediaTypeFileExtensionResolver} that maintains
 * lookups between file extensions and MediaTypes in both directions.
 *
 * <p>Initially created with a map of file extensions and media types.
 * Subsequently subclasses can use {@link #addMapping} to add more mappings.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.2
 */
public class MappingMediaTypeFileExtensionResolver implements MediaTypeFileExtensionResolver {
	// MediaTypeFileExtensionResolver的实现，它在两个方向上维护文件扩展名 fileExtensions 和媒体类型 MediaTypes 之间的查找。
	// 最初使用文件扩展名和媒体类型的映射创建。随后子类可以使用addMapping添加更多映射。

	// 此抽象类维护一些Map以及提供操作的方法，它维护了一个文件扩展名和MediaType的双向查找表。扩展名和MediaType的对应关系：
	//		一个MediaType对应N个扩展名
	//		一个扩展名最多只会属于一个MediaType~

	// 文件扩展 String 映射到 MediaType
	private final ConcurrentMap<String, MediaType> mediaTypes = new ConcurrentHashMap<>(64);

	// MediaType 映射的文件扩展集合 List<String>
	private final ConcurrentMap<MediaType, List<String>> fileExtensions = new ConcurrentHashMap<>(64);

	// 所有的文件后缀
	private final List<String> allFileExtensions = new CopyOnWriteArrayList<>();


	/**
	 * Create an instance with the given map of file extensions and media types.
	 */
	public MappingMediaTypeFileExtensionResolver(@Nullable Map<String, MediaType> mediaTypes) {
		if (mediaTypes != null) {
			Set<String> allFileExtensions = new HashSet<>(mediaTypes.size());
			mediaTypes.forEach((extension, mediaType) -> {
				String lowerCaseExtension = extension.toLowerCase(Locale.ENGLISH);
				this.mediaTypes.put(lowerCaseExtension, mediaType);
				addFileExtension(mediaType, lowerCaseExtension);
				allFileExtensions.add(lowerCaseExtension);
			});
			this.allFileExtensions.addAll(allFileExtensions);
		}
	}


	public Map<String, MediaType> getMediaTypes() {
		return this.mediaTypes;
	}

	protected List<MediaType> getAllMediaTypes() {
		return new ArrayList<>(this.mediaTypes.values());
	}

	/**
	 * Map an extension to a MediaType. Ignore if extension already mapped.
	 */
	protected void addMapping(String extension, MediaType mediaType) {
		MediaType previous = this.mediaTypes.putIfAbsent(extension, mediaType);
		if (previous == null) {
			addFileExtension(mediaType, extension);
			this.allFileExtensions.add(extension);
		}
	}

	private void addFileExtension(MediaType mediaType, String extension) {
		this.fileExtensions.computeIfAbsent(mediaType, key -> new CopyOnWriteArrayList<>()).add(extension);
	}


	@Override
	public List<String> resolveFileExtensions(MediaType mediaType) {
		// 从MediaType解析文件后缀

		// 1. 缓存是否命中
		List<String> fileExtensions = this.fileExtensions.get(mediaType);
		// 2. 未命中,返回空集合
		return (fileExtensions != null ? fileExtensions : Collections.emptyList());
	}

	@Override
	public List<String> getAllFileExtensions() {
		// 所有支持的文件后缀
		return Collections.unmodifiableList(this.allFileExtensions);
	}

	/**
	 * Use this method for a reverse lookup from extension to MediaType.
	 * @return a MediaType for the extension, or {@code null} if none found
	 */
	@Nullable
	protected MediaType lookupMediaType(String extension) {
		// 从文件后缀查MediaType
		return this.mediaTypes.get(extension.toLowerCase(Locale.ENGLISH));
	}

}
