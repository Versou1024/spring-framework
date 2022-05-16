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

package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.function.SingletonSupplier;

/**
 * Base class for asynchronous method execution aspects, such as
 * {@code org.springframework.scheduling.annotation.AnnotationAsyncExecutionInterceptor}
 * or {@code org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect}.
 *
 * <p>Provides support for <i>executor qualification</i> on a method-by-method basis.
 * {@code AsyncExecutionAspectSupport} objects must be constructed with a default {@code
 * Executor}, but each individual method may further qualify a specific {@code Executor}
 * bean to be used when executing it, e.g. through an annotation attribute.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.1.2
 */
public abstract class AsyncExecutionAspectSupport implements BeanFactoryAware {
	/*
	 * 异步执行器的切面支持 -- 主要提供给想要使用切面完成异步操作的类继承使用
	 * 因此主要和 执行器、BeanFactory、异常处理器 有关
	 *
	 * 核心方法：
	 * determineAsyncExecutor -- 为异步切面方法查找对应的执行器
	 * doSubmit -- 提交task
	 * handleError -- 异常处理
	 */

	/**
	 * The default name of the {@link TaskExecutor} bean to pick up: "taskExecutor".
	 * <p>Note that the initial lookup happens by type; this is just the fallback
	 * in case of multiple executor beans found in the context.
	 * @since 4.2.6
	 */
	public static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME = "taskExecutor";
	// 这是备选的。如果找到多个类型为TaskExecutor的Bean，才会备选的再用这个名称去找的~~~


	protected final Log logger = LogFactory.getLog(getClass());

	// 缓存~~~AsyncTaskExecutor是TaskExecutor的子接口
	// 从这可以看出：不同的方法，对应的异步执行器还不一样咯~~~~~~
	private final Map<Method, AsyncTaskExecutor> executors = new ConcurrentHashMap<>(16);

	// 默认的线程执行器
	private SingletonSupplier<Executor> defaultExecutor;

	// 异步异常处理器
	private SingletonSupplier<AsyncUncaughtExceptionHandler> exceptionHandler;

	@Nullable
	private BeanFactory beanFactory;


	/**
	 * Create a new instance with a default {@link AsyncUncaughtExceptionHandler}.
	 * @param defaultExecutor the {@code Executor} (typically a Spring {@code AsyncTaskExecutor}
	 * or {@link java.util.concurrent.ExecutorService}) to delegate to, unless a more specific
	 * executor has been requested via a qualifier on the async method, in which case the
	 * executor will be looked up at invocation time against the enclosing bean factory
	 */
	public AsyncExecutionAspectSupport(@Nullable Executor defaultExecutor) {
		this.defaultExecutor = new SingletonSupplier<>(defaultExecutor, () -> getDefaultExecutor(this.beanFactory));
		this.exceptionHandler = SingletonSupplier.of(SimpleAsyncUncaughtExceptionHandler::new);
	}

	/**
	 * Create a new {@link AsyncExecutionAspectSupport} with the given exception handler.
	 * @param defaultExecutor the {@code Executor} (typically a Spring {@code AsyncTaskExecutor}
	 * or {@link java.util.concurrent.ExecutorService}) to delegate to, unless a more specific
	 * executor has been requested via a qualifier on the async method, in which case the
	 * executor will be looked up at invocation time against the enclosing bean factory
	 * @param exceptionHandler the {@link AsyncUncaughtExceptionHandler} to use
	 */
	public AsyncExecutionAspectSupport(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
		this.defaultExecutor = new SingletonSupplier<>(defaultExecutor, () -> getDefaultExecutor(this.beanFactory));
		this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
	}


	/**
	 * Configure this aspect with the given executor and exception handler suppliers,
	 * applying the corresponding default if a supplier is not resolvable.
	 * @since 5.1
	 */
	public void configure(@Nullable Supplier<Executor> defaultExecutor,
			@Nullable Supplier<AsyncUncaughtExceptionHandler> exceptionHandler) {
		// 用户可以直接配置executor、异常处理器Exceptionhandler
		// 注意：如果获取不到，那么就会调动getDefaultExecutor(this.beanFactory)
		this.defaultExecutor = new SingletonSupplier<>(defaultExecutor, () -> getDefaultExecutor(this.beanFactory));
		this.exceptionHandler = new SingletonSupplier<>(exceptionHandler, SimpleAsyncUncaughtExceptionHandler::new);
	}

