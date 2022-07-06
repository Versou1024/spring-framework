/*
 * Copyright 2002-2021 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;

/**
 * Abstract base class for factories that can create Spring AOP Advisors
 * given AspectJ classes from classes honoring the AspectJ 5 annotation syntax.
 *
 * <p>This class handles annotation parsing and validation functionality.
 * It does not actually generate Spring AOP Advisors, which is deferred to subclasses.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AbstractAspectJAdvisorFactory implements AspectJAdvisorFactory {
	// 🇫🇯🇫🇯🇫🇯
	// AspectJAdvisorFactory: 是通过给定AspectJ的classes就可以创建SpringAop的advisor的工厂
	// AbstractAspectJAdvisorFactory 继承了 AspectJAdvisorFactory 作为一个抽象类
	//  	主要目的: 提供可使用的工具方法给子类使用,实现通用逻辑isAspect(Class<?>)\validate(Class<?>)
	// 核心业务: 即创建Advisors[将带有AspectJ注解语法的方法给转换过去即可]仍需要子类ReflectiveAspectJAdvisorFactory去实现哦
	
	// 要求: AspectJ的classes的切入点遵循 AspectJ 5 注解语法
	private static final String AJC_MAGIC = "ajc$";

	// ❗️❗️❗️支持的增强通知注解：用于后续检查
	// @Pointcut 引用其他通知
	// @Around 环绕通知
	// @Before 前置通知
	// @After 后置通知
	// @AfterReturning 返回后通知
	// @AfterThrowing 抛出异常后通知
	private static final Class<?>[] ASPECTJ_ANNOTATION_CLASSES = new Class<?>[] {Pointcut.class, Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class};


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	// 参数名发现器 -- 默认使用AspectJAnnotationParameterNameDiscoverer
	protected final ParameterNameDiscoverer parameterNameDiscoverer = new AspectJAnnotationParameterNameDiscoverer();


	/**
	 * We consider something to be an AspectJ aspect suitable for use by the Spring AOP system
	 * if it has the @Aspect annotation, and was not compiled by ajc. The reason for this latter test
	 * is that aspects written in the code-style (AspectJ language) also have the annotation present
	 * when compiled by ajc with the -1.5 flag, yet they cannot be consumed by Spring AOP.
	 */
	@Override
	public boolean isAspect(Class<?> clazz) {
		// ❗️❗️❗️
		// 如果它具有@Aspect注解并且不是由ajc编译的，我们认为它是适合Spring AOP 系统使用Advisor
		return (hasAspectAnnotation(clazz) && !compiledByAjc(clazz));
	}

	private boolean hasAspectAnnotation(Class<?> clazz) {
		// 第一步：检查是否有@Aspect注解
		// 该算法的操作如下：
		//		在给定类上搜索注释，如果找到则返回。
		//		递归搜索给定类声明的所有注释。
		//		递归搜索给定类声明的所有接口。
		//		递归搜索给定类的超类层次结构
		return (AnnotationUtils.findAnnotation(clazz, Aspect.class) != null);
	}

	/**
	 * We need to detect this as "code-style" AspectJ aspects should not be
	 * interpreted by Spring AOP.
	 */
	private boolean compiledByAjc(Class<?> clazz) {
		// 我们需要检测到这一点，因为Spring AOP不应该解释“代码风格”的AspectJ方面。

		// 第二步：检查字段名是否有"ajc$"
		for (Field field : clazz.getDeclaredFields()) {
			if (field.getName().startsWith(AJC_MAGIC)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void validate(Class<?> aspectClass) throws AopConfigException {


		// 1、aspectClass的超类有@Aspect注解,并且超类非抽象类,就抛出异常
		Class<?> superclass = aspectClass.getSuperclass();
		if (superclass.getAnnotation(Aspect.class) != null &&
				!Modifier.isAbstract(superclass.getModifiers())) {
			throw new AopConfigException("[" + aspectClass.getName() + "] cannot extend concrete aspect [" +
					superclass.getName() + "]");
		}

		// 2、PerClauseKind是否为支持的
		AjType<?> ajType = AjTypeSystem.getAjType(aspectClass);
		// 2.1 是否为有效的Aspect切面
		if (!ajType.isAspect()) {
			throw new NotAnAtAspectException(aspectClass);
		}
		// 2.2 不支持PERCFLOW与PERCFLOWBELOW
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflow instantiation model: " +
					"This is not supported in Spring AOP.");
		}
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOWBELOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflowbelow instantiation model: " +
					"This is not supported in Spring AOP.");
		}
	}

	/*
	 * 查找并返回给定方法的第一个 AspectJ 注解（无论如何应该只有一个......）
	 * AspectJ注解包括: Pointcut.class, Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	protected static AspectJAnnotation<?> findAspectJAnnotationOnMethod(Method method) {
		// 方法上必须有Pointcut.class, Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class
		// 才认为是通知增强方法，并返回这个对应的注解 -> 使用 AspectJAnnotation 包装起来哦
		for (Class<?> clazz : ASPECTJ_ANNOTATION_CLASSES) {
			AspectJAnnotation<?> foundAnnotation = findAnnotation(method, (Class<Annotation>) clazz);
			if (foundAnnotation != null) {
				return foundAnnotation;
			}
		}
		return null;
	}

	@Nullable
	private static <A extends Annotation> AspectJAnnotation<A> findAnnotation(Method method, Class<A> toLookFor) {
		// 查找method上是否有自动的通知增强注解，有的话，转换为AspectJAnnotation
		A result = AnnotationUtils.findAnnotation(method, toLookFor);
		if (result != null) {
			// 使用AspectJAnnotation包装起来
			return new AspectJAnnotation<>(result);
		}
		else {
			return null;
		}
	}


	/**
	 * Enum for AspectJ annotation types.
	 * @see AspectJAnnotation#getAnnotationType()
	 */
	protected enum AspectJAnnotationType {
		// AspectJAnnotationType = AspectJ Annotation Type
		// 枚举值
		AtPointcut, AtAround, AtBefore, AtAfter, AtAfterReturning, AtAfterThrowing
	}


	/**
	 * Class modelling an AspectJ annotation, exposing its type enumeration and
	 * pointcut String.
	 * @param <A> the annotation type
	 */
	protected static class AspectJAnnotation<A extends Annotation> {
		// AspectJAnnotation = AspectJ Annotation
		// 内部类：用于将通知增强注解@Around\@PointCut\@Before\@After等等转换为相应AspectJAnnotation

		// 注解中的属性: 比如@Before的value属性\@AfterReturning的pointCut属性
		private static final String[] EXPRESSION_ATTRIBUTES = new String[] {"pointcut", "value"};

		private static Map<Class<?>, AspectJAnnotationType> annotationTypeMap = new HashMap<>(8);

		static {
			// 通知增强注解 -> 通知枚举类型 的映射关系
			annotationTypeMap.put(Pointcut.class, AspectJAnnotationType.AtPointcut);
			annotationTypeMap.put(Around.class, AspectJAnnotationType.AtAround);
			annotationTypeMap.put(Before.class, AspectJAnnotationType.AtBefore);
			annotationTypeMap.put(After.class, AspectJAnnotationType.AtAfter);
			annotationTypeMap.put(AfterReturning.class, AspectJAnnotationType.AtAfterReturning);
			annotationTypeMap.put(AfterThrowing.class, AspectJAnnotationType.AtAfterThrowing);
		}

		// 通知增强注解
		private final A annotation;

		// 通知增强注解对应枚举类型
		private final AspectJAnnotationType annotationType;

		// 通知增强注解annotation中的value属性即AspectJ表达式
		private final String pointcutExpression;

		// 通知增强注解annotation中的argNames属性
		private final String argumentNames; 

		public AspectJAnnotation(A annotation) {
			// 1. 通知增强注解
			this.annotation = annotation;
			// 2. 查找对应的AspectJAnnotationType枚举类型
			this.annotationType = determineAnnotationType(annotation);
			try {
				// 3. 获取@Before、@After等注解中value的AspectJ表达式
				this.pointcutExpression = resolveExpression(annotation); // value属性
				// 4. 获取通知增强注解@Before、@After等注解中的 argNames 属性
				Object argNames = AnnotationUtils.getValue(annotation, "argNames");
				this.argumentNames = (argNames instanceof String ? (String) argNames : ""); // argNames属性
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(annotation + " is not a valid AspectJ annotation", ex);
			}
		}

		// 根据annotationTypeMap将annotation转换为AspectJAnnotationType
		private AspectJAnnotationType determineAnnotationType(A annotation) {
			AspectJAnnotationType type = annotationTypeMap.get(annotation.annotationType());
			if (type != null) {
				return type;
			}
			throw new IllegalStateException("Unknown annotation type: " + annotation);
		}

		// 获取@Before、@After等注解中value的AspectJ表达式
		private String resolveExpression(A annotation) {
			for (String attributeName : EXPRESSION_ATTRIBUTES) {
				Object val = AnnotationUtils.getValue(annotation, attributeName);
				if (val instanceof String) {
					String str = (String) val;
					if (!str.isEmpty()) {
						return str;
					}
				}
			}
			throw new IllegalStateException("Failed to resolve expression: " + annotation);
		}

		public AspectJAnnotationType getAnnotationType() {
			return this.annotationType;
		}

		public A getAnnotation() {
			return this.annotation;
		}

		public String getPointcutExpression() {
			return this.pointcutExpression;
		}

		public String getArgumentNames() {
			return this.argumentNames;
		}

		@Override
		public String toString() {
			return this.annotation.toString();
		}
	}


	/**
	 * ParameterNameDiscoverer implementation that analyzes the arg names
	 * specified at the AspectJ annotation level.
	 */
	private static class AspectJAnnotationParameterNameDiscoverer implements ParameterNameDiscoverer {
		// AspectJAnnotationParameterNameDiscoverer = AspectJ Annotation ParameterName Discoverer
		// 适用于: AspectJ使用的哦

		@Override
		@Nullable
		public String[] getParameterNames(Method method) {
			// 1. 形参数量为0,返回空数组
			if (method.getParameterCount() == 0) {
				return new String[0];
			}
			// 2. 查看通知增强型注解如@Before/@After -> 多个的时候,只有第一个生效哦
			AspectJAnnotation<?> annotation = findAspectJAnnotationOnMethod(method);
			if (annotation == null) {
				return null;
			}
			// 3. 执行到这: 形参数量非0,且有增强型通知注解
			// 获取通知注解中的argNames属性,允许通过逗号","分割开来
			StringTokenizer nameTokens = new StringTokenizer(annotation.getArgumentNames(), ",");
			if (nameTokens.countTokens() > 0) {
				String[] names = new String[nameTokens.countTokens()];
				for (int i = 0; i < names.length; i++) {
					names[i] = nameTokens.nextToken();
				}
				return names;
			}
			else {
				return null;
			}
		}

		@Override
		@Nullable
		public String[] getParameterNames(Constructor<?> ctor) {
			throw new UnsupportedOperationException("Spring AOP cannot handle constructor advice");
		}
	}

}
