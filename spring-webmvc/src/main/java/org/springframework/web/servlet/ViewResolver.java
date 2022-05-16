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

package org.springframework.web.servlet;

import java.util.Locale;

import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by objects that can resolve views by name.
 *
 * <p>View state doesn't change during the running of the application,
 * so implementations are free to cache views.
 *
 * <p>Implementations are encouraged to support internationalization,
 * i.e. localized view resolution.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.web.servlet.view.InternalResourceViewResolver
 * @see org.springframework.web.servlet.view.ResourceBundleViewResolver
 * @see org.springframework.web.servlet.view.XmlViewResolver
 */
public interface ViewResolver {
	/*
	 * 视图解析接口：
	 * API:
	 * 通过viewName、locale解析为View
	 *
	 *
	 * ViewResolver用来将String类型的视图名和Locale解析为View类型的视图。
	 * View是用来渲染页面的，也就是将程序返回的参数填入模板里，生成html（也可能是其它类型）文件。
	 * 这里就有两个关键问题：
	 * 		使用哪个模板？
	 * 		用什么技术（规则）填入参数？
	 * 这其实是ViewResolver主要要做的工作
	 *
	 * ViewResolver需要找到渲染render所用的模板和所用的技术（也就是视图的类型）进行渲染，具体的渲染过程则交由不同的View实现类自己完成。
	 *
	 * 实现类：
	 * 		AbstractCachingViewResolver 基于缓存的抽象视图解析器
	 * 		UrlBasedViewResolver 实现了缓存，提供了prefix suffix拼接的url视图解析器。
	 * 		InternalResourceViewResolver 基于url的内部资源视图解析器。
	 * 		XmlViewResolver 基于xml的缓存视图解析器
	 * 		BeanNameViewResolver beanName来自容器，并且不支持缓存。
	 * 		ResourceBundleViewResolver 这个有点复杂
	 * 		reeMarkerViewResolver、VolocityViewResolver 都基于url 但会解析成特定的view
	 * 		实现类也非常的多，在Spring MVC里是一个非常非常重要的概念（比如什么时候返回页面，什么时候返回json呢？），因此后面会有专门的文章进行深入解读
	 */

	/**
	 * Resolve the given view by name.
	 * <p>Note: To allow for ViewResolver chaining, a ViewResolver should
	 * return {@code null} if a view with the given name is not defined in it.
	 * However, this is not required: Some ViewResolvers will always attempt
	 * to build View objects with the given name, unable to return {@code null}
	 * (rather throwing an exception when View creation failed).
	 * @param viewName name of the view to resolve
	 * @param locale the Locale in which to resolve the view.
	 * ViewResolvers that support internationalization should respect this.
	 * @return the View object, or {@code null} if not found
	 * (optional, to allow for ViewResolver chaining)
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 */
	@Nullable
	View resolveViewName(String viewName, Locale locale) throws Exception;

}
