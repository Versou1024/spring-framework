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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * AOP Alliance {@code MethodInterceptor} that processes method invocations
 * asynchronously, using a given {@link org.springframework.core.task.AsyncTaskExecutor}.
 * Typically used with the {@link org.springframework.scheduling.annotation.Async} annotation.
 *
 * <p>In terms of target method signatures, any parameter types are supported.
 * However, the return type is constrained to either {@code void} or
 * {@code java.util.concurrent.Future}. In the latter case, the Future handle
 * returned from the proxy will be an actual asynchronous Future that can be used
 * to track the result of the asynchronous method execution. However, since the
 * target method needs to implement the same signature, it will have to return
 * a temporary Future handle that just passes the return value through
 * (like Spring's {@link org.springframework.scheduling.annotation.AsyncResult}
 * or EJB 3.1's {@code javax.ejb.AsyncResult}).
 *
 * <p>When the return type is {@code java.util.concurrent.Future}, any exception thrown
 * during the execution can be accessed and managed by the caller. With {@code void}
 * return type however, such exceptions cannot be transmitted back. In that case an
 * {@link AsyncUncaughtExceptionHandler} can be registered to process such exceptions.
 *
 * <p>As of Spring 3.1.2 the {@code AnnotationAsyncExecutionInterceptor} subclass is
 * preferred for use due to its support for executor qualification in conjunction with
 * Spring's {@code @Async} annotation.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 3.0
 * @see org.springframework.scheduling.annotation.Async
 * @see org.springframework.scheduling.annotation.AsyncAnnotationAdvisor
 * @see org.springframework.scheduling.annotation.AnnotationAsyncExecutionInterceptor
 */
public class AsyncExecutionInterceptor extends AsyncExecutionAspectSupport implements MethodInterceptor, Ordered {
	// 在AsyncExecutionAspectSupport的基础上，扩展增强通知即MethodInterceptor
	// AsyncExecutionInterceptor = AsyncExecution Interceptor

	/**
	 * Create a new instance with a default {@link AsyncUncaughtExceptionHandler}.
	 * @param defaultExecutor the {@link Executor} (typically a Spring {@link AsyncTaskExecutor}
	 * or {@link java.util.concurrent.ExecutorService}) to delegate to;
	 * as of 4.2.6, a local executor for this interceptor will be built otherwise
	 */
	public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor) {
		super(defaultExecutor);
	}

	/**
	 * Create a new {@code AsyncExecutionInterceptor}.
	 * @param defaultExecutor the {@link Executor} (typically a Spring {@link AsyncTaskExecutor}
	 * or {@link java.util.concurrent.ExecutorService}) to delegate to;
	 * as of 4.2.6, a local executor for this interceptor will be built otherwise
	 * @param exceptionHandler the {@link AsyncUncaughtExceptionHandler} to use
	 */
	public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
		super(defaultExecutor, exceptionHandler);
	}


	/**
	 * Intercept the given method invocation, submit the actual calling of the method to
	 * the correct task executor and return immediately to the caller.
	 * @param invocation the method to intercept and make asynchronous
	 * @return {@link Future} if the original method returns {@code Future}; {@code null}
	 * otherwise.
	 */
	@Override
	@Nullable
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		// ❗️❗️❗️切面增强的方法执行
		// 最重要的当然是这个invoke方法

		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
		Method specificMethod = ClassUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
		final Method userDeclaredMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);

		// 1. 决定使用哪一个用于执行此方法的异步执行器
		// 简述逻辑 -- 
		// 1. 查看当前方法上是否有@Async注解的value属性值，是否指定使用的Executor的beanName [方法上没有@Async，就会去类上查看@Async注解]
		// 2. 没有指定@Async的value时,若用户有直接通过实现AsyncConfigurer，定制化一个Executor,直接返回
		// 3. 用户也没有指定Executor时，将调用getDefaultExecutor()方法获取执行器
		//  	3.1 直接从ioc容器中查找TaskExecutor类型的bean出来
		//			3.1.1 如果有多个TaskExecutor类型的bean，找出beanName等于taskExecutor，beanType为Executor的执行器bean出来
		//			3.1.2 如果没有找到任何一个TaskExecutor类型的bean，兜底去ioc容器找属于Executor就可以啦
		// 4. 还找不到,AsyncExecutionInterceptor.getDefaultExecutor()进行了兜底操作,使用SimpleAsyncTaskExecutor
		AsyncTaskExecutor executor = determineAsyncExecutor(userDeclaredMethod);
		if (executor == null) {
			throw new IllegalStateException(
					"No executor specified and no default executor set on AsyncExecutionInterceptor either");
		}

		// 2. 构造一个任务，Callable(此处不采用Runable，因为需要返回值)
		Callable<Object> task = () -> {
			try {
				// result就是返回值
				Object result = invocation.proceed();
				// 注意此处的处理~~~~  相当于如果不是Future类型，就返回null了
				if (result instanceof Future) {
					return ((Future<?>) result).get();
				}
			}
			// 处理执行时可能产生的异常~~~~~~
			catch (ExecutionException ex) {
				handleError(ex.getCause(), userDeclaredMethod, invocation.getArguments());
			}
			catch (Throwable ex) {
				handleError(ex, userDeclaredMethod, invocation.getArguments());
			}
			return null;
		};

		// 3. 提交任务
		return doSubmit(task, executor, invocation.getMethod().getReturnType());
	}

	/**
	 * This implementation is a no-op for compatibility in Spring 3.1.2.
	 * Subclasses may override to provide support for extracting qualifier information,
	 * e.g. via an annotation on the given method.
	 * @return always {@code null}
	 * @since 3.1.2
	 * @see #determineAsyncExecutor(Method)
	 */
	@Override
	@Nullable
	protected String getExecutorQualifier(Method method) {
		return null;
	}

	/**
	 * This implementation searches for a unique {@link org.springframework.core.task.TaskExecutor}
	 * bean in the context, or for an {@link Executor} bean named "taskExecutor" otherwise.
	 * If neither of the two is resolvable (e.g. if no {@code BeanFactory} was configured at all),
	 * this implementation falls back to a newly created {@link SimpleAsyncTaskExecutor} instance
	 * for local use if no default could be found.
	 * @see #DEFAULT_TASK_EXECUTOR_BEAN_NAME
	 */
	@Override
	@Nullable
	protected Executor getDefaultExecutor(@Nullable BeanFactory beanFactory) {
		// 重写：getDefaultExecutor
		// 如果super.getDefaultExecutor从BeanFactory尝试获取TaskExecutor失败
		// 就提供SimpleAsyncTaskExecutor给用户使用吧 
		Executor defaultExecutor = super.getDefaultExecutor(beanFactory);
		// ❗️❗️❗️ 兜底
		//  SimpleAsyncTaskExecutor#doExecute(Runnable) 如下
		// 	protected void doExecute(Runnable task) {
		//		// SimpleAsyncTaskExecutor 没有使用线程,是直接new一个Thread,调用其start()方法 -> 这点需要注意哦
		//		Thread thread = (this.threadFactory != null ? this.threadFactory.newThread(task) : createThread(task));
		//		thread.start();
		//	}
		// 最好还是指定线程池吧
		return (defaultExecutor != null ? defaultExecutor : new SimpleAsyncTaskExecutor());
	}

	@Override
	public int getOrder() {
		// 这个advisor的执行优先级最高
		// 必须确保异步操作是率先执行的哦
		return Ordered.HIGHEST_PRECEDENCE; 
	}

}
