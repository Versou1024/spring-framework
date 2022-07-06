/*
 * Copyright 2002-2019 the original author or authors.
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

package org.aopalliance.intercept;

import org.aopalliance.aop.Advice;

/**
 * This interface represents a generic interceptor.
 *
 * <p>A generic interceptor can intercept runtime events that occur
 * within a base program. Those events are materialized by (reified
 * in) joinpoints. Runtime joinpoints can be invocations, field
 * access, exceptions...
 *
 * <p>This interface is not used directly. Use the sub-interfaces
 * to intercept specific events. For instance, the following class
 * implements some specific interceptors in order to implement a
 * debugger:
 *
 * <pre class=code>
 * class DebuggingInterceptor implements MethodInterceptor,
 *     ConstructorInterceptor {
 *
 *   Object invoke(MethodInvocation i) throws Throwable {
 *     debug(i.getMethod(), i.getThis(), i.getArgs());
 *     return i.proceed();
 *   }
 *
 *   Object construct(ConstructorInvocation i) throws Throwable {
 *     debug(i.getConstructor(), i.getThis(), i.getArgs());
 *     return i.proceed();
 *   }
 *
 *   void debug(AccessibleObject ao, Object this, Object value) {
 *     ...
 *   }
 * }
 * </pre>
 *
 * @author Rod Johnson
 * @see Joinpoint
 */
public interface Interceptor extends Advice {
	// 拦截器代表一个通用的拦截器。
	// 通用拦截器可以拦截在基本程序中发生的运行时事件。这些事件通过（具体化）连接点实现。运行时连接点可以是调用、字段访问、异常......
	// 因此它不用于 BeforeAdvice\AfterAdvice\ThrowsAdvice等等 --> 这些都是有指定拦截场景的语义
	// 比如BeforeAdvice发生在方法调用前\AfterAdvice发生在方法调用后\ThrowAdvice发生方法抛出异常后才会执行
	// 而Interceptor则可以在任何拦截场景下进行拦截
}