	/**
	 * Supply the executor to be used when executing async methods.
	 * @param defaultExecutor the {@code Executor} (typically a Spring {@code AsyncTaskExecutor}
	 * or {@link java.util.concurrent.ExecutorService}) to delegate to, unless a more specific
	 * executor has been requested via a qualifier on the async method, in which case the
	 * executor will be looked up at invocation time against the enclosing bean factory
	 * @see #getExecutorQualifier(Method)
	 * @see #setBeanFactory(BeanFactory)
	 * @see #getDefaultExecutor(BeanFactory)
	 */
	public void setExecutor(Executor defaultExecutor) {
		this.defaultExecutor = SingletonSupplier.of(defaultExecutor);
	}

	/**
	 * Supply the {@link AsyncUncaughtExceptionHandler} to use to handle exceptions
	 * thrown by invoking asynchronous methods with a {@code void} return type.
	 */
	public void setExceptionHandler(AsyncUncaughtExceptionHandler exceptionHandler) {
		this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
	}

	/**
	 * Set the {@link BeanFactory} to be used when looking up executors by qualifier
	 * or when relying on the default executor lookup algorithm.
	 * @see #findQualifiedExecutor(BeanFactory, String)
	 * @see #getDefaultExecutor(BeanFactory)
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/**
	 * Determine the specific executor to use when executing the given method.
	 * Should preferably return an {@link AsyncListenableTaskExecutor} implementation.
	 * @return the executor to use (or {@code null}, but just if no default executor is available)
	 */
	@Nullable
	protected AsyncTaskExecutor determineAsyncExecutor(Method method) {
		// 核心方法 -- 为切面异步方法查找执行器
		AsyncTaskExecutor executor = this.executors.get(method);
		if (executor == null) {
			// 缓存失效
			Executor targetExecutor;
			// 抽象方法：`AnnotationAsyncExecutionInterceptor`有实现。就是@Async注解的value值
			// 给异步切面类一个机会指定线程池，如果没有指定的话，就只能是获取默认的执行器了
			String qualifier = getExecutorQualifier(method);
			// 现在知道@Async直接的value值的作用了吧。就是制定执行此方法的执行器的（容器内执行器的Bean的名称）
			// 当然有可能为null。注意此处是支持@Qualified注解标注在类上来区分Bean的
			// 注意：此处targetExecutor仍然可能为null
			if (StringUtils.hasLength(qualifier)) {
				// 从BeanFactory中根据qualifier查看targetExecutor
				targetExecutor = findQualifiedExecutor(this.beanFactory, qualifier);
			}
			// 注解没有指定value值，那就去找默认的执行器
			else {
				// 获取默认的执行器
				targetExecutor = this.defaultExecutor.get();
			}
			if (targetExecutor == null) {
				// 若还未null，那就返回null吧
				return null;
			}
			// 把targetExecutor 包装成一个AsyncTaskExecutor返回，并且缓存起来。
			// TaskExecutorAdapter就是AsyncListenableTaskExecutor的一个实现类
			executor = (targetExecutor instanceof AsyncListenableTaskExecutor ? (AsyncListenableTaskExecutor) targetExecutor : new TaskExecutorAdapter(targetExecutor));
			// 存入缓存
			this.executors.put(method, executor);
		}
		return executor;
	}

	/**
	 * Return the qualifier or bean name of the executor to be used when executing the
	 * given async method, typically specified in the form of an annotation attribute.
	 * Returning an empty string or {@code null} indicates that no specific executor has
	 * been specified and that the {@linkplain #setExecutor(Executor) default executor}
	 * should be used.
	 * @param method the method to inspect for executor qualifier metadata
	 * @return the qualifier if specified, otherwise empty String or {@code null}
	 * @see #determineAsyncExecutor(Method)
	 * @see #findQualifiedExecutor(BeanFactory, String)
	 */
	@Nullable
	protected abstract String getExecutorQualifier(Method method);

