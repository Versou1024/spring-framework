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

package org.springframework.scheduling.annotation;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Pointcut;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * Advisor that activates asynchronous method execution through the {@link Async}
 * annotation. This annotation can be used at the method and type level in
 * implementation classes as well as in service interfaces.
 *
 * <p>This advisor detects the EJB 3.1 {@code javax.ejb.Asynchronous}
 * annotation as well, treating it exactly like Spring's own {@code Async}.
 * Furthermore, a custom async annotation type may get specified through the
 * {@link #setAsyncAnnotationType "asyncAnnotationType"} property.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see Async
 * @see AnnotationAsyncExecutionInterceptor
 */
@SuppressWarnings("serial")
public class AsyncAnnotationAdvisor extends AbstractPointcutAdvisor implements BeanFactoryAware {
	// 典型的 AbstractPointcutAdvisor

	private Advice advice;

	private Pointcut pointcut;


	/**
	 * Create a new {@code AsyncAnnotationAdvisor} for bean-style configuration.
	 */
	public AsyncAnnotationAdvisor() {
		this((Supplier<Executor>) null, (Supplier<AsyncUncaughtExceptionHandler>) null);
	}

	/**
	 * Create a new {@code AsyncAnnotationAdvisor} for the given task executor.
	 * @param executor the task executor to use for asynchronous methods
	 * (can be {@code null} to trigger default executor resolution)
	 * @param exceptionHandler the {@link AsyncUncaughtExceptionHandler} to use to
	 * handle unexpected exception thrown by asynchronous method executions
	 * @see AnnotationAsyncExecutionInterceptor#getDefaultExecutor(BeanFactory)
	 */
	@SuppressWarnings("unchecked")
	public AsyncAnnotationAdvisor(
			@Nullable Executor executor, @Nullable AsyncUncaughtExceptionHandler exceptionHandler) {

		this(SingletonSupplier.ofNullable(executor), SingletonSupplier.ofNullable(exceptionHandler));
	}

	/**
	 * Create a new {@code AsyncAnnotationAdvisor} for the given task executor.
	 * @param executor the task executor to use for asynchronous methods
	 * (can be {@code null} to trigger default executor resolution)
	 * @param exceptionHandler the {@link AsyncUncaughtExceptionHandler} to use to
	 * handle unexpected exception thrown by asynchronous method executions
	 * @since 5.1
	 * @see AnnotationAsyncExecutionInterceptor#getDefaultExecutor(BeanFactory)
	 */
	@SuppressWarnings("unchecked")
	public AsyncAnnotationAdvisor(@Nullable Supplier<Executor> executor, @Nullable Supplier<AsyncUncaughtExceptionHandler> exceptionHandler) {
		// 提供异步执行器和异步异常处理器
		// 1. 这里List长度选择2，应为绝大部分情况下只会支持这两种@Async和@Asynchronous
		Set<Class<? extends Annotation>> asyncAnnotationTypes = new LinkedHashSet<>(2);
		asyncAnnotationTypes.add(Async.class);
		try {
			asyncAnnotationTypes.add((Class<? extends Annotation>) ClassUtils.forName("javax.ejb.Asynchronous", AsyncAnnotationAdvisor.class.getClassLoader()));
		}
		catch (ClassNotFoundException ex) {
			// If EJB 3.1 API not present, simply ignore.
		}
		// 2. 构建advice增强方法、构建pointcut连接点
		// 两个核心方法
		this.advice = buildAdvice(executor, exceptionHandler);
		this.pointcut = buildPointcut(asyncAnnotationTypes);
	}


	/**
	 * Set the 'async' annotation type.
	 * <p>The default async annotation type is the {@link Async} annotation, as well
	 * as the EJB 3.1 {@code javax.ejb.Asynchronous} annotation (if present).
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a method is to
	 * be executed asynchronously.
	 * @param asyncAnnotationType the desired annotation type
	 */
	public void setAsyncAnnotationType(Class<? extends Annotation> asyncAnnotationType) {
		// 这里注意：如果你自己指定了注解类型。那么将不再扫描其余两个默认的注解，因此pointcut也就需要重新构建了
		Assert.notNull(asyncAnnotationType, "'asyncAnnotationType' must not be null");
		Set<Class<? extends Annotation>> asyncAnnotationTypes = new HashSet<>();
		// 用户扩展的注解类型，比如用户想要使用@Assyynnc作为异步启动的注解
		// 那么就会替换调@Async注解的使用哦
		asyncAnnotationTypes.add(asyncAnnotationType);
		// 导致 pointcut 被重新构建
		this.pointcut = buildPointcut(asyncAnnotationTypes);
	}

	/**
	 * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		// 有必要的话，传递到advice中
		// 如果这个advice也实现了BeanFactoryAware，那就也把BeanFactory放进去
		if (this.advice instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.advice).setBeanFactory(beanFactory);
		}
	}


	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}


	protected Advice buildAdvice(@Nullable Supplier<Executor> executor, @Nullable Supplier<AsyncUncaughtExceptionHandler> exceptionHandler) {
		// ❗️❗
		// 核心：构建增强通知方法advice -- AnnotationAsyncExecutionInterceptor -- 这里面就完成了异步的操作
		AnnotationAsyncExecutionInterceptor interceptor = new AnnotationAsyncExecutionInterceptor(null);
		// 可以向其中设置默认的executor
		interceptor.configure(executor, exceptionHandler);
		return interceptor;
	}

	/**
	 * Calculate a pointcut for the given async annotation types, if any.
	 * @param asyncAnnotationTypes the async annotation types to introspect
	 * @return the applicable Pointcut object, or {@code null} if none
	 */
	protected Pointcut buildPointcut(Set<Class<? extends Annotation>> asyncAnnotationTypes) {
		// ❗️❗️
		// 核心：连接点 -- 完成匹配
		// 采用一个组合切面：ComposablePointcut （因为可能需要支持多个注解嘛）
		ComposablePointcut result = null;
		for (Class<? extends Annotation> asyncAnnotationType : asyncAnnotationTypes) {
			// 这里为何new出来两个AnnotationMatchingPointcut？？？？？？
			// 第一个：类匹配（只需要类上面有这个注解，所有的方法都匹配）this.methodMatcher = MethodMatcher.TRUE;
			// 第二个：方法匹配。所有的类都可议。但是只有方法上有这个注解才会匹配上
			
			// 1. cpc 是 MethodMatcher.TRUE, ClassFilter根据是否存在asyncAnnotationType注解而返回true/false
			Pointcut cpc = new AnnotationMatchingPointcut(asyncAnnotationType, true);
			// 2. mpc 是 MethodMatcher匹配方法上是否有asyncAnnotationType注解返回true/false, ClassFilter根据是否存在asyncAnnotationType注解而返回true/false
			Pointcut mpc = new AnnotationMatchingPointcut(null, asyncAnnotationType, true);
			if (result == null) {
				result = new ComposablePointcut(cpc);
			}
			else {
				// 3.1 将cpc和mpc进行union,也就是说只要上面 cpc 或者 mpc 有一个满足,那么当前的Advice就可以使用在对应得类上
				result.union(cpc);
			}
			// 3.2 将cpc和mpc进行union,也就是说只要上面 cpc 或者 mpc 有一个满足,那么当前的Advice就可以使用在对应得类上
			result = result.union(mpc);
		}
		// 4. 到这: result一般不会为null
		return (result != null ? result : Pointcut.TRUE);
	}

}
