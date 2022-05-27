/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.context.support;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link Scope} wrapper for a ServletContext, i.e. for global web application attributes.
 *
 * <p>This differs from traditional Spring singletons in that it exposes attributes in the
 * ServletContext. Those attributes will get destroyed whenever the entire application
 * shuts down, which might be earlier or later than the shutdown of the containing Spring
 * ApplicationContext.
 *
 * <p>The associated destruction mechanism relies on a
 * {@link org.springframework.web.context.ContextCleanupListener} being registered in
 * {@code web.xml}. Note that {@link org.springframework.web.context.ContextLoaderListener}
 * includes ContextCleanupListener's functionality.
 *
 * <p>This scope is registered as default scope with key
 * {@link org.springframework.web.context.WebApplicationContext#SCOPE_APPLICATION "application"}.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.web.context.ContextCleanupListener
 */
public class ServletContextScope implements Scope, DisposableBean {
	// ServletContext 的Scope包装器，即用于全局 Web 应用程序属性。
	// 这与传统的 Spring 单例不同，它在 ServletContext 中公开属性。
	// 每当整个应用程序关闭时，这些属性都会被销毁，这可能早于或晚于包含 Spring ApplicationContext 的关闭。
	// 相关的销毁机制依赖于在web.xml中注册的org.springframework.web.context.ContextCleanupListener 。
	// 注意org.springframework.web.context.ContextLoaderListener包含 ContextCleanupListener 的功能

	private final ServletContext servletContext;

	private final Map<String, Runnable> destructionCallbacks = new LinkedHashMap<>();


	/**
	 * Create a new Scope wrapper for the given ServletContext.
	 * @param servletContext the ServletContext to wrap
	 */
	public ServletContextScope(ServletContext servletContext) {
		Assert.notNull(servletContext, "ServletContext must not be null");
		this.servletContext = servletContext;
	}


	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		Object scopedObject = this.servletContext.getAttribute(name);
		if (scopedObject == null) {
			scopedObject = objectFactory.getObject();
			this.servletContext.setAttribute(name, scopedObject);
		}
		return scopedObject;
	}

	@Override
	@Nullable
	public Object remove(String name) {
		// 移除同名的ServletContext属性或注册的销毁回调

		Object scopedObject = this.servletContext.getAttribute(name);
		if (scopedObject != null) {
			synchronized (this.destructionCallbacks) {
				this.destructionCallbacks.remove(name);
			}
			this.servletContext.removeAttribute(name);
			return scopedObject;
		}
		else {
			return null;
		}
	}

	@Override
	public void registerDestructionCallback(String name, Runnable callback) {
		// 注册销毁回调

		synchronized (this.destructionCallbacks) {
			this.destructionCallbacks.put(name, callback);
		}
	}

	@Override
	@Nullable
	public Object resolveContextualObject(String key) {
		// 解析上下文对象
		return null;
	}

	@Override
	@Nullable
	public String getConversationId() {
		// 获取会话ID

		return null;
	}


	/**
	 * Invoke all registered destruction callbacks.
	 * To be called on ServletContext shutdown.
	 * @see org.springframework.web.context.ContextCleanupListener
	 */
	@Override
	public void destroy() {
		// 调用 destructionCallbacks() 异步回调销毁机制被触发

		synchronized (this.destructionCallbacks) {
			for (Runnable runnable : this.destructionCallbacks.values()) {
				runnable.run();
			}
			this.destructionCallbacks.clear();
		}
	}

}
