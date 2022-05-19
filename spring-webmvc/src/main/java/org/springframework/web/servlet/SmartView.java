/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.servlet;

/**
 * Provides additional information about a View such as whether it
 * performs redirects.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public interface SmartView extends View {
	// 增加一个接口: 提供有关视图的附加信息，例如它是否执行重定向。
	// 顾名思义RedirectView是用于页面跳转使用的。重定向我们都不陌生，因此我们下面主要看看RedirectView它的实现：
	//
	// 重定向在浏览器可议看到两个毫不相关的request请求。跳转的请求会丢失原请求的所有数据，
	// 一般的解决方法是将原请求中的数据放到跳转请求的URL中这样来传递，下面来看看RedirectView是怎么优雅的帮我们解决这个问题的~~~

	/**
	 * Whether the view performs a redirect.
	 */
	boolean isRedirectView();

}
