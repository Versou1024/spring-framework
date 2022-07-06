/*
 * Copyright 2002-2016 the original author or authors.
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
import java.lang.reflect.Type;

import org.springframework.aop.AfterAdvice;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.TypeUtils;

/**
 * Spring AOP advice wrapping an AspectJ after-returning advice method.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAfterReturningAdvice extends AbstractAspectJAdvice
		implements AfterReturningAdvice, AfterAdvice, Serializable {
	// 	将@Aspec注解标注的切面类中@AfterReturning标注的通知方法 -> 转换为SpringAop框架的AfterReturningAdvice
	//	AfterReturningAdvice 会被 DefaultAdvisorAdapterRegistry.getInterceptors()或者wrap() 从advice转换为合适的MethodInterceptor
	
	
	// AfterReturningAdvice -> AfterAdvice -> Advice 标志性接口
	// AfterReturningAdvice 提供 AfterReturning() 的方法
	// AbstractAspectJAdvice -> Advice + AspectJPrecedenceInformation
	// 

	public AspectJAfterReturningAdvice(
			Method aspectJBeforeAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

		super(aspectJBeforeAdviceMethod, pointcut, aif);
	}


	@Override
	public boolean isBeforeAdvice() {
		return false;
	}

	@Override
	public boolean isAfterAdvice() {
		return true;
	}

	@Override
	public void setReturningName(String name) {
		setReturningNameNoCheck(name);
	}

	// note: AspectJAfterReturningAdvice 只有Advice没有具体的MethodInterceptor
	// 但是 AfterReturningAdvice 会在 DefaultAdvisorAdapterRegistry.getInterceptors()或者wrap() 将AfterReturningAdvice 转换为合适的MethodInterceptor
	// AfterReturningAdvice 对应就是  AfterReturningAdviceInterceptor -> 而恰好在AfterReturningAdviceInterceptor.interceptor()中执行了 mi.proceed()
	// 然后再触发这里的afterReturning()方法 -> 去执行@After标注的通知增强方法哦
	@Override
	public void afterReturning(@Nullable Object returnValue, Method method, Object[] args, @Nullable Object target) throws Throwable {
		// 1. 检查返回值的类型是否和接受返回值的通知方法中的形参类型是否兼容
		if (shouldInvokeOnReturnValueOf(method, returnValue)) {
			// 2. 如果兼容的话,就可以开始执行@AfterReturning标注的增强通知方法
			invokeAdviceMethod(getJoinPointMatch(), returnValue, null);
		}
	}


	/**
	 * Following AspectJ semantics, if a returning clause was specified, then the
	 * advice is only invoked if the returned value is an instance of the given
	 * returning type and generic type parameters, if any, match the assignment
	 * rules. If the returning type is Object, the advice is *always* invoked.
	 * @param returnValue the return value of the target method
	 * @return whether to invoke the advice method for the given return value
	 */
	private boolean shouldInvokeOnReturnValueOf(Method method, @Nullable Object returnValue) {
		// 1. 主要就是检查方法的返回值是否符合形参中返回值的类型哦
		// @AfterReturning(value = "target(com.yyq.aspectJAdvanced.SmartSeller)", returning = "retVal")
		// 比如上面指定返回值作为形参时的名字为retVal,而返回值为String类型,用户使用 Integer retVal 去接受就会报错
		Class<?> type = getDiscoveredReturningType();
		Type genericType = getDiscoveredReturningGenericType();
		// 1. 如果我们不处理原始类型，请检查泛型参数是否可分配。
		return (matchesReturnValue(type, method, returnValue) &&
				(genericType == null || genericType == type ||
						TypeUtils.isAssignable(genericType, method.getGenericReturnType())));
	}

	/**
	 * Following AspectJ semantics, if a return value is null (or return type is void),
	 * then the return type of target method should be used to determine whether advice
	 * is invoked or not. Also, even if the return type is void, if the type of argument
	 * declared in the advice method is Object, then the advice must still get invoked.
	 * @param type the type of argument declared in advice method
	 * @param method the advice method
	 * @param returnValue the return value of the target method
	 * @return whether to invoke the advice method for the given return value and type
	 */
	private boolean matchesReturnValue(Class<?> type, Method method, @Nullable Object returnValue) {
		if (returnValue != null) {
			return ClassUtils.isAssignableValue(type, returnValue);
		}
		else if (Object.class == type && void.class == method.getReturnType()) {
			return true;
		}
		else {
			return ClassUtils.isAssignable(type, method.getReturnType());
		}
	}

}