	/**
	 * Retrieve a target executor for the given qualifier.
	 * @param qualifier the qualifier to resolve
	 * @return the target executor, or {@code null} if none available
	 * @since 4.2.6
	 * @see #getExecutorQualifier(Method)
	 */
	@Nullable
	protected Executor findQualifiedExecutor(@Nullable BeanFactory beanFactory, String qualifier) {
		if (beanFactory == null) {
			throw new IllegalStateException("BeanFactory must be set on " + getClass().getSimpleName() +
					" to access qualified executor '" + qualifier + "'");
		}
		return BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, Executor.class, qualifier);
	}

	/**
	 * Retrieve or build a default executor for this advice instance.
	 * An executor returned from here will be cached for further use.
	 * <p>The default implementation searches for a unique {@link TaskExecutor} bean
	 * in the context, or for an {@link Executor} bean named "taskExecutor" otherwise.
	 * If neither of the two is resolvable, this implementation will return {@code null}.
	 * @param beanFactory the BeanFactory to use for a default executor lookup
	 * @return the default executor, or {@code null} if none available
	 * @since 4.2.6
	 * @see #findQualifiedExecutor(BeanFactory, String)
	 * @see #DEFAULT_TASK_EXECUTOR_BEAN_NAME
	 */
	@Nullable
	protected Executor getDefaultExecutor(@Nullable BeanFactory beanFactory) {
		// 获取默认的Executor，前提是用户没有指定Executor
		if (beanFactory != null) {
			try {
				// Search for TaskExecutor bean... not plain Executor since that would
				// match with ScheduledExecutorService as well, which is unusable for
				// our purposes here. TaskExecutor is more clearly designed for it.
				// 尝试获取TaskExecutor
				return beanFactory.getBean(TaskExecutor.class);
			}
			catch (NoUniqueBeanDefinitionException ex) {
				// 有多个TaskExecutor时，获取指定名字为"taskExecutor"的Executor
				logger.debug("Could not find unique TaskExecutor bean", ex);
				try {
					return beanFactory.getBean(DEFAULT_TASK_EXECUTOR_BEAN_NAME, Executor.class);
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (logger.isInfoEnabled()) {
						logger.info("More than one TaskExecutor bean found within the context, and none is named " +
								"'taskExecutor'. Mark one of them as primary or name it 'taskExecutor' (possibly " +
								"as an alias) in order to use it for async processing: " + ex.getBeanNamesFound());
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				// 没有找到任何一个TaskExecutor时，就只好自己创建啦，
				logger.debug("Could not find default TaskExecutor bean", ex);
				try {
					return beanFactory.getBean(DEFAULT_TASK_EXECUTOR_BEAN_NAME, Executor.class);
				}
				catch (NoSuchBeanDefinitionException ex2) {
					logger.info("No task executor bean found for async processing: " +
							"no bean of type TaskExecutor and no bean named 'taskExecutor' either");
				}
				// 这里还没有获取到，就放弃。 用本地默认的executor吧~~~
				// 子类可以去复写此方法，发现为null的话可议给一个默认值~~~~比如`AsyncExecutionInterceptor`默认给的就是`SimpleAsyncTaskExecutor`作为执行器的
				// Giving up -> either using local default executor or none at all...
			}
		}
		return null;
	}


	/**
	 * Delegate for actually executing the given task with the chosen executor.
	 * @param task the task to execute
	 * @param executor the chosen executor
	 * @param returnType the declared return type (potentially a {@link Future} variant)
	 * @return the execution result (potentially a corresponding {@link Future} handle)
	 */
	@Nullable
	protected Object doSubmit(Callable<Object> task, AsyncTaskExecutor executor, Class<?> returnType) {
		// 根据不同的返回值类型，来采用不同的方案去异步执行，但是执行器都是executor

		// CompletableFuture类型，就要用CompletableFuture的方式执行
		if (CompletableFuture.class.isAssignableFrom(returnType)) {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return task.call();
				}
				catch (Throwable ex) {
					throw new CompletionException(ex);
				}
			}, executor);
		}
		// ListenableFuture 类型
		else if (ListenableFuture.class.isAssignableFrom(returnType)) {
			return ((AsyncListenableTaskExecutor) executor).submitListenable(task);
		}
		// Future类型，直接提交
		else if (Future.class.isAssignableFrom(returnType)) {
			return executor.submit(task);
		}
		// 同上
		else {
			executor.submit(task);
			return null;
		}
	}

	/**
	 * Handles a fatal error thrown while asynchronously invoking the specified
	 * {@link Method}.
	 * <p>If the return type of the method is a {@link Future} object, the original
	 * exception can be propagated by just throwing it at the higher level. However,
	 * for all other cases, the exception will not be transmitted back to the client.
	 * In that later case, the current {@link AsyncUncaughtExceptionHandler} will be
	 * used to manage such exception.
	 * @param ex the exception to handle
	 * @param method the method that was invoked
	 * @param params the parameters used to invoke the method
	 */
	protected void handleError(Throwable ex, Method method, Object... params) throws Exception {
		if (Future.class.isAssignableFrom(method.getReturnType())) {
			ReflectionUtils.rethrowException(ex);
		}
		else {
			// Could not transmit the exception to the caller with default executor
			try {
				this.exceptionHandler.obtain().handleUncaughtException(ex, method, params);
			}
			catch (Throwable ex2) {
				logger.warn("Exception handler for async method '" + method.toGenericString() +
						"' threw unexpected exception itself", ex2);
			}
		}
	}

}
