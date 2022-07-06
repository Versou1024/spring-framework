/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.dao.annotation;

import java.lang.annotation.Annotation;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.dao.support.PersistenceExceptionTranslationInterceptor;
import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * Spring AOP exception translation aspect for use at Repository or DAO layer level.
 * Translates native persistence exceptions into Spring's DataAccessException hierarchy,
 * based on a given PersistenceExceptionTranslator.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.dao.DataAccessException
 * @see org.springframework.dao.support.PersistenceExceptionTranslator
 */
@SuppressWarnings("serial")
public class PersistenceExceptionTranslationAdvisor extends AbstractPointcutAdvisor {
	// 用于存储库或DAO层级别的 Spring AOP -- 主要处理:异常转换方面
	// 即将持久层框架给定的 PersistenceExceptionTranslator 转换为 Spring 的 DataAccessException 层次结构
	// note: 当然PersistenceExceptionTranslationAdvisor并不是这样使用就完啦,还需要搭配其他组件一般是BeanPostProcessor以此在Bean生成的过程中,构建出代理类来 -- 替换调原始对象
	// 比如这里搭配 PersistenceExceptionTranslationPostProcessor

	// 拦截器
	private final PersistenceExceptionTranslationInterceptor advice;

	// 切点
	private final AnnotationMatchingPointcut pointcut;


	/**
	 * Create a new PersistenceExceptionTranslationAdvisor.
	 * @param persistenceExceptionTranslator the PersistenceExceptionTranslator to use
	 * @param repositoryAnnotationType the annotation type to check for
	 */
	public PersistenceExceptionTranslationAdvisor(
			PersistenceExceptionTranslator persistenceExceptionTranslator,
			Class<? extends Annotation> repositoryAnnotationType) {
		// repositoryAnnotationType 是需要去检查aop增强的注解 -- 会检查继承习题上的哦
		this.advice = new PersistenceExceptionTranslationInterceptor(persistenceExceptionTranslator);
		// note: 这里构建的AnnotationMatchingPointcut,方法过滤器为MethodMatcher.TRUE,类过滤器在继承体系上找指定的注解哦
		this.pointcut = new AnnotationMatchingPointcut(repositoryAnnotationType, true);
	}

	/**
	 * Create a new PersistenceExceptionTranslationAdvisor.
	 * @param beanFactory the ListableBeanFactory to obtaining all
	 * PersistenceExceptionTranslators from
	 * @param repositoryAnnotationType the annotation type to check for
	 */
	PersistenceExceptionTranslationAdvisor(
			ListableBeanFactory beanFactory, Class<? extends Annotation> repositoryAnnotationType) {

		this.advice = new PersistenceExceptionTranslationInterceptor(beanFactory);
		this.pointcut = new AnnotationMatchingPointcut(repositoryAnnotationType, true);
	}


	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

}
