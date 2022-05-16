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

package org.springframework.web.context;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.lang.Nullable;

/**
 * Specialization of {@link ConfigurableEnvironment} allowing initialization of
 * servlet-related {@link org.springframework.core.env.PropertySource} objects at the
 * earliest moment that the {@link ServletContext} and (optionally) {@link ServletConfig}
 * become available.
 *
 * @author Chris Beams
 * @since 3.1.2
 * @see ConfigurableWebApplicationContext#getEnvironment()
 */
public interface ConfigurableWebEnvironment extends ConfigurableEnvironment {
	/*
	 * 关于 属性 property 和 环境profiles 的五个核心接口
	 * PropertyResolver 接口负责 Property 的获取（通过 key 获得 value），
	 * Environment 继承了这个接口，加入获得 Profile 的内容。
	 * ConfigurablePropertyResolver 继承了 PropertyResolver，为了解决 Property 的获取过程中涉及到的数据类型的转换和${..}表达式的解析问题。
	 * ConfigurableEnvironment 在此基础上，加入了 Profile 的设置功能。
	 * ConfigurableWebEnvironment 扩展了 web 功能，将 servlet 上下文作为配置源。
	 *
	 * AbstractEnvironment，StandardEnvironment，StandardServletEnvironment 都是 Spring 对上述功能的实现。
	 */

	/**
	 * Replace any {@linkplain
	 * org.springframework.core.env.PropertySource.StubPropertySource stub property source}
	 * instances acting as placeholders with real servlet context/config property sources
	 * using the given parameters.
	 * @param servletContext the {@link ServletContext} (may not be {@code null})
	 * @param servletConfig the {@link ServletConfig} ({@code null} if not available)
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources(
	 * org.springframework.core.env.MutablePropertySources, ServletContext, ServletConfig)
	 */
	void initPropertySources(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig);
	// 使用给定的参数，用真正的servlet上下文/config属性源替换充当占位符的任何存根属性源实例。

}
