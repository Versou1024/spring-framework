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

package org.springframework.aop.aspectj.annotation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.aop.aspectj.InstantiationModelAwarePointcutAdvisor;
import org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactory.AspectJAnnotation;
import org.springframework.aop.support.DynamicMethodMatcherPointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.lang.Nullable;

/**
 * Internal implementation of AspectJPointcutAdvisor.
 * Note that there will be one instance of this advisor for each target method.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
@SuppressWarnings("serial")
final class InstantiationModelAwarePointcutAdvisorImpl implements InstantiationModelAwarePointcutAdvisor, AspectJPrecedenceInformation, Serializable {
	/**=
	 * AspectJPointcutAdvisor 的内部实现。请注意，每个目标方法将有一个此advisor的实例。
	 */

	private static final Advice EMPTY_ADVICE = new Advice() {};


	// AspectJExpression切入点 -> 持有切入点方法的重要信息
	// 即从@Before\@After\@Around等注解的方法上的重要信息
	private final AspectJExpressionPointcut declaredPointcut; 

	private final Class<?> declaringClass; // 通知方法所在的类

	private final String methodName; // 通知方法名

	private final Class<?>[] parameterTypes; // 通知方法的参数类型

	private transient Method aspectJAdviceMethod; // 通知方法

	private final AspectJAdvisorFactory aspectJAdvisorFactory; // 从AspectJ切面类转换为SpringAOP的Advisor的工厂

	private final MetadataAwareAspectInstanceFactory aspectInstanceFactory; // 获取切面类实例aspectInstance和aspectMetadata的工厂 -- 切面类工厂

	private final int declarationOrder; // 指定声明的顺序

	private final String aspectName; // 切面类的名字

	private final Pointcut pointcut; // 最终生效的切入点

	private final boolean lazy; // 当前Advisor是否懒加载其advice

	// 从@Before\@After\@Around等注解的方法中实例化出来的Advice通知
	// 在创建InstantiationModelAwarePointcutAdvisorImpl对象的时候,对于PerClause为SINGLETON的切面类都会直接被实例化出来
	// 而其余类型的PerClause的需要在getAdvice()时延迟加载
	@Nullable
	private Advice instantiatedAdvice;

	// 是否前置
	@Nullable
	private Boolean isBeforeAdvice;

	// 是否后置
	@Nullable
	private Boolean isAfterAdvice; 


	public InstantiationModelAwarePointcutAdvisorImpl(AspectJExpressionPointcut declaredPointcut,
			Method aspectJAdviceMethod, AspectJAdvisorFactory aspectJAdvisorFactory,
			MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

		// 切入点的方法
		// 1.即@Pointcut/@Before/@After的value属性相关信息
		this.declaredPointcut = declaredPointcut;
		this.declaringClass = aspectJAdviceMethod.getDeclaringClass();
		this.methodName = aspectJAdviceMethod.getName();
		this.parameterTypes = aspectJAdviceMethod.getParameterTypes();
		// 使用AspectJ相关注解的标注的通知增强方法
		this.aspectJAdviceMethod = aspectJAdviceMethod;
		// 将aspectJ转为SpringAOP框架中的advisor的公
		this.aspectJAdvisorFactory = aspectJAdvisorFactory;
		// 持有切面aspect的实例和元数据的工厂
		this.aspectInstanceFactory = aspectInstanceFactory;
		// @Before\@After标注的通知方法的声明顺序
		this.declarationOrder = declarationOrder;
		// @Aspect标注的切面类的名字
		this.aspectName = aspectName;

		// 2. 只要不是PerTarget、PerThis、Within类，那么就是单例的Aspect，说明需要提前实例化，而不是懒加载
		if (aspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			// 2.1 懒加载
			// aspectInstanceFactory.getAspectMetadata().getPerClausePointcut() 主要AspectJMetadata创建时确定的
			// 	switch (this.ajType.getPerClause().getKind()) {
			//			case SINGLETON:
			//				this.perClausePointcut = Pointcut.TRUE; // 对于PerClause是SINGLETON返回PONITCUT.TRUE
			//				return;
			//			case PERTARGET:
			//			case PERTHIS:
			//				// PERTARGET和PERTHIS处理方式一样  返回的是AspectJExpressionPointcut
			//				AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut();
			//				ajexp.setLocation(aspectClass.getName());
			//				ajexp.setExpression(findPerClause(aspectClass));
			//				ajexp.setPointcutDeclarationScope(aspectClass);
			//				this.perClausePointcut = ajexp;
			//				return;
			//			case PERTYPEWITHIN:
			//				// Works with a type pattern
			//				// 组成的、合成得切点表达式~~~
			//				this.perClausePointcut = new ComposablePointcut(new TypePatternClassFilter(findPerClause(aspectClass)));
			//				return;
			Pointcut preInstantiationPointcut = Pointcuts.union(aspectInstanceFactory.getAspectMetadata().getPerClausePointcut(), this.declaredPointcut);

			// Make it dynamic: must mutate from pre-instantiation to post-instantiation state.
			// If it's not a dynamic pointcut, it may be optimized out
			// by the Spring AOP infrastructure after the first evaluation.
			this.pointcut = new PerTargetInstantiationModelPointcut(this.declaredPointcut, preInstantiationPointcut, aspectInstanceFactory);
			this.lazy = true;
		}
		else {
			// 2.2 立即加载
			this.pointcut = this.declaredPointcut;
			this.lazy = false; // 非懒加载
			// 2.2 ❗️❗️❗️
			// 为整个AspectJ注解标注的通知方法生成对应的Advice
			this.instantiatedAdvice = instantiateAdvice(this.declaredPointcut);
		}
		
		// 上面的两种情况 -- 都会导致instantiateAdvice()有不同的表现哦
	}

	// 实现 InstantiationModelAwarePointCutAdvisor extends PointcutAdvisor 的 isLazy()/getPointcut()/isAdviceInstantiated()方法

	/**
	 * The pointcut for Spring AOP to use.
	 * Actual behaviour of the pointcut will change depending on the state of the advice.
	 */
	@Override
	public Pointcut getPointcut() {
		// 一般而言就是: 使用AspectJ表达式完成的AspectJExpressionPointcut
		return this.pointcut;
	}

	@Override
	public boolean isLazy() {
		// 一般切面类直接使用功能@Aspect都是非懒加载的
		return this.lazy;
	}

	@Override
	public synchronized boolean isAdviceInstantiated() {
		// 当lazy为false时候,在构造器中就完成了设置哦
		return (this.instantiatedAdvice != null);
	}
	
	// 实现Advisor的getAdvice()\isPerInstance()两个接口

	/**
	 * Lazily instantiate advice if necessary.
	 */
	@Override
	public synchronized Advice getAdvice() {
		// 1. 当切面类即@Aspect修饰的切面类的Ajtype的PerClause的kind
		// 是PerTarget、PerThis、Within之一，那么就是懒加载的 -- 懒加载执行 instantiateAdvice(this.declaredPointcut)
		// 是SINGLETON,
		if (this.instantiatedAdvice == null) {
			this.instantiatedAdvice = instantiateAdvice(this.declaredPointcut);
		}
		return this.instantiatedAdvice;
	}

	private Advice instantiateAdvice(AspectJExpressionPointcut pointcut) {
		// ❗️❗️❗️
		// 核心方法之一：实例化advice -> 实际为委托给aspectJAdvisorFactory完成

		// 利用 Advisor工厂 开始生产 Advisor
		// 传入 aspectJAdviceMethod、pointcut、aspectInstanceFactory、declarationOrder、aspectName
		Advice advice = this.aspectJAdvisorFactory.getAdvice(this.aspectJAdviceMethod, pointcut,
				this.aspectInstanceFactory, this.declarationOrder, this.aspectName);
		return (advice != null ? advice : EMPTY_ADVICE);
	}

	/**
	 * This is only of interest for Spring AOP: AspectJ instantiation semantics
	 * are much richer. In AspectJ terminology, all a return of {@code true}
	 * means here is that the aspect is not a SINGLETON.
	 */
	@Override
	public boolean isPerInstance() {
		return (getAspectMetadata().getAjType().getPerClause().getKind() != PerClauseKind.SINGLETON);
	}

	/**
	 * Return the AspectJ AspectMetadata for this advisor.
	 */
	public AspectMetadata getAspectMetadata() {
		return this.aspectInstanceFactory.getAspectMetadata();
	}

	public MetadataAwareAspectInstanceFactory getAspectInstanceFactory() {
		return this.aspectInstanceFactory;
	}

	public AspectJExpressionPointcut getDeclaredPointcut() {
		return this.declaredPointcut;
	}
	
	//  实现AspectJPrecedenceInformation接口的方法

	@Override
	public int getOrder() {
		return this.aspectInstanceFactory.getOrder();
	}

	@Override
	public String getAspectName() {
		return this.aspectName;
	}

	@Override
	public int getDeclarationOrder() {
		return this.declarationOrder;
	}

	@Override
	public boolean isBeforeAdvice() {
		if (this.isBeforeAdvice == null) {
			// 懒加载
			determineAdviceType();
		}
		return this.isBeforeAdvice;
	}

	@Override
	public boolean isAfterAdvice() {
		if (this.isAfterAdvice == null) {
			// 懒加载
			determineAdviceType();
		}
		return this.isAfterAdvice;
	}

	/**
	 * Duplicates some logic from getAdvice, but importantly does not force
	 * creation of the advice.
	 */
	private void determineAdviceType() {
		// 又利用AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod()方法查找通知方法上的通知增强注解
		AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(this.aspectJAdviceMethod);
		if (aspectJAnnotation == null) {
			// 如果为空，啥都不是，sb
			this.isBeforeAdvice = false;
			this.isAfterAdvice = false;
		}
		else {
			switch (aspectJAnnotation.getAnnotationType()) {
				// around或pointcut都是无效的
				case AtPointcut:
				case AtAround:
					this.isBeforeAdvice = false;
					this.isAfterAdvice = false;
					break;
					// AtBefore 为 isBeforeAdvice = true
				case AtBefore:
					this.isBeforeAdvice = true;
					this.isAfterAdvice = false;
					break;
					// AtAfter\AtAfterReturning\AtAfterThrowing 的 isAfterAdvice = true
				case AtAfter:
				case AtAfterReturning:
				case AtAfterThrowing:
					this.isBeforeAdvice = false;
					this.isAfterAdvice = true;
					break;
			}
		}
	}


	private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
		inputStream.defaultReadObject();
		try {
			this.aspectJAdviceMethod = this.declaringClass.getMethod(this.methodName, this.parameterTypes);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Failed to find advice method on deserialization", ex);
		}
	}

	@Override
	public String toString() {
		return "InstantiationModelAwarePointcutAdvisor: expression [" + getDeclaredPointcut().getExpression() +
				"]; advice method [" + this.aspectJAdviceMethod + "]; perClauseKind=" +
				this.aspectInstanceFactory.getAspectMetadata().getAjType().getPerClause().getKind();
	}


	/**
	 * Pointcut implementation that changes its behaviour when the advice is instantiated.
	 * Note that this is a <i>dynamic</i> pointcut; otherwise it might be optimized out
	 * if it does not at first match statically.
	 */
	private static final class PerTargetInstantiationModelPointcut extends DynamicMethodMatcherPointcut {

		private final AspectJExpressionPointcut declaredPointcut;

		private final Pointcut preInstantiationPointcut;

		@Nullable
		private LazySingletonAspectInstanceFactoryDecorator aspectInstanceFactory;

		public PerTargetInstantiationModelPointcut(AspectJExpressionPointcut declaredPointcut,
				Pointcut preInstantiationPointcut, MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
			
			// AspectJ相关注解的切入点,比如@Before\@After的AspectJExpressionPointcut
			this.declaredPointcut = declaredPointcut;
			// 切面类的PerClause的PointCut
			this.preInstantiationPointcut = preInstantiationPointcut;
			if (aspectInstanceFactory instanceof LazySingletonAspectInstanceFactoryDecorator) {
				this.aspectInstanceFactory = (LazySingletonAspectInstanceFactoryDecorator) aspectInstanceFactory;
			}
		}

		
		// 静态方法匹配
		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			// We're either instantiated and matching on declared pointcut,
			// or uninstantiated matching on either pointcut...
			return (isAspectMaterialized() && this.declaredPointcut.matches(method, targetClass)) ||
					this.preInstantiationPointcut.getMethodMatcher().matches(method, targetClass);
		}

		// 动态方法匹配
		@Override
		public boolean matches(Method method, Class<?> targetClass, Object... args) {
			// This can match only on declared pointcut.
			return (isAspectMaterialized() && this.declaredPointcut.matches(method, targetClass));
		}

		private boolean isAspectMaterialized() {
			// 只要不是 LazySingletonAspectInstanceFactoryDecorator
			// 或者, 是LazySingletonAspectInstanceFactoryDecorator的情况下,已经实例化了AspectInstance
			return (this.aspectInstanceFactory == null || this.aspectInstanceFactory.isMaterialized());
		}
	}

}
