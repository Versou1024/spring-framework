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

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * A strategy for resolving the requested media types for a request.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
@FunctionalInterface
public interface ContentNegotiationStrategy {
	// Spring MVC实现内容协商的策略接口
	// 这个策略接口就是想知道客户端的请求需要什么类型（MediaType）的数据List。从 上文 我们知道Spring MVC它支持了4种不同的协商机制，它都和此策略接口相关的。

	// Spring MVC实现了HTTP内容协商的同时，又进行了扩展。它支持4种协商方式：
	//		HTTP头Accept
	// 			accept:application/json
	//		扩展名
	// 			若我访问/test/1.xml返回的是xml，若访问/test/1.json返回的是json；完美~
	//		请求参数
	// 			请求URL：/test/1?format=xml返回xml；/test/1?format=json返回json。
	//		固定类型（producers）
	// 			利用@RequestMapping注解属性produces（可能你平时也在用，但并不知道原因）：

	// Spring MVC默认加载两个该策略接口的实现类：
	//		ServletPathExtensionContentNegotiationStrategy–>根据文件扩展名（支持RESTful）。
	//		HeaderContentNegotiationStrategy–>根据HTTP Header里的Accept字段（支持Http）

	/**
	 * A singleton list with {@link MediaType#ALL} that is returned from
	 * {@link #resolveMediaTypes} when no specific media types are requested.
	 * @since 5.0.5
	 */
	List<MediaType> MEDIA_TYPE_ALL_LIST = Collections.singletonList(MediaType.ALL);


	/**
	 * Resolve the given request to a list of media types. The returned list is
	 * ordered by specificity first and by quality parameter second.
	 * @param webRequest the current request
	 * @return the requested media types, or {@link #MEDIA_TYPE_ALL_LIST} if none
	 * were requested.
	 * @throws HttpMediaTypeNotAcceptableException if the requested media
	 * types cannot be parsed
	 */
	List<MediaType> resolveMediaTypes(NativeWebRequest webRequest)
			throws HttpMediaTypeNotAcceptableException;

}
