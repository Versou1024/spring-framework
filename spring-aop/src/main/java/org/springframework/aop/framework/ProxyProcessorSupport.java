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

import java.io.Closeable;

import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * Base class with common functionality for proxy processors, in particular
 * ClassLoader management and the {@link #evaluateProxyInterfaces} algorithm.
 *
 * @author Juergen Hoeller
 * @since 4.1
 * @see AbstractAdvisingBeanPostProcessor
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
 */
@SuppressWarnings("serial")
public class ProxyProcessorSupport extends ProxyConfig implements Ordered, BeanClassLoaderAware, AopInfrastructureBean {
	// ProxyProcessorSupport = Proxy Processor Support
	// 主要是子类作为BeanPostProcessor等Processor处理器时处理Proxy时提供了一些公共方法实现
	// 1. 允许按照order排序\可以设置ClassLoader
	// 2. 检查目标class是否有有接口需要被代理哦

	/**
	 * This should run after all other processors, so that it can just add
	 * an advisor to existing proxies rather than double-proxy.
	 */

	// AOP的自动代理创建器必须在所有的别的processors之后执行，以确保它可以代理到所有的小伙伴们，即使需要双重代理得那种
	private int order = Ordered.LOWEST_PRECEDENCE;

	@Nullable
	private ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();

	private boolean classLoaderConfigured = false;


	/**
	 * Set the ordering which will apply to this processor's implementation
	 * of {@link Ordered}, used when applying multiple processors.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @param order the ordering value
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the ClassLoader to generate the proxy class in.
	 * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the containing
	 * {@link org.springframework.beans.factory.BeanFactory} for loading all bean classes.
	 * This can be overridden here for specific proxies.
	 */
	public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
		this.proxyClassLoader = classLoader;
		this.classLoaderConfigured = (classLoader != null);
	}

	/**
	 * Return the configured proxy ClassLoader for this processor.
	 */
	@Nullable
	protected ClassLoader getProxyClassLoader() {
		return this.proxyClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		if (!this.classLoaderConfigured) {
			this.proxyClassLoader = classLoader;
		}
	}


	/**
	 * Check the interfaces on the given bean class and apply them to the {@link ProxyFactory},
	 * if appropriate.
	 * <p>Calls {@link #isConfigurationCallbackInterface} and {@link #isInternalLanguageInterface}
	 * to filter for reasonable proxy interfaces, falling back to a target-class proxy otherwise.
	 * @param beanClass the class of the bean
	 * @param proxyFactory the ProxyFactory for the bean
	 */
	protected void evaluateProxyInterfaces(Class<?> beanClass, ProxyFactory proxyFactory) {
		// ❗️❗️❗️这是它提供的一个最为核心的方法：用于计算beanClass中可以被代理的接口,并设置到ProxyFactory中去
		
		// 1. 检查给定beanClass上的接口们，并交给proxyFactory处理
		Class<?>[] targetInterfaces = ClassUtils.getAllInterfacesForClass(beanClass, getProxyClassLoader());
		// 2. 标记：是否有存在【合理的】接口~~~
		boolean hasReasonableProxyInterface = false;
		for (Class<?> ifc : targetInterfaces) {
			// 2.1 只要接口不属于回调接口、且不属于语言groovy、且非空接口同时该接口必须还有方法才行，不要忘记了这步判断咯~~~~
			if (!isConfigurationCallbackInterface(ifc) && !isInternalLanguageInterface(ifc) && ifc.getMethods().length > 0) {
				// 2.2 认为有合理接口
				hasReasonableProxyInterface = true;
				break;
			}
		}
		// 3.1 有合理的接口
		if (hasReasonableProxyInterface) {
			for (Class<?> ifc : targetInterfaces) {
				// 这里Spring的Doc特别强调了：不能只把合理的接口设置进去，而是都得加入进去
				proxyFactory.addInterface(ifc);
			}
		}
		// 3.2 没有合理的接口
		else {
			// 3.2.1  没有任何合理的接口，表示使用CGLIB方式去创建代理了~~~~
			proxyFactory.setProxyTargetClass(true);
		}
	}

	/**
	 * Determine whether the given interface is just a container callback and
	 * therefore not to be considered as a reasonable proxy interface.
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is just a container callback
	 */
	protected boolean isConfigurationCallbackInterface(Class<?> ifc) {
		// 是否为匹配的回调接口，例如InitializingBean、DisposableBean、Closeable、AutoCloseable、Aware
		// 是的话，返回ture
		// 判断此接口类型是否属于：容器去回调的类型，这里例举处理一些接口 初始化、销毁、自动刷新、自动关闭、Aware感知等等
		return (InitializingBean.class == ifc || DisposableBean.class == ifc || Closeable.class == ifc ||
				AutoCloseable.class == ifc || ObjectUtils.containsElement(ifc.getInterfaces(), Aware.class));
	}

	/**
	 * Determine whether the given interface is a well-known internal language interface
	 * and therefore not to be considered as a reasonable proxy interface.
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is an internal language interface
	 */
	protected boolean isInternalLanguageInterface(Class<?> ifc) {
		// 是否是如下通用的接口。若实现的是这些接口也会排除，不认为它是实现了接口的类
		return (ifc.getName().equals("groovy.lang.GroovyObject") ||
				ifc.getName().endsWith(".cglib.proxy.Factory") ||
				ifc.getName().endsWith(".bytebuddy.MockAccess"));
	}

}
