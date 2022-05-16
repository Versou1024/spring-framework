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

package org.springframework.web.servlet.support;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * {@link org.springframework.web.WebApplicationInitializer WebApplicationInitializer}
 * to register a {@code DispatcherServlet} and use Java-based Spring configuration.
 *
 * <p>Implementations are required to implement:
 * <ul>
 * <li>{@link #getRootConfigClasses()} -- for "root" application context (non-web
 * infrastructure) configuration.
 * <li>{@link #getServletConfigClasses()} -- for {@code DispatcherServlet}
 * application context (Spring MVC infrastructure) configuration.
 * </ul>
 *
 * <p>If an application context hierarchy is not required, applications may
 * return all configuration via {@link #getRootConfigClasses()} and return
 * {@code null} from {@link #getServletConfigClasses()}.
 *
 * @author Arjen Poutsma
 * @author Chris Beams
 * @since 3.2
 */
public abstract class AbstractAnnotationConfigDispatcherServletInitializer extends AbstractDispatcherServletInitializer {
	/*
	 * AbstractAnnotationConfigDispatcherServletInitializer 抽象 注解配置以及DispatcherServlet的初始器
	 * 目的：加载Root或者子容器的AnnotationConfig
	 *
	 * WebApplicationInitializer注册DispatcherServlet并使用基于Java的Spring配置。
	 * 需要实现以下方法：
	 * getRootConfigClasses（）-- 用于“根”应用程序上下文（非web基础设施）配置。
	 * getServletConfigClasses（）-- 用于DispatcherServlet应用程序上下文（Spring MVC基础设施）配置。
	 * 如果不需要应用程序上下文层次结构，应用程序可以通过getRootConfigClasses（）返回所有配置，并从getServletConfigClasses（）返回null。
	 */

	/**
	 * {@inheritDoc}
	 * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
	 * providing it the annotated classes returned by {@link #getRootConfigClasses()}.
	 * Returns {@code null} if {@link #getRootConfigClasses()} returns {@code null}.
	 */
	@Override
	@Nullable //Spring告诉我们，这个是允许返回null的，也就是说是允许我们返回null的，后面会专门针对这里如果返回null，后面会是怎么样的流程的一个说明
	protected WebApplicationContext createRootApplicationContext() {
		// 实现 AbstractContextLoaderInitializer#createRootApplicationContext
		// 创建Spring根的ioc容器
		Class<?>[] configClasses = getRootConfigClasses();// 【抽象】- 获取根Root容器中的配置类
		if (!ObjectUtils.isEmpty(configClasses)) {
			AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();// 父容器 - spring - 基于注解的web上下文
			context.register(configClasses);
			return context;
		}
		else {
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
	 * providing it the annotated classes returned by {@link #getServletConfigClasses()}.
	 */
	@Override
	protected WebApplicationContext createServletApplicationContext() {
		// 实现：AbstractDispatcherServletInitializer#createServletApplicationContext

		// 1. 创建web的ioc容器 -- 一个基于注解的Web容器,能够对@RequestController\@Controller等注解感知
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext(); // 子容器 - web容器:一个基于注解的上下文
		Class<?>[] configClasses = getServletConfigClasses(); // 【抽象】 - 获取Servlet的配置类
		if (!ObjectUtils.isEmpty(configClasses)) {
			// 2. @Configuration的配置类，可以有多个会以累加的形式添加进去到子Web容器中
			context.register(configClasses);
		}
		return context;
	}

	/*
	 * 容器完全隔离后的好处是非常明显的，比如我们的web组件，就放在AppConfig里，其它的放在Root里，不要什么都往RootConfig里面塞，
	 */

	/**
	 * Specify {@code @Configuration} and/or {@code @Component} classes for the
	 * {@linkplain #createRootApplicationContext() root application context}.
	 * @return the configuration for the root application context, or {@code null}
	 * if creation and registration of a root context is not desired
	 */
	@Nullable
	protected abstract Class<?>[] getRootConfigClasses(); // 根容器的配置类；（Spring的配置文件）   提供给父容器；

	/**
	 * Specify {@code @Configuration} and/or {@code @Component} classes for the
	 * {@linkplain #createServletApplicationContext() Servlet application context}.
	 * @return the configuration for the Servlet application context, or
	 * {@code null} if all configuration is specified through root config classes.
	 */
	@Nullable
	protected abstract Class<?>[] getServletConfigClasses(); // web容器的配置类（SpringMVC配置文件）  提供给子容器；

}
