/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.servlet.view.script;

import org.springframework.web.servlet.view.UrlBasedViewResolver;

/**
 * Convenience subclass of {@link UrlBasedViewResolver} that supports
 * {@link ScriptTemplateView} and custom subclasses of it.
 *
 * <p>The view class for all views created by this resolver can be specified
 * via the {@link #setViewClass(Class)} property.
 *
 * <p><b>Note:</b> When chaining ViewResolvers this resolver will check for the
 * existence of the specified template resources and only return a non-null
 * View object if a template is actually found.
 *
 * @author Sebastien Deleuze
 * @since 4.2
 * @see ScriptTemplateConfigurer
 */
public class ScriptTemplateViewResolver extends UrlBasedViewResolver {
	// UrlBasedViewResolver 的便利子类，支持 ScriptTemplateView 及其自定义子类
	// 不难看出 -- 这是一个和脚本渲染有关的处理器,会处理为ScriptTemplateView

	/**
	 * Sets the default {@link #setViewClass view class} to {@link #requiredViewClass}:
	 * by default {@link ScriptTemplateView}.
	 */
	public ScriptTemplateViewResolver() {
		// 设置viewClass就是requiredViewClass()
		setViewClass(requiredViewClass());
	}

	/**
	 * A convenience constructor that allows for specifying {@link #setPrefix prefix}
	 * and {@link #setSuffix suffix} as constructor arguments.
	 * @param prefix the prefix that gets prepended to view names when building a URL
	 * @param suffix the suffix that gets appended to view names when building a URL
	 * @since 4.3
	 */
	public ScriptTemplateViewResolver(String prefix, String suffix) {
		// 一个方便的构造函数，允许将prefix和suffix指定为构造函数参数
		this();
		setPrefix(prefix);
		setSuffix(suffix);
	}


	@Override
	protected Class<?> requiredViewClass() {
		return ScriptTemplateView.class;
	}

}
