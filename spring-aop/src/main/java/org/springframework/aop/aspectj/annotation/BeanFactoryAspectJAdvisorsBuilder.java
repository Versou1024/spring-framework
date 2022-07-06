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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {
	// 基于BeanFactory完成AspectJ表达式切面类的构建器

	// bean工厂
	private final ListableBeanFactory beanFactory;

	// AspectJAdvisorFactory 将 AspectJ注解标注的切面类中的通知方法转换为Advisor的Factory
	private final AspectJAdvisorFactory advisorFactory;

	// 切面的BeanNames
	@Nullable
	private volatile List<String> aspectBeanNames;

	// @Aspect注解的切面类的beanName为key
	// 切面类中的使用AspectJ注解的通知方法形成的Advisor为value
	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>(); 

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		// 1. 缓存的切面类的名字
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {
				// 2. 懒加载，尝试初始
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					// 2.1 遍历BeanFactory中所有的bean
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						// 2.2 非合格bean直接跳过
						// 其子类BeanFactoryAspectJAdvisorsBuilderAdapter重写了isEligibleBean()
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// 2.3 我们必须小心，不要急于实例化bean，因为在当前情况中，它们会被Spring容器缓存，但不会被编织
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) {
							continue;
						}
						// 2.4 是否有@Aspect注解
						if (this.advisorFactory.isAspect(beanType)) {
							// 2.4.1 有@Aspect注解的bean就认为是切面,加入到aspectNames
							aspectNames.add(beanName);
							// 2.4.2 转换为AspectMetadata -> 这个不必多说，you know的
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 2.4.3 当AspectJ为单例模式 -- 一般子使用@Aspect,而不设置value属性,都默认是SINGLETON类型的
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								// 2.4.4 BeanFactoryAspectInstanceFactory 就是MetadataAwareAspectInstanceFactory,不过是结合BeanFactory来使用的哦
								MetadataAwareAspectInstanceFactory factory = new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								// 2.4.5 获取所有的增强方法 ❗️❗️❗️ 开始输出Advisor啦
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								// 2.4.6.1 如果切面类是单例的
								if (this.beanFactory.isSingleton(beanName)) {
									// 如果是单例，就存储beanName以及对应的advisor
									this.advisorsCache.put(beanName, classAdvisors);
								}
								// 2.4.6.2 如果切面类是原型的,或其他Scope的
								else {
									// 如果是原型，就存储AspectFactory，下次从factory重新获取原型的切面类就可以啦
									this.aspectFactoryCache.put(beanName, factory);
								}
								advisors.addAll(classAdvisors);
							}
							else {
								// 忽略不计~~~
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								MetadataAwareAspectInstanceFactory factory = new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								// 缓存，key->beanName，value->factory
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		// 3. 最终都没有找到切面类,啥也不说直接返回空数组吧
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		// 4. 汇总
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			// 4.1 所有单例的切面类中的Advisor集合取出来
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			// 4.2 所有原型切面类中的MetadataAwareAspectInstanceFactory取出俩
			// 调用.getAdvisors(factory)重新获取一遍即可
			else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		// 5. 返回整个项目中所有的切面类中所有通知方法转换出来的Advisor集合哦
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
