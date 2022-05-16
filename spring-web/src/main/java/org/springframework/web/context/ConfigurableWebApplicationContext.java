/*
 * Copyright 2002-2014 the original author or authors.
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

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by configurable web application contexts.
 * Supported by {@link ContextLoader} and
 * {@link org.springframework.web.servlet.FrameworkServlet}.
 *
 * <p>Note: The setters of this interface need to be called before an
 * invocation of the {@link #refresh} method inherited from
 * {@link org.springframework.context.ConfigurableApplicationContext}.
 * They do not cause an initialization of the context on their own.
 *
 * @author Juergen Hoeller
 * @since 05.12.2003
 * @see #refresh
 * @see ContextLoader#createWebApplicationContext
 * @see org.springframework.web.servlet.FrameworkServlet#createWebApplicationContext
 */
public interface ConfigurableWebApplicationContext extends WebApplicationContext, ConfigurableApplicationContext {
	/*
	 * 将ApplicationContext的两个二级接口：web环境的WebApplicationContext、可配置的ConfigurableApplicationContext集成
	 * 并没有定义太多的操作，主要和Servlet上下文及配置文件。它一次性都继承了上面的两个二级接口，可以说是他俩的结合体
	 *
	 * setServletContext、setServletConfig、setNamespace、setConfigLocation、
	 *
	 * 注意：这样方法应该在refresh()方法之前被执行设置
	 *
	 * 此处有个非常重要的实现：
	 * AbstractRefreshableWebApplicationContext，它相当于在父类基础上，再实现了ConfigurableWebApplicationContext从而具有web的特性。
	 * 它自己是抽象类，但是具体实现有如下：
	 * 		XmlWebApplicationContext：这个可以说是web环境下Spring的默认容器。之前讲解过了ContextLoaderListener启动的时候，如果你没有指定web容器
	 * 		（比如若是Tomcat注解驱动的话，就会外面创建一个AnnotationConfigWebApplicationContext传进来），
	 * 		那么this.context = createWebApplicationContext(servletContext);
	 * 		（它会去找contextConfigLocation，找到xml配置文件）就会创建一个XmlWebApplicationContext类型的容器。至于为何呢？
	 * 		其实默认类型在配置文件：org\springframework\web\context\ContextLoader.properties这个文件里，里面写着：
	 */

	/**
	 * Prefix for ApplicationContext ids that refer to context path and/or servlet name.
	 */
	String APPLICATION_CONTEXT_ID_PREFIX = WebApplicationContext.class.getName() + ":";

	/**
	 * Name of the ServletConfig environment bean in the factory.
	 * @see javax.servlet.ServletConfig
	 */
	String SERVLET_CONFIG_BEAN_NAME = "servletConfig";


	/**
	 * Set the ServletContext for this web application context.
	 * <p>Does not cause an initialization of the context: refresh needs to be
	 * called after the setting of all configuration properties.
	 * @see #refresh()
	 */
	void setServletContext(@Nullable ServletContext servletContext);

	/**
	 * Set the ServletConfig for this web application context.
	 * Only called for a WebApplicationContext that belongs to a specific Servlet.
	 * @see #refresh()
	 */
	void setServletConfig(@Nullable ServletConfig servletConfig);

	/**
	 * Return the ServletConfig for this web application context, if any.
	 */
	@Nullable
	ServletConfig getServletConfig();

	/**
	 * Set the namespace for this web application context,
	 * to be used for building a default context config location.
	 * The root web application context does not have a namespace.
	 */
	void setNamespace(@Nullable String namespace);

	/**
	 * Return the namespace for this web application context, if any.
	 */
	@Nullable
	String getNamespace();

	/**
	 * Set the config locations for this web application context in init-param style,
	 * i.e. with distinct locations separated by commas, semicolons or whitespace.
	 * <p>If not set, the implementation is supposed to use a default for the
	 * given namespace or the root web application context, as appropriate.
	 */
	void setConfigLocation(String configLocation);

	/**
	 * Set the config locations for this web application context.
	 * <p>If not set, the implementation is supposed to use a default for the
	 * given namespace or the root web application context, as appropriate.
	 */
	void setConfigLocations(String... configLocations);

	/**
	 * Return the config locations for this web application context,
	 * or {@code null} if none specified.
	 */
	@Nullable
	String[] getConfigLocations();

}
