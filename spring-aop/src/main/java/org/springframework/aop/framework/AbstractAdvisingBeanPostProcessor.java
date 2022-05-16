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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;

/**
 * Base class for {@link BeanPostProcessor} implementations that apply a
 * Spring AOP {@link Advisor} to specific beans.
 *
 * @author Juergen Hoeller
 * @since 3.2
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisingBeanPostProcessor extends ProxyProcessorSupport implements BeanPostProcessor {
	// 在 ProxyProcessorSupport 基础上，接受一个Spring aop

	@Nullable
	protected Advisor advisor;  // 留给子类去设置，然后加载到bean中做代理

	protected boolean beforeExistingAdvisors = false;

	// 缓存合格的Bean们
	private final Map<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether this post-processor's advisor is supposed to apply before
	 * existing advisors when encountering a pre-advised object.
	 * <p>Default is "false", applying the advisor after existing advisors, i.e.
	 * as close as possible to the target method. Switch this to "true" in order
	 * for this post-processor's advisor to wrap existing advisors as well.
	 * <p>Note: Check the concrete post-processor's javadoc whether it possibly
	 * changes this flag by default, depending on the nature of its advisor.
	 */
	public void setBeforeExistingAdvisors(boolean beforeExistingAdvisors) {
		// 当遇到一个pre-object的时候，是否把该processor所持有得advisor放在现有的增强器们之前执行
		// 默认是false，会放在最后一个位置上的
		this.beforeExistingAdvisors = beforeExistingAdvisors;
	}


	// 不处理
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	// Bean已经实例化、初始化完成之后执行。
	// 核心模板 -- 关注：advisor的来源、advisor的存放位置、bean是否满足advisor
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		// 忽略AopInfrastructureBean的Bean，并且如果没有advisor也会忽略不处理~~~~~
		// 需要知道：advisor是当前类留给子类实现的，因此该类的子类应该提供advisor，然后再后续的proxyFactory.addAdvisor添加进去，然后生成一个代理对象Proxy
		if (this.advisor == null || bean instanceof AopInfrastructureBean) {
			// Ignore AOP infrastructure such as scoped proxies.
			return bean;
		}

		// 如果这个Bean已经被代理过了（比如已经被AOP切中了），那本处就无需再重复创建代理了嘛
		// 直接向里面添加advisor就成了
		// 虽然项目中使用@Aspect的类没有直接继承Advised，但是为其生成代理类的时候，会为其实现两个标记性接口：Advised、SpringProxy
		if (bean instanceof Advised) {
			Advised advised = (Advised) bean;
			// 注意此advised不能是已经被冻结了的。且源对象必须是Eligible合格的 --- 合格的标准：满足子类的advisor的切面中的pointcut的要求，同时不是一个原始类
			// 因为冻结了配置就无须重新更新advisors
			if (!advised.isFrozen() && isEligible(AopUtils.getTargetClass(bean))) {
				// Add our local Advisor to the existing proxy's Advisor chain...
				// 把自己持有的局部advisor放在首位（如果beforeExistingAdvisors=true）
				if (this.beforeExistingAdvisors) {
					// 例如：@Async导入的异步interceptor就需要放在第一个位置，提前做异步处理
					advised.addAdvisor(0, this.advisor);
				}
				// 否则就是尾部位置
				else {
					advised.addAdvisor(this.advisor);
				}
				// 最终直接返回即可，因为已经没有必要再创建一次代理对象了
				return bean;
			}
		}
		// 如果这个Bean事合格的（此方法下面有解释）   这个时候是没有被代理过的
		if (isEligible(bean, beanName)) {
			// 以当前的配置，创建一个ProxyFactory
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName);
			// 如果不是使用CGLIB常见代理，那就去分析出它所实现的接口们  然后放进ProxyFactory 里去
			if (!proxyFactory.isProxyTargetClass()) {
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			// 切面就是当前持有得advisor
			proxyFactory.addAdvisor(this.advisor);
			// 留给子类，自己还可以对proxyFactory进行自定义~~~~~

			customizeProxyFactory(proxyFactory);
			// 最终返回这个代理对象~~~~~
			return proxyFactory.getProxy(getProxyClassLoader());
		}

		// No proxy needed.
		return bean;
	}

	/**
	 * Check whether the given bean is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Delegates to {@link #isEligible(Class)} for target class checking.
	 * Can be overridden e.g. to specifically exclude certain beans by name.
	 * <p>Note: Only called for regular bean instances but not for existing
	 * proxy instances which implement {@link Advised} and allow for adding
	 * the local {@link Advisor} to the existing proxy's {@link Advisor} chain.
	 * For the latter, {@link #isEligible(Class)} is being called directly,
	 * with the actual target class behind the existing proxy (as determined
	 * by {@link AopUtils#getTargetClass(Object)}).
	 * @param bean the bean instance
	 * @param beanName the name of the bean
	 * @see #isEligible(Class)
	 */
	protected boolean isEligible(Object bean, String beanName) {
		return isEligible(bean.getClass());
	}

	/**
	 * Check whether the given class is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Implements caching of {@code canApply} results per bean target class.
	 * @param targetClass the class to check against
	 * @see AopUtils#canApply(Advisor, Class)
	 */
	protected boolean isEligible(Class<?> targetClass) {
		// 是否合格：主要依据就是判断targetClass是否满足advisor切面类的定义

		Boolean eligible = this.eligibleBeans.get(targetClass);
		// 缓存命中
		if (eligible != null) {
			// eligible 为true，就表示合格的bean
			// eligible 为false，就表示不合格的bean -- 既不符合切面的定义
			return eligible;
		}
		// 如果没有切面（就相当于没有给配置增强器，那铁定是不合格的）
		if (this.advisor == null) {
			return false;
		}
		// 检查targetClass是否被advisor接受
		eligible = AopUtils.canApply(this.advisor, targetClass);
		// 存入缓存
		this.eligibleBeans.put(targetClass, eligible);
		return eligible;
	}

	/**
	 * Prepare a {@link ProxyFactory} for the given bean.
	 * <p>Subclasses may customize the handling of the target instance and in
	 * particular the exposure of the target class. The default introspection
	 * of interfaces for non-target-class proxies and the configured advisor
	 * will be applied afterwards; {@link #customizeProxyFactory} allows for
	 * late customizations of those parts right before proxy creation.
	 * @param bean the bean instance to create a proxy for
	 * @param beanName the corresponding bean name
	 * @return the ProxyFactory, initialized with this processor's
	 * {@link ProxyConfig} settings and the specified bean
	 * @since 4.2.3
	 * @see #customizeProxyFactory
	 */
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		// 子类可以复写。比如`AbstractBeanFactoryAwareAdvisingPostProcessor`就复写了这个方法~~~
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.copyFrom(this);
		proxyFactory.setTarget(bean);
		return proxyFactory;
	}

	/**
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory the ProxyFactory that is already configured with
	 * target, advisor and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 * @since 4.2.3
	 * @see #prepareProxyFactory
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}

}
