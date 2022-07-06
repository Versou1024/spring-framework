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
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;

/**
 * Bean post-processor that automatically applies asynchronous invocation
 * behavior to any bean that carries the {@link Async} annotation at class or
 * method-level by adding a corresponding {@link AsyncAnnotationAdvisor} to the
 * exposed proxy (either an existing AOP proxy or a newly generated proxy that
 * implements all of the target's interfaces).
 *
 * <p>The {@link TaskExecutor} responsible for the asynchronous execution may
 * be provided as well as the annotation type that indicates a method should be
 * invoked asynchronously. If no annotation type is specified, this post-
 * processor will detect both Spring's {@link Async @Async} annotation as well
 * as the EJB 3.1 {@code javax.ejb.Asynchronous} annotation.
 *
 * <p>For methods having a {@code void} return type, any exception thrown
 * during the asynchronous method invocation cannot be accessed by the
 * caller. An {@link AsyncUncaughtExceptionHandler} can be specified to handle
 * these cases.
 *
 * <p>Note: The underlying async advisor applies before existing advisors by default,
 * in order to switch to async execution as early as possible in the invocation chain.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.0
 * @see Async
 * @see AsyncAnnotationAdvisor
 * @see #setBeforeExistingAdvisors
 * @see ScheduledAnnotationBeanPostProcessor
 */
@SuppressWarnings("serial")
public class AsyncAnnotationBeanPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor {
	// 加载到ioc容器的时机:
	// @EnableAsync的元注解 -> @Import(AsyncConfigurationSelector.class) -> AsyncConfigurationSelector -> 导入的配置类ProxyAsyncConfiguration -> 
	// 配置类下的@Bean方法导入AsyncAnnotationBeanPostProcessor
	
	// 循环依赖问题: 注意点 ---
	// @Async的代理创建使用的是AsyncAnnotationBeanPostProcessor单独的后置处理器实现的，
	// 它只在一处postProcessAfterInitialization()实现了对代理对象的创建，因此若出现它被循环依赖了，就会报错如上~~

	/**
	 * The default name of the {@link TaskExecutor} bean to pick up: "taskExecutor".
	 * <p>Note that the initial lookup happens by type; this is just the fallback
	 * in case of multiple executor beans found in the context.
	 * @since 4.2
	 * @see AnnotationAsyncExecutionInterceptor#DEFAULT_TASK_EXECUTOR_BEAN_NAME
	 */
	public static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME =
			AnnotationAsyncExecutionInterceptor.DEFAULT_TASK_EXECUTOR_BEAN_NAME;


	protected final Log logger = LogFactory.getLog(getClass());

	// 异步的执行器 -> 用户可以通过向ioc容器注入AsyncConfigurer实现类完成
	// 默认是每有一个@Async就起一个线程,没有使用线程池,那么如果很多个异步线程,就会启动多个线程导致出现问题哦
	@Nullable
	private Supplier<Executor> executor;

	// 异常处理器  -> 用户可以通过向ioc容器注入AsyncConfigurer实现类完成
	// 默认: 
	@Nullable
	private Supplier<AsyncUncaughtExceptionHandler> exceptionHandler;

	// 用户自定义需要扫描的异步注解类型  -> 用户可以通过@EnableAsync的annotation来指定
	// 默认是 @Async 
	@Nullable
	private Class<? extends Annotation> asyncAnnotationType; 



	// ❗️❗️❗️
	// 此处特别注意：这里设置为true，也就是说@Async的Advisor会放在首位已有的Advisor最前面
	public AsyncAnnotationBeanPostProcessor() {
		// 扩展原因 -- @Async是用来启动异步操作的，因此@Async的Advisor需要放在第一个位置，以便启动异步操作
		// 当然前提是被当前AsyncAnnotationBeanPostProcessor处理器处理时,bean已经是一个代理对象,本身就有几个匹配的Advisor,属于Advised才生效
		// 如果bean被当前处理器执行时,都还没代理,纯纯的小白一个,那么这个参数值也就无效,因为就这个一个异步的Advisor值
		setBeforeExistingAdvisors(true);
	}


	/**
	 * Configure this post-processor with the given executor and exception handler suppliers,
	 * applying the corresponding default if a supplier is not resolvable.
	 * @since 5.1
	 */
	public void configure(@Nullable Supplier<Executor> executor, @Nullable Supplier<AsyncUncaughtExceptionHandler> exceptionHandler) {
		// 配置 执行器、异常处理器
		this.executor = executor;
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Set the {@link Executor} to use when invoking methods asynchronously.
	 * <p>If not specified, default executor resolution will apply: searching for a
	 * unique {@link TaskExecutor} bean in the context, or for an {@link Executor}
	 * bean named "taskExecutor" otherwise. If neither of the two is resolvable,
	 * a local default executor will be created within the interceptor.
	 * @see AnnotationAsyncExecutionInterceptor#getDefaultExecutor(BeanFactory)
	 * @see #DEFAULT_TASK_EXECUTOR_BEAN_NAME
	 */
	public void setExecutor(Executor executor) {
		this.executor = SingletonSupplier.of(executor);
	}

	/**
	 * Set the {@link AsyncUncaughtExceptionHandler} to use to handle uncaught
	 * exceptions thrown by asynchronous method executions.
	 * @since 4.1
	 */
	public void setExceptionHandler(AsyncUncaughtExceptionHandler exceptionHandler) {
		this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
	}

	/**
	 * Set the 'async' annotation type to be detected at either class or method
	 * level. By default, both the {@link Async} annotation and the EJB 3.1
	 * {@code javax.ejb.Asynchronous} annotation will be detected.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a method (or all
	 * methods of a given class) should be invoked asynchronously.
	 * @param asyncAnnotationType the desired annotation type
	 */
	public void setAsyncAnnotationType(Class<? extends Annotation> asyncAnnotationType) {
		Assert.notNull(asyncAnnotationType, "'asyncAnnotationType' must not be null");
		this.asyncAnnotationType = asyncAnnotationType;
	}


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);

		// 核心：在setBeanFactory的同时，完成对advisor的设置 -> 这个Advisor就是拦截方法的执行,交给异步处理器来执行

		// 1. 传入executor、exceptionHandler的advisor
		AsyncAnnotationAdvisor advisor = new AsyncAnnotationAdvisor(this.executor, this.exceptionHandler);
		// 2. 传入asyncAnnotationType
		if (this.asyncAnnotationType != null) {
			advisor.setAsyncAnnotationType(this.asyncAnnotationType);
		}
		// 3. 传入BeanFactory
		advisor.setBeanFactory(beanFactory);
		// 4. 设置advisor
		this.advisor = advisor;
	}

}
