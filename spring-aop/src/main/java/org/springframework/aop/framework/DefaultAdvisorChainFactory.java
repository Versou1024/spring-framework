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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {
	//DefaultAdvisorChainFactory：生成拦截器链

	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass) {
		// 获取匹配 targetClass 与 method 的所有切面的通知

		// 1. 注册中心：AdvisorAdapter 的注册表
		// 下面这个适配器将通知 [Advice] 包装成拦截器 [MethodInterceptor]; 而 DefaultAdvisorAdapterRegistry则是适配器的默认实现
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		Advisor[] advisors = config.getAdvisors();
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;

		// 2. 遍历advisors
		for (Advisor advisor : advisors) {
			// 2.1 PointcutAdvisor类型 -> 自带切点
			if (advisor instanceof PointcutAdvisor) {
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// 2.1.1 对于 PointcutAdvisor ，需要满足 ClassFilter、MethodMatcher ，然后才允许getInterceptors
				// config.isPreFiltered() 为true,表示生成的代理对象,已经是经过classFilter的匹配的,否则就需要调用classFilter哦
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					// 2.1.2  检查是否通过MethodMatcher
					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					} else {
						match = mm.matches(method, actualClass);
					}
					// 2.1.3 通过ClassFilter和MethodMatcher的匹配
					if (match) {
						//  ❗️❗️❗️
						//  因为最终需要执行的是拦截器即 MethodInterceptor#invoke(MethodInvocation invocation) 方法 -> 所以在有需要的情况下将advisor中的advice适配为MethodInterceptor
						//  通过适配器模式将通知 [Advice] 包装成 MethodInterceptor, 这里为什么是个数组? 
						//  因为一个类可以同时实现比如前置通知[MethodBeforeAdvice], 后置通知[AfterReturningAdvice], 异常通知接口[ThrowsAdvice]几个接口,那么映射出来的MethodInterceptor就有多个
						//  就需要适配为相应的 前置通知适配器MethodBeforeAdviceAdapter / 后置通知适配器AfterReturningAdviceAdapter / 异常通知适配器ThrowsAdviceAdapter
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						// 2.1.4 静态匹配完,查看是否需要动态匹配,即根据具体传入的形参值判断是否需要拦截器
						if (mm.isRuntime()) {
							// 2.1.5 如果需要在运行时动态拦截方法的执行则创建一个简单的对象封装相关的数据
							// interceptors所有的MethodInterceptor都会被封装为InterceptorAndDynamicMethodMatcher
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						} else {
							// 2.1.4 如果不是运行时动态匹配,那么传入就是MethodInterceptor[]而不是InterceptorAndDynamicMethodMatcher
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			// 2.2 如果advisor不是PointcutAdvisor,而是IntroductionAdvisor
			else if (advisor instanceof IntroductionAdvisor) {
				// 如果是引入切面的话，则判断它是否适用于目标类, Spring 中默认的引入切面实现是 DefaultIntroductionAdvisor 类
				// 默认的引入通知是 DelegatingIntroductionInterceptor 它实现了 MethodInterceptor 接口s
				// 属于IntroductionAdvisor时，只需要满足 classFilter 类过滤器进行匹配
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			// 2.3 普通的Advisor类型，不需要满足任何ClassFilter、MethodMatcher即可加入到interceptors
			else {
				// 从AdvisorAdapterRegistry中获取这个Advisor对应的Interceptor[]
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}
		// 实际上99%的情况都会执行2.1的地方

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
