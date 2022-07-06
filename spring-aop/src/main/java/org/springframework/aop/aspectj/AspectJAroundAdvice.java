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

package org.springframework.aop.aspectj;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.weaver.tools.JoinPointMatch;

import org.springframework.aop.ProxyMethodInvocation;

/**
 * Spring AOP around advice (MethodInterceptor) that wraps
 * an AspectJ advice method. Exposes ProceedingJoinPoint.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAroundAdvice extends AbstractAspectJAdvice implements MethodInterceptor, Serializable {
	// 将@Aspec注解标注的切面类中@Around标注的通知方法 -> 转换为SpringAop框架的MethodInterceptor

	public AspectJAroundAdvice(
			Method aspectJAroundAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

		super(aspectJAroundAdviceMethod, pointcut, aif);
	}


	@Override
	public boolean isBeforeAdvice() {
		return false;
	}

	@Override
	public boolean isAfterAdvice() {
		return false;
	}

	@Override
	protected boolean supportsProceedingJoinPoint() {
		// 注意: @Around是支持使用ProceedingJoinPoint形参的
		return true;
	}

	@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		ProxyMethodInvocation pmi = (ProxyMethodInvocation) mi;
		// 一种特殊的JointPoint -> ProceedingJoinPoint extends JoinPoint
		ProceedingJoinPoint pjp = lazyGetProceedingJoinPoint(pmi);
		// ❗️❗️❗️ -> 从pmi待拦截的方法信息中,截取出有用的JoinPointMatch切入点匹配信息是很有关的 -> 具体和参数绑定有关哦
		JoinPointMatch jpm = getJoinPointMatch(pmi);
		// 注意:@Around是没有返回值和抛出异常的哦
		return invokeAdviceMethod(pjp, jpm, null, null);
	}

	/**
	 * Return the ProceedingJoinPoint for the current invocation,
	 * instantiating it lazily if it hasn't been bound to the thread already.
	 * @param rmi the current Spring AOP ReflectiveMethodInvocation,
	 * which we'll use for attribute binding
	 * @return the ProceedingJoinPoint to make available to advice methods
	 */
	protected ProceedingJoinPoint lazyGetProceedingJoinPoint(ProxyMethodInvocation rmi) {
		// 在@Around中第一个形参可以是当前返回的 ProceedingJoinPoint
		// MethodInvocationProceedingJoinPoint 是 ProceedingJoinPoint 的实现类哦 ~~ 源码简单,作为了解
		return new MethodInvocationProceedingJoinPoint(rmi);
	}

}
