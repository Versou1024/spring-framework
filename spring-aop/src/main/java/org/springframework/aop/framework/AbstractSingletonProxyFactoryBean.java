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

package org.springframework.aop.framework;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Convenient superclass for {@link FactoryBean} types that produce singleton-scoped
 * proxy objects.
 *
 * <p>Manages pre- and post-interceptors (references, rather than
 * interceptor names, as in {@link ProxyFactoryBean}) and provides
 * consistent interface management.
 *
 * @author Juergen Hoeller
 * @since 2.0
 */
@SuppressWarnings("serial")
public abstract class AbstractSingletonProxyFactoryBean extends ProxyConfig
		implements FactoryBean<Object>, BeanClassLoaderAware, InitializingBean {
	// 用于生成单例的代理对象的FactoryBean类型的便捷超类
	// 能够管理前置拦截器和后置拦截器（引用，而不是拦截器名称，如ProxyFactoryBean ）并提供一致的接口管理。

	// 目标可以是任何对象，在这种情况下将创建一个 SingletonTargetSource。
	// 如果它是TargetSource，则不创建包装器TargetSource：
	// 这允许使用pooling或prototype TargetSource等
	@Nullable
	private Object target;

	// 指定被代理的接口集。
	// 如果未指定（默认），AOP 基础结构通过分析目标target确定哪些接口需要代理，代理目标对象实现的所有接口。
	@Nullable
	private Class<?>[] proxyInterfaces;

	// 在隐式的main主要拦截器之前设置要应用的附加拦截器（或advisor），例如 PerformanceMonitorInterceptor。
	// 您可以指定任何 AOP Alliance MethodInterceptor 或其他 Spring AOP Advices，以及 Spring AOP Advisors。
	@Nullable
	private Object[] preInterceptors;

	// 在隐式的main主要拦截器之后设置要应用的其他拦截器（或advisor）。
	// 您可以指定任何 AOP Alliance MethodInterceptor 或其他 Spring AOP Advices，以及 Spring AOP Advisors。
	@Nullable
	private Object[] postInterceptors;

	// 指定要使用的 AdvisorAdapterRegistry
	// 默认为GlobalAdvisorAdapterRegistry
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

	@Nullable
	private transient ClassLoader proxyClassLoader;

	@Nullable
	private Object proxy;


	/**
	 * Set the target object, that is, the bean to be wrapped with a transactional proxy.
	 * <p>The target may be any object, in which case a SingletonTargetSource will
	 * be created. If it is a TargetSource, no wrapper TargetSource is created:
	 * This enables the use of a pooling or prototype TargetSource etc.
	 * @see org.springframework.aop.TargetSource
	 * @see org.springframework.aop.target.SingletonTargetSource
	 * @see org.springframework.aop.target.LazyInitTargetSource
	 * @see org.springframework.aop.target.PrototypeTargetSource
	 * @see org.springframework.aop.target.CommonsPool2TargetSource
	 */
	public void setTarget(Object target) {
		this.target = target;
	}

	/**
	 * Specify the set of interfaces being proxied.
	 * <p>If not specified (the default), the AOP infrastructure works
	 * out which interfaces need proxying by analyzing the target,
	 * proxying all the interfaces that the target object implements.
	 */
	public void setProxyInterfaces(Class<?>[] proxyInterfaces) {
		this.proxyInterfaces = proxyInterfaces;
	}

	/**
	 * Set additional interceptors (or advisors) to be applied before the
	 * implicit transaction interceptor, e.g. a PerformanceMonitorInterceptor.
	 * <p>You may specify any AOP Alliance MethodInterceptors or other
	 * Spring AOP Advices, as well as Spring AOP Advisors.
	 * @see org.springframework.aop.interceptor.PerformanceMonitorInterceptor
	 */
	public void setPreInterceptors(Object[] preInterceptors) {
		this.preInterceptors = preInterceptors;
	}

	/**
	 * Set additional interceptors (or advisors) to be applied after the
	 * implicit transaction interceptor.
	 * <p>You may specify any AOP Alliance MethodInterceptors or other
	 * Spring AOP Advices, as well as Spring AOP Advisors.
	 */
	public void setPostInterceptors(Object[] postInterceptors) {
		this.postInterceptors = postInterceptors;
	}

	/**
	 * Specify the AdvisorAdapterRegistry to use.
	 * Default is the global AdvisorAdapterRegistry.
	 * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
	 */
	public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
		this.advisorAdapterRegistry = advisorAdapterRegistry;
	}

	/**
	 * Set the ClassLoader to generate the proxy class in.
	 * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the
	 * containing BeanFactory for loading all bean classes. This can be
	 * overridden here for specific proxies.
	 */
	public void setProxyClassLoader(ClassLoader classLoader) {
		this.proxyClassLoader = classLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		if (this.proxyClassLoader == null) {
			this.proxyClassLoader = classLoader;
		}
	}


	@Override
	public void afterPropertiesSet() {
		// 1. 初始化检查
		// target必须存在,不能是beanName,加载默认的额proxyClassLoader
		if (this.target == null) {
			throw new IllegalArgumentException("Property 'target' is required");
		}
		if (this.target instanceof String) {
			throw new IllegalArgumentException("'target' needs to be a bean reference, not a bean name as value");
		}
		if (this.proxyClassLoader == null) {
			this.proxyClassLoader = ClassUtils.getDefaultClassLoader();
		}

		// 2. 使用ProxyFactory -> 非常的熟悉 -> 这不就是那个手动编码的生成Proxy代理对象的Proxy工厂
		// ProxyFactory是只能通过代码硬编码进行编写 一般都是给spring自己使用 -- 比如这里
		ProxyFactory proxyFactory = new ProxyFactory();
		
		 // note: 注意通过

		// 3.1 附加的前置拦截器advisorAdapterRegistry.wrap(interceptor)如果是advice会被包装为DefaultPointcutAdvisor
		// DefaultPointcutAdvisor 其 POINTCUT.TRUE
		if (this.preInterceptors != null) {
			for (Object interceptor : this.preInterceptors) {
				proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(interceptor));
			}
		}

		// 3.2 添加主拦截器（通常是advisor）
		proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(createMainInterceptor()));

		// 3.3 附加的后置拦截器
		if (this.postInterceptors != null) {
			for (Object interceptor : this.postInterceptors) {
				proxyFactory.addAdvisor(this.advisorAdapterRegistry.wrap(interceptor));
			}
		}

		// 3.4 从当前AbstractSingletonProxyFactoryBean中copy一份ProxyConfig的配置过去
		proxyFactory.copyFrom(this);

		// 3.5 创建TargetSource并为设置到proxyFactory中
		TargetSource targetSource = createTargetSource(this.target);
		proxyFactory.setTargetSource(targetSource);

		
		// 3.6 设置需要代理的接口哦
		if (this.proxyInterfaces != null) {
			proxyFactory.setInterfaces(this.proxyInterfaces);
		}
		else if (!isProxyTargetClass()) {
			// Rely on AOP infrastructure to tell us what interfaces to proxy.
			Class<?> targetClass = targetSource.getTargetClass();
			if (targetClass != null) {
				proxyFactory.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
			}
		}

		// 4. 钩子方法 -> 允许子类去修改proxyFactory
		postProcessProxyFactory(proxyFactory);

		// 5. 获取代理对象哦
		this.proxy = proxyFactory.getProxy(this.proxyClassLoader);
	}

	/**
	 * Determine a TargetSource for the given target (or TargetSource).
	 * @param target the target. If this is an implementation of TargetSource it is
	 * used as our TargetSource; otherwise it is wrapped in a SingletonTargetSource.
	 * @return a TargetSource for this object
	 */
	protected TargetSource createTargetSource(Object target) {
		if (target instanceof TargetSource) {
			return (TargetSource) target;
		}
		else {
			return new SingletonTargetSource(target);
		}
	}

	/**
	 * A hook for subclasses to post-process the {@link ProxyFactory}
	 * before creating the proxy instance with it.
	 * @param proxyFactory the AOP ProxyFactory about to be used
	 * @since 4.2
	 */
	protected void postProcessProxyFactory(ProxyFactory proxyFactory) {
	}


	@Override
	public Object getObject() {
		if (this.proxy == null) {
			throw new FactoryBeanNotInitializedException();
		}
		return this.proxy;
	}

	@Override
	@Nullable
	public Class<?> getObjectType() {
		// 当前FactoryBean的类型是什么
		// proxy.getClass() > proxyInterfaces[0] > targetSource.getTargetClass() > target.getClass()
		
		if (this.proxy != null) {
			return this.proxy.getClass();
		}
		if (this.proxyInterfaces != null && this.proxyInterfaces.length == 1) {
			return this.proxyInterfaces[0];
		}
		if (this.target instanceof TargetSource) {
			return ((TargetSource) this.target).getTargetClass();
		}
		if (this.target != null) {
			return this.target.getClass();
		}
		return null;
	}

	@Override
	public final boolean isSingleton() {
		return true;
	}


	/**
	 * Create the "main" interceptor for this proxy factory bean.
	 * Typically an Advisor, but can also be any type of Advice.
	 * <p>Pre-interceptors will be applied before, post-interceptors
	 * will be applied after this interceptor.
	 */
	protected abstract Object createMainInterceptor();
	// 这个返回的Object一般典型的就是Advisor,当然也可以是Advice[因为后续会使用advisorAdapterRegistry.wrap()对advice包装为Advisor]
	// 需要子类实现主要的拦截器
	// 在附加的preInterceptors之后执行
	// 在附加的postInterceptors之前执行

}
