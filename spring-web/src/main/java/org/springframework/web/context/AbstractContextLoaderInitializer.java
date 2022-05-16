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

package org.springframework.web.context;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.lang.Nullable;
import org.springframework.web.WebApplicationInitializer;

/**
 * Convenient base class for {@link WebApplicationInitializer} implementations
 * that register a {@link ContextLoaderListener} in the servlet context.
 *
 * <p>The only method required to be implemented by subclasses is
 * {@link #createRootApplicationContext()}, which gets invoked from
 * {@link #registerContextLoaderListener(ServletContext)}.
 *
 * @author Arjen Poutsma
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.2
 */
public abstract class AbstractContextLoaderInitializer implements WebApplicationInitializer {
	/*
	 * AbstractContextLoaderInitializer 抽象 上下文加载器 初始化
	 * 目的：负责根容器的创建
	 *
	 * ContextLoaderListener 继承了ContextLoader，而 ContextLoader -> 将负责根容器的创建
	 * 因此该类间接负责根容器的创建
	 *
	 * WebApplicationInitializer实现的抽象基类负责：在servlet上下文中注册ContextLoaderListener。
	 * 子类需要实现的唯一方法是createRootApplicationContext()，它可以从模板方法registerContextLoaderListener（ServletContext）调用。
	 */

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());


	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		// 间接被Servlet容器触发
		registerContextLoaderListener(servletContext);
	}

	/**
	 * Register a {@link ContextLoaderListener} against the given servlet context. The
	 * {@code ContextLoaderListener} is initialized with the application context returned
	 * from the {@link #createRootApplicationContext()} template method.
	 * @param servletContext the servlet context to register the listener against
	 */
	protected void registerContextLoaderListener(ServletContext servletContext) {
		//  1.【抽象方法】创建Root应用上下文, 被子类 AbstractAnnotationConfigDispatcherServletInitializer 实现 new 了一个 AnnotationConfigWebApplicationContext
		// 并向 AnnotationConfigWebApplicationContext 中注册配置类用于后续refresh()后加载BeanDefinition
		// 需要注意的:这里是没有调用 AnnotationConfigWebApplicationContext#refresh()刷新
		WebApplicationContext rootAppContext = createRootApplicationContext();
		if (rootAppContext != null) {
			// 2. 创建ContextLoaderListener监听ServletContext的加载和销毁
			// 需要注意: 这个监听会在DispatcherServlet被初始化后调用钩子方法,间接触发传递进去的rootAppContext#refresh() -> 直至使得父容器有bean
			ContextLoaderListener listener = new ContextLoaderListener(rootAppContext); // 核心点：注册ContextLoaderListener监听器，让它去初始化Spring父容器
			listener.setContextInitializers(getRootApplicationContextInitializers());
			// 4. 把监听器加入进来  这样该监听器就能监听ServletContext了，并且执行contextInitialized方法 --
			servletContext.addListener(listener);
		}
		else {
			logger.debug("No ContextLoaderListener registered, as " +
					"createRootApplicationContext() did not return an application context");
		}
	}

	/**
	 * Create the "<strong>root</strong>" application context to be provided to the
	 * {@code ContextLoaderListener}.
	 * <p>The returned context is delegated to
	 * {@link ContextLoaderListener#ContextLoaderListener(WebApplicationContext)} and will
	 * be established as the parent context for any {@code DispatcherServlet} application
	 * contexts. As such, it typically contains middle-tier services, data sources, etc.
	 * @return the root application context, or {@code null} if a root context is not
	 * desired
	 */
	@Nullable
	protected abstract WebApplicationContext createRootApplicationContext();

	/**
	 * Specify application context initializers to be applied to the root application
	 * context that the {@code ContextLoaderListener} is being created with.
	 * @since 4.2
	 * @see #createRootApplicationContext()
	 * @see ContextLoaderListener#setContextInitializers
	 */
	@Nullable
	protected ApplicationContextInitializer<?>[] getRootApplicationContextInitializers() {
		return null;
	}

}
