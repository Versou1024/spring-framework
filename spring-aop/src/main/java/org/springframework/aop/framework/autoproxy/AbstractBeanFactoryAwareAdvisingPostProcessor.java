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

package org.springframework.aop.framework.autoproxy;

import org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;

/**
 * Extension of {@link AbstractAutoProxyCreator} which implements {@link BeanFactoryAware},
 * adds exposure of the original target class for each proxied bean
 * ({@link AutoProxyUtils#ORIGINAL_TARGET_CLASS_ATTRIBUTE}),
 * and participates in an externally enforced target-class mode for any given bean
 * ({@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE}).
 * This post-processor is therefore aligned with {@link AbstractAutoProxyCreator}.
 *
 * @author Juergen Hoeller
 * @since 4.2.3
 * @see AutoProxyUtils#shouldProxyTargetClass
 * @see AutoProxyUtils#determineTargetClass
 */
@SuppressWarnings("serial")
public abstract class AbstractBeanFactoryAwareAdvisingPostProcessor extends AbstractAdvisingBeanPostProcessor implements BeanFactoryAware {
	// AbstractBeanFactoryAwareAdvisingPostProcessor = Abstract BeanFactoryAware Advising PostProcessor
	// 从名字可以看出，它相较于父类，就和BeanFactory有关

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	// 如果这个Bean工厂不是ConfigurableListableBeanFactory ，那就set一个null
	// 我们的`DefaultListableBeanFactory`显然就是它的子类~~~~~
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = (beanFactory instanceof ConfigurableListableBeanFactory ? (ConfigurableListableBeanFactory) beanFactory : null);
	}

	@Override
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		// prepareProxyFactory(Object bean, String beanName) 方法在超类的 postProcessAfterInitialization() 初始化的后置处理中构建代理对象时有使用到
		// 重写 prepareProxyFactory()

		
		// 1. 使用AutoProxyUtils.exposeTargetClass()将targetClass作为属性ORIGINAL_TARGET_CLASS_ATTRIBUTE中的值存入到AOP代理对象
		// 用来: 指示自动代理 bean 的原始目标类的 bean 定义属性，例如用于在基于接口的代理后面的目标类上的注解的自省。
		if (this.beanFactory != null) {
			AutoProxyUtils.exposeTargetClass(this.beanFactory, beanName, bean.getClass());
		}
		// 2. 父类的super.prepareProxyFactory(bean, beanName)重调用
		ProxyFactory proxyFactory = super.prepareProxyFactory(bean, beanName);
		// 3. 检查是否需要做Cglib代理
		if (!proxyFactory.isProxyTargetClass() && this.beanFactory != null &&
				AutoProxyUtils.shouldProxyTargetClass(this.beanFactory, beanName)) {
			proxyFactory.setProxyTargetClass(true);
		}
		return proxyFactory;
	}

	@Override
	protected boolean isEligible(Object bean, String beanName) {
		// 重写 isEligible
		// 额外扩展；!AutoProxyUtils.isOriginalInstance(beanName, bean.getClass()) 不能是原始实例，同时满足切面的要求
		return (!AutoProxyUtils.isOriginalInstance(beanName, bean.getClass()) && super.isEligible(bean, beanName));
	}

}
