/*
 * Copyright 2002-2020 the original author or authors.
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

import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.lang.Nullable;

/**
 * {@link AutowireCandidateResolver} implementation to use when no annotation
 * support is available. This implementation checks the bean definition only.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 2.5
 */
public class SimpleAutowireCandidateResolver implements AutowireCandidateResolver {
	//	SimpleAutowireCandidateResolver没有任何实现，就是一个Adapter

	/**
	 * Shared instance of {@code SimpleAutowireCandidateResolver}.
	 * @since 5.2.7
	 */
	public static final SimpleAutowireCandidateResolver INSTANCE = new SimpleAutowireCandidateResolver();


	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		// 默认就是查看：BeanDefinition中是否设置autowrireCandidate为true，即允许作为自动装配的候选者
		// 否则这个Bean是无法作为候选者被依赖注入的哦
		return bdHolder.getBeanDefinition().isAutowireCandidate();
	}

	@Override
	public boolean isRequired(DependencyDescriptor descriptor) {
		return descriptor.isRequired();
	}

	@Override
	public boolean hasQualifier(DependencyDescriptor descriptor) {
		return false;
	}

	@Override
	@Nullable
	public Object getSuggestedValue(DependencyDescriptor descriptor) {
		return null;
	}

	@Override
	@Nullable
	public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
		// 默认实现就是返回null的
		return null;
	}

	/**
	 * This implementation returns {@code this} as-is.
	 * @see #INSTANCE
	 */
	@Override
	public AutowireCandidateResolver cloneIfNecessary() {
		return this;
	}

}
