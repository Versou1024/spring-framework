/*
 * Copyright 2002-2020 the original author or authors.
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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareParents;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.aop.aspectj.DeclareParentsAdvisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

/**
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring AspectJ's annotation syntax, using reflection to invoke the
 * corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {
	// AbstractAspectJAdvisorFactory 实现了 AspectJAdvisorFactory 的 isAspect(Class<?>)和validate(Class<?>) 两个方法
	// ReflectiveAspectJAdvisorFactory 则需要实现 AspectJAdvisorFactory 的 getAdvisor() 将Aspect切面类或者切面类下的某个切面方法
	// 转换为Spring框架的Advisor

	private static final Comparator<Method> METHOD_COMPARATOR;

	static {
		// 注意：虽然 @After 排序在 @AfterReturning 和 @AfterThrowing 之前
		// 但实际上会在 @AfterReturning 和 @AfterThrowing 方法之后调用 @After 通知方法，
		// 因为 AspectJAfterAdvice.invoke(MethodInvocation) 在 `try` 中调用了继续()块并且仅在相应的“finally”块中调用 @After 建议方法。
		// 默认的排序规则 -- Around/Before/After/AfterReturning/AfterThrowing
		Comparator<Method> adviceKindComparator = new ConvertingComparator<>(
				new InstanceComparator<>(
						Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class),
				(Converter<Method, Annotation>) method -> {
					AspectJAnnotation<?> ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
					return (ann != null ? ann.getAnnotation() : null);
				});
		Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName); // 对于注解相同的,将使用方法名进行比较排序哦
		METHOD_COMPARATOR = adviceKindComparator.thenComparing(methodNameComparator);
	}


	@Nullable
	private final BeanFactory beanFactory;


	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}.
	 */
	public ReflectiveAspectJAdvisorFactory() {
		this(null);
	}

	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
	 * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
	 * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
	 * @param beanFactory the BeanFactory to propagate (may be {@code null}}
	 * @since 4.3.6
	 * @see AspectJExpressionPointcut#setBeanFactory
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
	 */
	public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
		// 1. 从 MetadataAwareAspectInstanceFactory 中获取到 AspectJ切面实例对象,以及实例对象的元数据,切面名
		Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
		// 2. 验证 aspectClass 是否为切面类
		validate(aspectClass);

		// 3. 我们用 LazySingletonAspectInstanceFactoryDecorator 装饰器包装 MetadataAwareAspectInstanceFactory，这样它就只会实例化一次。
		MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory = new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

		List<Advisor> advisors = new ArrayList<>();
		// 4. 找到不带有@Pointcut注解的方法,并进行排序后返回
		for (Method method : getAdvisorMethods(aspectClass)) {
			// 4.1 ❗️❗️❗️
			// 将Aspect切面类中的使用Aspect注解标注的通知方法 -> 转换SpringAop框架的Advisor
			Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		// 5. advisors 不为空，并且aspectMetadata是懒加载啊【只要不是PerClauseKind.SINGLETON就报错】
		// 可忽略 ~~ 一般perThis和perTarget多例的切面类都不会被使用到哦
		if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
			advisors.add(0, instantiationAdvisor);
		}

		// 6. 查找切面类上的字段上是否有使用@DeclareParents来指令一个Advisor
		// ❗️❗️❗️
		for (Field field : aspectClass.getDeclaredFields()) {
			Advisor advisor = getDeclareParentsAdvisor(field);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		return advisors;
	}

	private List<Method> getAdvisorMethods(Class<?> aspectClass) {
		final List<Method> methods = new ArrayList<>();
		// 1. 检查所有方法，找到不带有@Pointcut注解的方法
		ReflectionUtils.doWithMethods(aspectClass, method -> {
			// Exclude pointcuts
			if (AnnotationUtils.getAnnotation(method, Pointcut.class) == null) {
				methods.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
		// 2. 对过滤出的所有方法进行sort操作
		if (methods.size() > 1) {
			methods.sort(METHOD_COMPARATOR);
		}
		return methods;
	}

	/**
	 * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
	 * for the given introduction field.
	 * <p>Resulting Advisors will need to be evaluated for targets.
	 * @param introductionField the field to introspect
	 * @return the Advisor instance, or {@code null} if not an Advisor
	 */
	@Nullable
	private Advisor getDeclareParentsAdvisor(Field introductionField) {
		
		// 1. 解析持有@DeclareParents的注解,如果没有就返回null,表示没有对应的Advisor
		DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
		if (declareParents == null) {
			return null;
		}
		
		// 2. 有使用@DeclareParents注解的时候那么defaultImpl不能是默认值,用户必须更改这块的配置
		if (DeclareParents.class == declareParents.defaultImpl()) {
			throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
		}

		// 3. 创建对应的advisor
		// 传入了 字段的类型Type\@DeclareParents的value属性\@DeclareParents的defaultImpl属性
		return new DeclareParentsAdvisor(
				introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
	}


	@Override
	@Nullable
	public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrderInAspect, String aspectName) {
		// candidateAdviceMethod 候选出来的带有AspectJ注解的通知方法；
		// aspectInstanceFactory 切面类实例以及切面类的元数据工厂；
		// declarationOrderInAspect 声明在Aspect切面类中所有的通知方法中的ordered
		// aspectName 切面类对象的名字

		// 1. 老样子,验证AspectJ切面类是否有效
		validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());

		// 2. 尝试从AspectJ切面类中将不带有@PonitCut的方法作为切入点方法，
		// 获取通知中的value即@PointCut\@Before\@After等value属性，作为expression存入AspectJExpressionPointcut，生成符合AspectJ的连接点
		AspectJExpressionPointcut expressionPointcut = getPointcut(candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
		if (expressionPointcut == null) {
			return null;
		}

		// 3. ❗️❗️❗️这个candidateAdviceMethod是AspectJ通知方法，允许生成Advisor
		// 向其中设置 连接点expressionPointcut、增强通知方法candidateAdviceMethod、AspectJ实例与元数据感知工厂aspectInstanceFactory、
		// 生命的order顺序declarationOrderInAspect、aspect切面类的名字、生成AspectJAdvisor的工厂this
		// ❗️❗️❗️note: 注意里面持有advice以及pointCut
		return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod, this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
	}

	@Nullable
	private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
		// ❗️❗️❗️
		
		// 1. candidateAdviceMethod 是 @Aspect 注解的切面类中不带有 @PointCut 的方法
		// 调用超类的 findAspectJAnnotationOnMethod 检查是否有通知注解，例如@Pointcut\@Before\@After
		AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		if (aspectJAnnotation == null) {
			// 1.1 没有通知增强注解，就别搞了
			return null;
		}
		// 2 有通知增强注解，那就把其中的value拿出来做接入点AspectJExpressionPointcut
		AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
		// 2.1 向 ajexp 注入表达式
		ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
		if (this.beanFactory != null) {
			// 2.2 向 ajexp 注入 BeanFactory
			ajexp.setBeanFactory(this.beanFactory);
		}
		return ajexp;
	}


	@Override
	@Nullable
	public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
			MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {
		// 注意:这里最终返回的是 Advice -> 而不是Advisor
		
		// 1.  老样子: 验证aspectClass
		Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		validate(candidateAspectClass);

		// 2. 查看AspectJ的相关通知注解
		AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		if (aspectJAnnotation == null) {
			return null;
		}

		// 3. 如果我们到达这里，通过上面我们知道有一个AspectJ相关通知注解的方法
		// 但是还需要确定candidateAspectClass是否有@Aspect注解哦
		if (!isAspect(candidateAspectClass)) {
			throw new AopConfigException("Advice must be declared inside an aspect type: " +
					"Offending method '" + candidateAdviceMethod + "' in class [" +
					candidateAspectClass.getName() + "]");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Found AspectJ method: " + candidateAdviceMethod);
		}

		AbstractAspectJAdvice springAdvice;

		// ❗️❗️❗️
		// 4. 根据AspectJ通知增强注解，完成转换到Advice

		// 枚举方便做switch
		switch (aspectJAnnotation.getAnnotationType()) {
			case AtPointcut: // @Pointcut注解是引用的其他切入点方法
				if (logger.isDebugEnabled()) {
					logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
				}
				return null;
			case AtAround: // @Around注解
				springAdvice = new AspectJAroundAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtBefore: // @Before注解
				springAdvice = new AspectJMethodBeforeAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfter: // @After注解
				springAdvice = new AspectJAfterAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfterReturning: // @AfterReturning注解
				springAdvice = new AspectJAfterReturningAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterReturningAnnotation.returning())) {
					// ❗️特殊关照：将@AfterReturning中的属性returning值，并调用setReturningName设置进去
					springAdvice.setReturningName(afterReturningAnnotation.returning());
				}
				break;
			case AtAfterThrowing: // @AfterThrowing注解
				springAdvice = new AspectJAfterThrowingAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
					// ❗️❗特殊关照：将@AfterThrowing的throwing值设置到进去
					springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
				}
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported advice type on method: " + candidateAdviceMethod);
		}

		// 5. 现在开始配置Aspect -> AspectName\DeclarationOrder\argNames\
		springAdvice.setAspectName(aspectName);
		springAdvice.setDeclarationOrder(declarationOrder);
		String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
		if (argNames != null) {
			// 设置参数名
			springAdvice.setArgumentNamesFromStringArray(argNames);
		}
		
		// 5.1 提前计算一下参数绑定
		springAdvice.calculateArgumentBindings();

		return springAdvice;
	}


	/**
	 * Synthetic advisor that instantiates the aspect.
	 * Triggered by per-clause pointcut on non-singleton aspect.
	 * The advice has no effect.
	 */
	@SuppressWarnings("serial")
	protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

		public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
			super(aif.getAspectMetadata().getPerClausePointcut(), (MethodBeforeAdvice)
					(method, args, target) -> aif.getAspectInstance());
		}
	}

}
