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

import java.util.LinkedList;
import java.util.List;

import org.springframework.util.Assert;

/**
 * Base class for proxy factories.
 * Provides convenient access to a configurable AopProxyFactory.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see #createAopProxy()
 */
@SuppressWarnings("serial")
public class ProxyCreatorSupport extends AdvisedSupport {
	/*
	 * AopProxy工厂的封装, 用于方便访问AopProxy。主要是封装AopProxyFactory，用来创建AopProxy
	 * -- 帮助其子类创建JDK和Cglib代理
	 *
	 * ProxyCreatorSupport 注册和触发监听器，借助DefaultAopProxyFactory获取代理
	 *
	 * 有三个子类：一点点发展起来的
	 * ProxyFactory 是只能通过代码硬编码进行编写 一般都是给spring自己使用。
	 * ProxyFactoryBean 是将我们的AOP和IOC融合起来，
	 * AspectJ 是目前大家最常用的 起到集成AspectJ和Spring
	 *
	 * Advice:通知，定义在连接点做什么，比如我们在方法前后进行日志打印（前置通知、后置通知、环绕通知等等）
	 * Pointcut：切点，决定advice应该作用于那个连接点，比如根据正则等规则匹配哪些方法需要增强（Pointcut 目前有getClassFilter（类匹配），getMethodMatcher（方法匹配），Pointcut TRUE （全匹配））
	 * JoinPoint：连接点，就是spring允许你是通知（Advice）的地方，那可就真多了，基本每个方法的前、后（两者都有也行），或抛出异常是时都可以是连接点，spring只支持方法连接点。其他如AspectJ还可以让你在构造器或属性注入时都行，不过那不是咱们关注的，只要记住，和方法有关的前前后后都是连接点（通知方法里都可以获取到这个连接点，顺便获取到相关信息）。
	 * Advisor：把pointcut和advice连接起来（可由Spring去完成，我们都交给容器管理就行，当然，你也可以手动完成）Spring的Advisor是Pointcut和Advice的配置器，它是将Advice注入程序中Pointcut位置的代码。
	 */

	private AopProxyFactory aopProxyFactory;

	private final List<AdvisedSupportListener> listeners = new LinkedList<>();

	/** Set to true when the first AOP proxy has been created. */
	private boolean active = false;


	/**
	 * Create a new ProxyCreatorSupport instance.
	 */
	public ProxyCreatorSupport() {
		// 空构造函数，默认构造：DefaultAopProxyFactory
		this.aopProxyFactory = new DefaultAopProxyFactory();
	}

	/**
	 * Create a new ProxyCreatorSupport instance.
	 * @param aopProxyFactory the AopProxyFactory to use
	 */
	public ProxyCreatorSupport(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}


	/**
	 * Customize the AopProxyFactory, allowing different strategies
	 * to be dropped in without changing the core framework.
	 * <p>Default is {@link DefaultAopProxyFactory}, using dynamic JDK
	 * proxies or CGLIB proxies based on the requirements.
	 */
	public void setAopProxyFactory(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}

	/**
	 * Return the AopProxyFactory that this ProxyConfig uses.
	 */
	public AopProxyFactory getAopProxyFactory() {
		return this.aopProxyFactory;
	}

	/**
	 * Add the given AdvisedSupportListener to this proxy configuration.
	 * @param listener the listener to register
	 */
	public void addListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.add(listener);
	}

	/**
	 * Remove the given AdvisedSupportListener from this proxy configuration.
	 * @param listener the listener to deregister
	 */
	public void removeListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.remove(listener);
	}


	/**
	 * Subclasses should call this to get a new AOP proxy. They should <b>not</b>
	 * create an AOP proxy with {@code this} as an argument.
	 */
	protected final synchronized AopProxy createAopProxy() {
		// 同步方法 -- 创建AopProxy

		// active 初始化默认是false
		if (!this.active) {
			// 调用active()进行激活，触发监听器的回调
			// 因此只要开始创建aop代理对象,都会将active设置为true,并激活
			activate();
		}
		// ❗️❗️❗️ -> 这tm就是核心啊 -- xdm
		return getAopProxyFactory().createAopProxy(this);
	}

	/**
	 * Activate this proxy configuration.
	 * @see AdvisedSupportListener#activated
	 */
	private void activate() {
		// active激活后，需要遍历 AdvisedSupportListener#activated 进行处理
		this.active = true;
		for (AdvisedSupportListener listener : this.listeners) {
			listener.activated(this);
		}
	}

	/**
	 * Propagate advice change event to all AdvisedSupportListeners.
	 * @see AdvisedSupportListener#adviceChanged
	 */
	@Override
	protected void adviceChanged() {
		// 在超类AdvisedSupport的adviceChanged()基础扩展
		super.adviceChanged();
		synchronized (this) {
			// 当已经创建过aop代理对象后,再向AdvisedSupport中添加或移除Advisor
			// 就会触发 AdvisedSupportListener的change事件哦
			if (this.active) {
				for (AdvisedSupportListener listener : this.listeners) {
					listener.adviceChanged(this);
				}
			}
		}
	}

	/**
	 * Subclasses can call this to check whether any AOP proxies have been created yet.
	 */
	protected final synchronized boolean isActive() {
		// 子类可以调用它来检查是否已经创建了任何 AOP 代理
		return this.active;
	}

}
