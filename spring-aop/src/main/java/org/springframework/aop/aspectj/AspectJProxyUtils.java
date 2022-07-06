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

package org.springframework.aop.aspectj;

import java.util.List;

import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;

/**
 * Utility methods for working with AspectJ proxies.
 *
 * @author Rod Johnson
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AspectJProxyUtils {
	// 同样的，它相对于AopProxyUtils，它只是专门处理AspectJ代理对象的工具类。

	/**
	 * Add special advisors if necessary to work with a proxy chain that contains AspectJ advisors:
	 * concretely, {@link ExposeInvocationInterceptor} at the beginning of the list.
	 * <p>This will expose the current Spring AOP invocation (necessary for some AspectJ pointcut
	 * matching) and make available the current AspectJ JoinPoint. The call will have no effect
	 * if there are no AspectJ advisors in the advisor chain.
	 * @param advisors the advisors available
	 * @return {@code true} if an {@link ExposeInvocationInterceptor} was added to the list,
	 * otherwise {@code false}
	 */
	public static boolean makeAdvisorChainAspectJCapableIfNecessary(List<Advisor> advisors) {
		// ❗️❗️❗️
		// 只提供这么一个公共方法，但是这个方法都还是非常重要的。 Capable：有能力的
		// 它在自动代理创建器`AspectJAwareAdvisorAutoProxyCreator#extendAdvisors`方法中有调用（重要~~~）
		// 在AspectJProxyFactory#addAdvisorsFromAspectInstanceFactory方法中也有调用
		// 它的作用：只要发现有AspectJ的Advisor存在，并且advisors还不包含有ExposeInvocationInterceptor.ADVISOR
		// 那就在第一个位置上调添加一个ExposeInvocationInterceptor.ADVISOR
		// 这个`ExposeInvocationInterceptor.ADVISOR`的作用：就是获取到当前的currentInvocation，也是使用的ThreadLocal
		// 可以暴露当前Spring AOP的invocation，这个的调用不影响advisor chain的调用
		// 原因在于: AspectJ注解的通知增强方法构成的Advice是需要自动MethodInvocation,因此需要通过ExposeInvocationInterceptor帮助暴露出来
		// 在
		if (!advisors.isEmpty()) {
			boolean foundAspectJAdvice = false;
			for (Advisor advisor : advisors) {
				// Be careful not to get the Advice without a guard, as this might eagerly
				// instantiate a non-singleton AspectJ aspect...
				if (isAspectJAdvice(advisor)) {
					foundAspectJAdvice = true;
					break;
				}
			}
			if (foundAspectJAdvice && !advisors.contains(ExposeInvocationInterceptor.ADVISOR)) {
				advisors.add(0, ExposeInvocationInterceptor.ADVISOR);
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether the given Advisor contains an AspectJ advice.
	 * @param advisor the Advisor to check
	 */
	private static boolean isAspectJAdvice(Advisor advisor) {
		// 判断，该Advisor是否是AspectJ的的增强器
		return (advisor instanceof InstantiationModelAwarePointcutAdvisor ||
				advisor.getAdvice() instanceof AbstractAspectJAdvice ||
				(advisor instanceof PointcutAdvisor &&
						((PointcutAdvisor) advisor).getPointcut() instanceof AspectJExpressionPointcut));
	}

}
