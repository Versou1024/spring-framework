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

package org.springframework.aop.aspectj;

import org.aopalliance.aop.Advice;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.support.ClassFilters;
import org.springframework.aop.support.DelegatePerTargetObjectIntroductionInterceptor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

/**
 * Introduction advisor delegating to the given object.
 * Implements AspectJ annotation-style behavior for the DeclareParents annotation.
 *
 * @author Rod Johnson
 * @author Ramnivas Laddad
 * @since 2.0
 */
public class DeclareParentsAdvisor implements IntroductionAdvisor {

	private final Advice advice;

	private final Class<?> introducedInterface;

	private final ClassFilter typePatternClassFilter;


	/**
	 * Create a new advisor for this DeclareParents field.
	 * @param interfaceType static field defining the introduction
	 * @param typePattern type pattern the introduction is restricted to
	 * @param defaultImpl the default implementation class
	 */
	public DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, Class<?> defaultImpl) {
		
		// 形参如下: 
		// 标记有@DeclareParents注解的字段的类型Type\@DeclareParents的value属性\@DeclareParents的defaultImpl属性
		this(interfaceType, typePattern,
				new DelegatePerTargetObjectIntroductionInterceptor(defaultImpl, interfaceType));
	}

	/**
	 * Create a new advisor for this DeclareParents field.
	 * @param interfaceType static field defining the introduction
	 * @param typePattern type pattern the introduction is restricted to
	 * @param delegateRef the delegate implementation object
	 */
	public DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, Object delegateRef) {
		this(interfaceType, typePattern, new DelegatingIntroductionInterceptor(delegateRef));
	}

	/**
	 * Private constructor to share common code between impl-based delegate and reference-based delegate
	 * (cannot use method such as init() to share common code, due the use of final fields).
	 * @param interfaceType static field defining the introduction
	 * @param typePattern type pattern the introduction is restricted to
	 * @param interceptor the delegation advice as {@link IntroductionInterceptor}
	 */
	private DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, IntroductionInterceptor interceptor) {
		this.advice = interceptor;
		// 标记有@DeclareParents注解的字段的类型Type -- 也就是代理类需要额外实现的接口
		// @DeclareParents注解的defaultImpl属性 -- 代理实现的接口被触发时,应该交给defaultImpl属性指定的class去执行
		// 也就是说defaultImpl属性指定的对象也需要去实现最上面说的接口哦
		this.introducedInterface = interfaceType;

		// typePattern 是 @DeclareParents注解的value属性 -- 标记为哪些类生成代理对象,需要额外实现的上面的接口
		ClassFilter typePatternFilter = new TypePatternClassFilter(typePattern);
		ClassFilter exclusion = (clazz -> !this.introducedInterface.isAssignableFrom(clazz)); // 如果扫描到的类本身已经实现clazz,就不需要切面类来帮助代理委托实现了啊 -> 因此是一个排除
		// ClassFilters.intersection(typePatternFilter, exclusion) 就要求两个ClassFilter的and操作,即必须两个都满足才可以
		this.typePatternClassFilter = ClassFilters.intersection(typePatternFilter, exclusion);
	}


	@Override
	public ClassFilter getClassFilter() {
		return this.typePatternClassFilter;
	}

	@Override
	public void validateInterfaces() throws IllegalArgumentException {
		// Do nothing
	}

	@Override
	public boolean isPerInstance() {
		return true;
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Class<?>[] getInterfaces() {
		return new Class<?>[] {this.introducedInterface};
	}

}
