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
	// ProxyProcessorSupport有两个子类
	// 一个是: AbstractAutoProxy -> 
	// 		其整个体系主要是检查到切面类,将所有的切面类中的所有通知方法转换为Advisor集合
	// 		然后在每个Bean实例化之后,初始化之前,检查是否被项目中部分Advisor拦截器匹配到,如果匹配到,就需要自动为其创建代理对象并在初始化的前置操作中返回
	// 
	// 一个是: AbstractAdvisingBeanPostProcessor ->
	// 		其整个体系主要是为了关照特殊的案例,可以去接受一个Spring AOP的Advisor对象 
	
	// AbstractAdvisingBeanPostProcessor = Abstract Advising BeanPostProcessor
	// 首先这是一个BeanPostProcessor,然后是抽象的,主要能够在BeanPostProcessor的后置处理中完成对处理Advising,
	
	
	// 在 ProxyProcessorSupport 基础上，接受一个Spring aop

	// 留给子类去设置，然后加载到bean中做代理
	@Nullable
	protected Advisor advisor;

	// 当遇到一个pre-object的时候，是否把该processor所持有得advisor放在现有的增强器们之前执行
	// 默认是false，会放在最后一个位置上的
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
		// 1. 忽略AopInfrastructureBean的Bean，并且如果没有advisor也会忽略不处理~~~~~
		if (this.advisor == null || bean instanceof AopInfrastructureBean) {
			return bean;
		}

		// 2. note: 创建代理对象时会去实现两个接口：Advised、SpringProxy
		// 因此如果bean已经被代理过，那本处就无需再重复创建代理了嘛,直接向里面添加advisor就成了
		if (bean instanceof Advised) {
			// 2.1 强转
			Advised advised = (Advised) bean;
			// 注意此advised不能是已经被冻结了的。且源对象必须是Eligible合格的 
			// 合格的标准：
			// 		a：如果没有设置advisor,永远返回false表示不合格
			// 		b：检查targetClass是否被可以被设置的advisor的ClassFilter/MethodMatcher接受
			if (!advised.isFrozen() && isEligible(AopUtils.getTargetClass(bean))) {
				// 2.2 把自己持有的局部advisor放在首位（如果beforeExistingAdvisors=true）
				if (this.beforeExistingAdvisors) {
					// 例如：@Async导入的异步interceptor就需要放在第一个位置，提前做异步处理
					advised.addAdvisor(0, this.advisor);
				}
				// 2.3 否则就是尾部位置
				else {
					advised.addAdvisor(this.advisor);
				}
				// 2.4 最终直接返回即可，因为已经没有必要再创建一次代理对象了
				return bean;
			}
		}
		
		// 3. 如果bean不是Advised被代理过的,或者其配置已经冻结或者不满足设置的Advisor的类过滤和方法匹配,就执行到这里
		// 和上面的isEligible()方法一样,主要要求: 检查targetClass是否被可以被设置的advisor的ClassFilter/MethodMatcher接受
		if (isEligible(bean, beanName)) {
			// 3.1 以当前的配置，创建一个ProxyFactory --> ProxyFactory用来手动创建Aop代理对象
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName); // 子类可复写该方法
			// 3.2 如果不是使用CGLIB常见代理，那就去分析出它所实现的接口们,然后放进ProxyFactory 里去
			if (!proxyFactory.isProxyTargetClass()) {
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			// 3.3 将advisor设置进去吧
			proxyFactory.addAdvisor(this.advisor);
			// 3.4 留给子类，自己还可以对proxyFactory进行自定义~~~~~
			customizeProxyFactory(proxyFactory);
			// 3.5 最终返回这个代理对象~~~~~
			return proxyFactory.getProxy(getProxyClassLoader());
		}

		// 4. 无须代理
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
		// 主要两点:
		// a：如果没有设置advisor,永远返回false
		// b：检查targetClass是否被可以被设置的advisor的ClassFilter/MethodMatcher接受

		// 1. 缓存命中
		Boolean eligible = this.eligibleBeans.get(targetClass);
		if (eligible != null) {
			return eligible;
		}
		// 2. 缓存未命中[如果没有设置advisor,那么也是将是无效的]
		if (this.advisor == null) {
			return false;
		}
		// 3. 检查targetClass是否被advisor的ClassFilter/MethodMatcher接受
		eligible = AopUtils.canApply(this.advisor, targetClass);
		// 4. 存入缓存结果
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
		// note: 子类可以复写。比如`AbstractBeanFactoryAwareAdvisingPostProcessor`就复写了这个方法~~~
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
