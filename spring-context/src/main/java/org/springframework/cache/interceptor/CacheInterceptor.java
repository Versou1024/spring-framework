/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.cache.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.lang.Nullable;

/**
 * AOP Alliance MethodInterceptor for declarative cache
 * management using the common Spring caching infrastructure
 * ({@link org.springframework.cache.Cache}).
 *
 * <p>Derives from the {@link CacheAspectSupport} class which
 * contains the integration with Spring's underlying caching API.
 * CacheInterceptor simply calls the relevant superclass methods
 * in the correct order.
 *
 * <p>CacheInterceptors are thread-safe.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
@SuppressWarnings("serial")
public class CacheInterceptor extends CacheAspectSupport implements MethodInterceptor, Serializable {
	// 缓存 - 拦截器

	@Override
	@Nullable
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		// 1. 获取目标方法
		Method method = invocation.getMethod();

		// 2. 创建 CacheOperationInvoker -- 实现函数式接口 CacheOperationInvoker
		// 函数栈 ...
		// org.springframework.cache.interceptor.CacheAspectSupport.handleSynchronizedGet() 执行 @Cacheable 从缓存中get未命中,执行方法
		// 或者 -- org.springframework.cache.interceptor.CacheAspectSupport.execute() 执行中触发
		// 在 org.springframework.cache.interceptor.CacheAspectSupport.invokeOperation() 触发该lambda表达式


		// 采用函数的形式，最终把此函数传交给父类的execute()去执行
		// 但是很显然，最终**执行目标方法**的是invocation.proceed();它

		// 这里就是对执行方法调用的一次封装，主要是为了处理对异常的包装。
		CacheOperationInvoker aopAllianceInvoker = () -> {
			try {
				return invocation.proceed();
			}
			catch (Throwable ex) {
				throw new CacheOperationInvoker.ThrowableWrapper(ex);
			}
		};

		// 3. 执行 aopAllianceInvoker
		try {
			// //真正地去处理缓存操作的执行，很显然这是父类的方法，所以我们要到父类CacheAspectSupport中去看看。
			return execute(aopAllianceInvoker, invocation.getThis(), method, invocation.getArguments());
		}
		catch (CacheOperationInvoker.ThrowableWrapper th) {
			throw th.getOriginal();
		}
	}

}
