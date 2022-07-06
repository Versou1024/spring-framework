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

package org.springframework.dao.support;

import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * AOP Alliance MethodInterceptor that provides persistence exception translation
 * based on a given PersistenceExceptionTranslator.
 *
 * <p>Delegates to the given {@link PersistenceExceptionTranslator} to translate
 * a RuntimeException thrown into Spring's DataAccessException hierarchy
 * (if appropriate). If the RuntimeException in question is declared on the
 * target method, it is always propagated as-is (with no translation applied).
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see PersistenceExceptionTranslator
 */
public class PersistenceExceptionTranslationInterceptor implements MethodInterceptor, BeanFactoryAware, InitializingBean {
	// 委托给给定的PersistenceExceptionTranslator以将抛出的 RuntimeException 转换为 Spring 的 DataAccessException 层次结构（如果适用）。
	// 如果在目标方法上声明了有问题的 RuntimeException，它总是按原样传播（不应用翻译）。

	@Nullable
	private volatile PersistenceExceptionTranslator persistenceExceptionTranslator;

	private boolean alwaysTranslate = false;

	@Nullable
	private ListableBeanFactory beanFactory;


	/**
	 * Create a new PersistenceExceptionTranslationInterceptor.
	 * Needs to be configured with a PersistenceExceptionTranslator afterwards.
	 * @see #setPersistenceExceptionTranslator
	 */
	public PersistenceExceptionTranslationInterceptor() {
	}

	/**
	 * Create a new PersistenceExceptionTranslationInterceptor
	 * for the given PersistenceExceptionTranslator.
	 * @param pet the PersistenceExceptionTranslator to use
	 */
	public PersistenceExceptionTranslationInterceptor(PersistenceExceptionTranslator pet) {
		Assert.notNull(pet, "PersistenceExceptionTranslator must not be null");
		this.persistenceExceptionTranslator = pet;
	}

	/**
	 * Create a new PersistenceExceptionTranslationInterceptor, autodetecting
	 * PersistenceExceptionTranslators in the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to obtaining all
	 * PersistenceExceptionTranslators from
	 */
	public PersistenceExceptionTranslationInterceptor(ListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * Specify the PersistenceExceptionTranslator to use.
	 * <p>Default is to autodetect all PersistenceExceptionTranslators
	 * in the containing BeanFactory, using them in a chain.
	 * @see #detectPersistenceExceptionTranslators
	 */
	public void setPersistenceExceptionTranslator(PersistenceExceptionTranslator pet) {
		this.persistenceExceptionTranslator = pet;
	}

	/**
	 * Specify whether to always translate the exception ("true"), or whether throw the
	 * raw exception when declared, i.e. when the originating method signature's exception
	 * declarations allow for the raw exception to be thrown ("false").
	 * <p>Default is "false". Switch this flag to "true" in order to always translate
	 * applicable exceptions, independent from the originating method signature.
	 * <p>Note that the originating method does not have to declare the specific exception.
	 * Any base class will do as well, even {@code throws Exception}: As long as the
	 * originating method does explicitly declare compatible exceptions, the raw exception
	 * will be rethrown. If you would like to avoid throwing raw exceptions in any case,
	 * switch this flag to "true".
	 */
	public void setAlwaysTranslate(boolean alwaysTranslate) {
		this.alwaysTranslate = alwaysTranslate;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		if (this.persistenceExceptionTranslator == null) {
			// No explicit exception translator specified - perform autodetection.
			if (!(beanFactory instanceof ListableBeanFactory)) {
				throw new IllegalArgumentException(
						"Cannot use PersistenceExceptionTranslator autodetection without ListableBeanFactory");
			}
			this.beanFactory = (ListableBeanFactory) beanFactory;
		}
	}

	@Override
	public void afterPropertiesSet() {
		if (this.persistenceExceptionTranslator == null && this.beanFactory == null) {
			throw new IllegalArgumentException("Property 'persistenceExceptionTranslator' is required");
		}
	}


	@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			return mi.proceed();
		}
		catch (RuntimeException ex) {
			// 1.如果异常的类型在方法的 throws 子句中，则让它抛出 raw。
			//  ReflectionUtils.declaresException()确定给定方法是否显式声明给定异常或其超类之一，这意味着该类型的异常可以在反射调用中按原样传播
			// 即方法上的声明的异常可以按照原样抛出
			if (!this.alwaysTranslate && ReflectionUtils.declaresException(mi.getMethod(), ex.getClass())) {
				throw ex;
			}
			// 2. 修改异常信息
			else {
				PersistenceExceptionTranslator translator = this.persistenceExceptionTranslator;
				if (translator == null) {
					Assert.state(this.beanFactory != null,
							"Cannot use PersistenceExceptionTranslator autodetection without ListableBeanFactory");
					// 2.1 没有直接set PersistenceExceptionTranslator
					// 从BeanFactory中获取所有的翻译器,并组合为链式的ChainedPersistenceExceptionTranslator后返回
					translator = detectPersistenceExceptionTranslators(this.beanFactory);
					this.persistenceExceptionTranslator = translator;
				}
				// 2.2 尝试转换
				throw DataAccessUtils.translateIfNecessary(ex, translator);
			}
		}
	}

	/**
	 * Detect all PersistenceExceptionTranslators in the given BeanFactory.
	 * @param bf the ListableBeanFactory to obtain PersistenceExceptionTranslators from
	 * @return a chained PersistenceExceptionTranslator, combining all
	 * PersistenceExceptionTranslators found in the given bean factory
	 * @see ChainedPersistenceExceptionTranslator
	 */
	protected PersistenceExceptionTranslator detectPersistenceExceptionTranslators(ListableBeanFactory bf) {
		
		// 1. 检测给定 BeanFactory 中的所有 PersistenceExceptionTranslators。
		// 注意两个形参: 
		// includeNonSingletons – 是否也包括原型和其他Scppe作用域的bean,还是仅仅仅包括单例（也适用于 FactoryBeans）
		// allowEagerInit – 是否初始化由 FactoryBeans（或通过带有“factory-bean”引用的工厂方法）创建的惰性初始化单例和对象以进行类型检查。
		// 请注意，FactoryBeans 需要立即初始化以确定它们的类型：因此请注意，为此标志传递“true”将初始化 FactoryBeans 和“factory-bean”引用。
		Map<String, PersistenceExceptionTranslator> pets = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				bf, PersistenceExceptionTranslator.class, false, false);
		
		// 2. 将所有的翻译器组合到链式的ChainedPersistenceExceptionTranslator上
		ChainedPersistenceExceptionTranslator cpet = new ChainedPersistenceExceptionTranslator();
		for (PersistenceExceptionTranslator pet : pets.values()) {
			cpet.addDelegate(pet);
		}
		return cpet;
	}

}
