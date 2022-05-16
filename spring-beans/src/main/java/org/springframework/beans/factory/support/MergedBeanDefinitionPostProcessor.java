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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Post-processor callback interface for <i>merged</i> bean definitions at runtime.
 * {@link BeanPostProcessor} implementations may implement this sub-interface in order
 * to post-process the merged bean definition (a processed copy of the original bean
 * definition) that the Spring {@code BeanFactory} uses to create a bean instance.
 *
 * <p>The {@link #postProcessMergedBeanDefinition} method may for example introspect
 * the bean definition in order to prepare some cached metadata before post-processing
 * actual instances of a bean. It is also allowed to modify the bean definition but
 * <i>only</i> for definition properties which are actually intended for concurrent
 * modification. Essentially, this only applies to operations defined on the
 * {@link RootBeanDefinition} itself but not to the properties of its base classes.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getMergedBeanDefinition
 */
public interface MergedBeanDefinitionPostProcessor extends BeanPostProcessor {
	/*
	运行时用 merge bean definitions 的后置处理器回调接口。

	BeanPostProcessor 实现可以实现这个子接口，以便对Spring BeanFactory用于创建bean实例的合并bean定义（原始bean定义的已处理副本）进行后处理。
	例如：

	postProcessMergedBeanDefinition
		可以内省bean定义，以便在对bean的实际实例进行后处理之前准备一些缓存的元数据。
		执行时机：创建Bean的过程中，当Bean已经被实例化之后，立即就会处理 MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
		因此它的执行实际：是在实例化bean之后，属性填充、初始化之前，也就是在
			InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation、postProcessProperties		之前
			InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation 之后
			BeanPostProcessor的初始化前后处理器		之前
	它还可以修改bean定义，但仅限于实际用于并发修改的定义属性。
	本质上，这只适用于在RootBeanDefinition本身上定义的操作，而不适用于其基类的属性。
	 */

	/**
	 * Post-process the given merged bean definition for the specified bean.
	 * @param beanDefinition the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see AbstractAutowireCapableBeanFactory#applyMergedBeanDefinitionPostProcessors
	 */
	void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName);

	/**
	 * A notification that the bean definition for the specified name has been reset,
	 * and that this post-processor should clear any metadata for the affected bean.
	 * <p>The default implementation is empty.
	 * @param beanName the name of the bean
	 * @since 5.1
	 * @see DefaultListableBeanFactory#resetBeanDefinition
	 */
	default void resetBeanDefinition(String beanName) {
	}

}
