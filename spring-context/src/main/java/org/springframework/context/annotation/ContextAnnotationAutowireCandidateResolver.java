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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Complete implementation of the
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver} strategy
 * interface, providing support for qualifier annotations as well as for lazy resolution
 * driven by the {@link Lazy} annotation in the {@code context.annotation} package.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
public class ContextAnnotationAutowireCandidateResolver extends QualifierAnnotationAutowireCandidateResolver {
	// org.springframework.beans.factory.support.AutowireCandidateResolver策略接口的完整实现，
	// 提供对限定符注解的支持以及由context.annotation包中的Lazy注解驱动的惰性解析。

	// 主要就是对@Lazy做出解析反应

	@Override
	@Nullable
	public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
		// 如果 注入依赖 上的@Lazy注解
		// 就执行 buildLazyResolutionProxy(descriptor, beanName)
		// 否则就是返回的null哦
		return (isLazy(descriptor) ? buildLazyResolutionProxy(descriptor, beanName) : null);
	}

	protected boolean isLazy(DependencyDescriptor descriptor) {

		// 1. 有@Lazy注解,且value值为true,就返回true
		// 适用于 注入依赖 是字段的
		for (Annotation ann : descriptor.getAnnotations()) {
			Lazy lazy = AnnotationUtils.getAnnotation(ann, Lazy.class);
			if (lazy != null && lazy.value()) {
				return true;
			}
		}
		// 2. 参见形参上私有@Lazy注解,且value为true,就返回true
		// 适用于 注入依赖 是方法/构造器级别的
		MethodParameter methodParam = descriptor.getMethodParameter();
		if (methodParam != null) {
			Method method = methodParam.getMethod();
			if (method == null || void.class == method.getReturnType()) {
				Lazy lazy = AnnotationUtils.getAnnotation(methodParam.getAnnotatedElement(), Lazy.class);
				if (lazy != null && lazy.value()) {
					return true;
				}
			}
		}
		return false;
	}

	protected Object buildLazyResolutionProxy(final DependencyDescriptor descriptor, final @Nullable String beanName) {
		// 构建惰性解析代理

		// 1. BeanFactory
		BeanFactory beanFactory = getBeanFactory();
		Assert.state(beanFactory instanceof DefaultListableBeanFactory,
				"BeanFactory needs to be a DefaultListableBeanFactory");
		final DefaultListableBeanFactory dlbf = (DefaultListableBeanFactory) beanFactory;

		// 2. 目标对象 -- target目标类，用于对外统一被代理的目标对象
		// 主要写入 targetClass\target
		TargetSource ts = new TargetSource() {
			@Override
			public Class<?> getTargetClass() {
				return descriptor.getDependencyType();
			}
			@Override
			public boolean isStatic() {
				return false;
			}
			// getTarget() 方法不会被直接调用的哦
			@Override
			public Object getTarget() {
				Set<String> autowiredBeanNames = (beanName != null ? new LinkedHashSet<>(1) : null);
				Object target = dlbf.doResolveDependency(descriptor, beanName, autowiredBeanNames, null);
				// 解析失败,如果是@Autowrite的map/list/array就给一个空的集合
				// 而其余直接注入的一个bean,不存在,就爆出异常
				if (target == null) {
					Class<?> type = getTargetClass();
					if (Map.class == type) {
						return Collections.emptyMap();
					}
					else if (List.class == type) {
						return Collections.emptyList();
					}
					else if (Set.class == type || Collection.class == type) {
						return Collections.emptySet();
					}
					throw new NoSuchBeanDefinitionException(descriptor.getResolvableType(),
							"Optional dependency not present for lazy injection point");
				}
				if (autowiredBeanNames != null) {
					// 检查需要注入的依赖,是否包含在ioc容器中,有的话,就需要注册 autowiredBeanName 和 目标 beanName 的依赖关系
					for (String autowiredBeanName : autowiredBeanNames) {
						if (dlbf.containsBean(autowiredBeanName)) {
							dlbf.registerDependentBean(autowiredBeanName, beanName);
						}
					}
				}
				// 返回目标对象
				return target;
			}
			@Override
			public void releaseTarget(Object target) {
			}
		};

		// 3. proxyFactory
		ProxyFactory pf = new ProxyFactory();
		// 4. 设置 targetSource
		pf.setTargetSource(ts);
		Class<?> dependencyType = descriptor.getDependencyType();
		if (dependencyType.isInterface()) {
			pf.addInterface(dependencyType);
		}
		// 5. 获取代理对象
		return pf.getProxy(dlbf.getBeanClassLoader());

		// 因此@Lazy标注的注入,使用代理对象返回的
		// 因此用户使用@Lazy标注的属性的方法时,会触发getTarget()动作->然后才会触发去IOC容器解析这个依赖的动作->然后从ioc容器中拿到这个注入的bean->执行相应的方法
	}

}
