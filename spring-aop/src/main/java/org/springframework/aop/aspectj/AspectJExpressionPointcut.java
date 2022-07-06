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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.weaver.patterns.NamePattern;
import org.aspectj.weaver.reflect.ReflectionWorld.ReflectionWorldException;
import org.aspectj.weaver.reflect.ShadowMatchImpl;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.FuzzyBoolean;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.aspectj.weaver.tools.MatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParameter;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.ShadowMatch;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.autoproxy.ProxyCreationContext;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AbstractExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Spring {@link org.springframework.aop.Pointcut} implementation
 * that uses the AspectJ weaver to evaluate a pointcut expression.
 *
 * <p>The pointcut expression value is an AspectJ expression. This can
 * reference other pointcuts and use composition and other operations.
 *
 * <p>Naturally, as this is to be processed by Spring AOP's proxy-based model,
 * only method execution pointcuts are supported.
 *
 * @author Rob Harrop
 * @author Adrian Colyer
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Dave Syer
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJExpressionPointcut extends AbstractExpressionPointcut implements ClassFilter, IntroductionAwareMethodMatcher, BeanFactoryAware {
	/**
	 * 依赖于AspectJ组件的功能，完成ClassFilter类过滤、MethodMatcher方法匹配的功能； 
	 * 【注意：IntroductionAwareMethodMatcher就是MethodMatcher】
	 * 它的创建是需要有对应的 AspectJ 的aop表达式完成的，然后根据aop表达式做ClassFilter和MethodMatcher的匹配能力
	 */

	// AspectJ支持的表达式类型可以通过类org.aspectj.weaver.tools.PointcutPrimitive查看，
	// Spring AOP并没有支持所有AspectJ表达式类型，而是选择了其中最实用的一些进行了支持。具体可以查看org.springframework.aop.aspectj.AspectJExpressionPointcut。
	private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = new HashSet<>();

	static {
		// note: https://blog.csdn.net/buzhimingyue/article/details/106071059
		// Spring支持的AspectJ的那些语法：如下
		// Spring AOP支持的AspectJ表达式概览：

		//execution: 匹配方法执行的切入点。Spring AOP主要使用的切点标识符。
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.EXECUTION);		
		//args: 匹配参数是给定类型的连接点。(方法入参是给定类型的方法)
		// args匹配指定参数类型和指定参数数量的方法,与包名和类名无关。
		// args pk execution: 
		// args匹配的是 运行时 传递给方法的参数类型
		// 使用“args(参数类型列表)”匹配当前执行的方法传入的参数为指定类型的执行方法；
		// 注意是匹配传入的参数类型，不是匹配方法签名的参数类型；参数类型列表中的参数必须是类型全限定名，通配符不支持；
		// args属于动态切入点，这种切入点开销非常大，非特殊情况最好不要使用；
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.ARGS);			
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.REFERENCE);
		// this: 限制匹配是给定类型的实例的bean引用(Spring AOP proxy)的连接点。(代理类是给定类型的类的所有方法)
		// this指向代理对象 -- JDK代理时，指向接口和代理类proxy / cglib代理时，指向接口和子类(不使用proxy)。
		// 使用“this(类型全限定名)”匹配当前AOP代理对象类型的执行方法；注意是AOP代理对象的类型匹配，这样就可能包括引入接口方法也可以匹配；注意this中使用的表达式必须是类型全限定名，不支持通配符；
		// 模式 描述
		// this(cn.javass.spring.chapter6.service.IPointcutService) 当前AOP对象实现了 IPointcutService接口的任何方法
		// this(cn.javass.spring.chapter6.service.IIntroductionService) 当前AOP对象实现了 IIntroductionService接口的任何方法,也可能是引入接口
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.THIS);			
		// target: 限制匹配是给定类型的实例的目标对象(被代理对象)的连接点。(目标对象是给定类型的类的所有方法)
		// 使用“target(类型全限定名)”匹配当前目标对象类型的执行方法；注意是目标对象的类型匹配，这样就不包括引入接口也类型匹配；注意target中使用的表达式必须是类型全限定名，不支持通配符；
		// target(cn.javass.spring.chapter6.service.IPointcutService) 当前目标对象（非AOP对象）实现了 IPointcutService接口的任何方法
		// target(cn.javass.spring.chapter6.service.IIntroductionService) 当前目标对象（非AOP对象） 实现了IIntroductionService 接口的任何方法,不可能是引入接口
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.TARGET);			
		// within: 匹配在指定类型内的连接点。(即指定class的所有方法)
		// within与execution相比，粒度更大，仅能实现到包和接口、类级别。而execution可以精确到方法的返回值，参数个数、修饰符、参数类型等
		// within(cn.javass..*) cn.javass包及子包下的任何方法执行
		// within(cn.javass..IPointcutService+) cn.javass包或所有子包下IPointcutService类型及子类型的任何方法
		// within(@cn.javass..Secure *) 持有cn.javass..Secure注解的任何类型的任何方法
		// 必须是在目标对象上声明这个注解，在接口上声明的对它不起作用
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.WITHIN);
		// @annotation: 匹配连接点有给定注解的连接点。(方法上有给定注解即可)
		// 比如: 匹配带有com.chenss.anno.Chenss注解的方法
		// @Pointcut("@annotation(com.chenss.anno.Chenss)")
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ANNOTATION);
		// @within: 匹配有给定注解的类型的连接点。(class上有给定注解, 那么class的所有方法都被拦截,相比于@annotation更加粗粒度)
		// 就是匹配有指定注解的类中的所有方法个
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_WITHIN);
		// @args: 匹配实际传递的参数的运行时类型有给定的注解的连接点。(方法入参上有给定注解)
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ARGS);
		// @target: 匹配有给定注解的执行对象的class的连接点。(目标对象class上有给定注解的类的所有方法)
		// 当前目标对象实现了有指定注解的类时,那么指定注解类的所有方法都需要被代理
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_TARGET);		

	}


	private static final Log logger = LogFactory.getLog(AspectJExpressionPointcut.class);

	// 声明连接点方法的类 -> 连接点的方法比如@Before标注的方法
	// 就是切面类
	@Nullable
	private Class<?> pointcutDeclarationScope;

	private String[] pointcutParameterNames = new String[0];

	private Class<?>[] pointcutParameterTypes = new Class<?>[0];

	@Nullable
	private BeanFactory beanFactory;

	@Nullable
	private transient ClassLoader pointcutClassLoader;

	@Nullable
	private transient PointcutExpression pointcutExpression;

	private transient Map<Method, ShadowMatch> shadowMatchCache = new ConcurrentHashMap<>(32);


	/**
	 * Create a new default AspectJExpressionPointcut.
	 */
	public AspectJExpressionPointcut() {
	}

	/**
	 * Create a new AspectJExpressionPointcut with the given settings.
	 * @param declarationScope the declaration scope for the pointcut
	 * @param paramNames the parameter names for the pointcut
	 * @param paramTypes the parameter types for the pointcut
	 */
	public AspectJExpressionPointcut(Class<?> declarationScope, String[] paramNames, Class<?>[] paramTypes) {
		// 1. declarationScope
		this.pointcutDeclarationScope = declarationScope;
		if (paramNames.length != paramTypes.length) {
			throw new IllegalStateException(
					"Number of pointcut parameter names must match number of pointcut parameter types");
		}
		// 2. 切入点的参数名称
		this.pointcutParameterNames = paramNames;
		// 3. 切入点的参数类型
		this.pointcutParameterTypes = paramTypes;
	}


	/**
	 * Set the declaration scope for the pointcut.
	 */
	public void setPointcutDeclarationScope(Class<?> pointcutDeclarationScope) {
		this.pointcutDeclarationScope = pointcutDeclarationScope;
	}

	/**
	 * Set the parameter names for the pointcut.
	 */
	public void setParameterNames(String... names) {
		this.pointcutParameterNames = names;
	}

	/**
	 * Set the parameter types for the pointcut.
	 */
	public void setParameterTypes(Class<?>... types) {
		this.pointcutParameterTypes = types;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public ClassFilter getClassFilter() {
		// 检查 pointcutExpression 是否存在，并且构建
		obtainPointcutExpression();
		return this;
	}

	@Override
	public MethodMatcher getMethodMatcher() {
		obtainPointcutExpression();
		return this;
	}


	/**
	 * Check whether this pointcut is ready to match,
	 * lazily building the underlying AspectJ pointcut expression.
	 */
	private PointcutExpression obtainPointcutExpression() {
		// 1. 表达式为空,抛出异常
		if (getExpression() == null) {
			throw new IllegalStateException("Must set property 'expression' before attempting to match");
		}
		// 2. 加载AspectJ表达式 -- PointcutExpression
		if (this.pointcutExpression == null) {
			this.pointcutClassLoader = determinePointcutClassLoader();
			this.pointcutExpression = buildPointcutExpression(this.pointcutClassLoader);
		}
		// 3. 返回AspectJ表达式
		return this.pointcutExpression;
	}

	/**
	 * Determine the ClassLoader to use for pointcut evaluation.
	 */
	@Nullable
	private ClassLoader determinePointcutClassLoader() {
		// 确定使用的ClassLoader,尝试从 BeanFactory - 切面类 - 默认的ClassLoader 中依次获取
		
		if (this.beanFactory instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) this.beanFactory).getBeanClassLoader();
		}
		if (this.pointcutDeclarationScope != null) {
			return this.pointcutDeclarationScope.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Build the underlying AspectJ pointcut expression.
	 */
	private PointcutExpression buildPointcutExpression(@Nullable ClassLoader classLoader) {
		// 1. 构建 PointcutParser 解析器 -> 涉及AspectJ的支持
		PointcutParser parser = initializePointcutParser(classLoader);
		// 2. 需要从 切入点表达式中 解析出对应的 切入点形参 -> 因此构建同等形参数组大小的PointcutParameter
		// 举例更加复杂的情况: 
		// @Before(args(param) && target(bean) && @annotation(secure)",   
		//        argNames="jp,param,bean,secure")  
		// public void before5(JoinPoint jp, String param, IPointcutService pointcutService, Secure secure) {  
		// ……  
		// }  
		// 等价于
		// @Before(args(java.lang.String) && target(xx.yy.IPointcutService) && @annotation(java.xx.secure)",   
		//        argNames="jp,param,bean,secure")  
		// public void before5(JoinPoint jp, String param, IPointcutService pointcutService, Secure secure) {  
		// ……  
		// }
		PointcutParameter[] pointcutParameters = new PointcutParameter[this.pointcutParameterNames.length];
		// 3. 创建 PointcutParameterImpl
		for (int i = 0; i < pointcutParameters.length; i++) {
			pointcutParameters[i] = parser.createPointcutParameter(
					this.pointcutParameterNames[i], this.pointcutParameterTypes[i]);
		}
		return parser.parsePointcutExpression(replaceBooleanOperators(resolveExpression()),
				this.pointcutDeclarationScope, pointcutParameters);
	}

	private String resolveExpression() {
		String expression = getExpression();
		Assert.state(expression != null, "No expression set");
		return expression;
	}

	/**
	 * Initialize the underlying AspectJ pointcut parser.
	 */
	private PointcutParser initializePointcutParser(@Nullable ClassLoader classLoader) {
		// 1. 准备AspectJ的表达式解析器 -- classLoader、支持的aspect类型
		PointcutParser parser = PointcutParser.getPointcutParserSupportingSpecifiedPrimitivesAndUsingSpecifiedClassLoaderForResolution(SUPPORTED_PRIMITIVES, classLoader);
		// 2. 初始化一个Pointcut的解析器。我们发现最后一行，新注册了一个BeanPointcutDesignatorHandler  
		// 它是允许处理Spring自己支持的bean() 的切点表达式的
		// 比如
		// @PointCut(value="bean(*Service)")
		// 匹配所有以Service命名（id或name）结尾的Bean
		parser.registerPointcutDesignatorHandler(new BeanPointcutDesignatorHandler());
		return parser;
	}


	/**
	 * If a pointcut expression has been specified in XML, the user cannot
	 * write {@code and} as "&&" (though &amp;&amp; will work).
	 * We also allow {@code and} between two pointcut sub-expressions.
	 * <p>This method converts back to {@code &&} for the AspectJ pointcut parser.
	 */
	private String replaceBooleanOperators(String pcExpr) {
		// 将Spring中的AspectJ表达式的and\or\not适配为最终的AspectJ框架的&& || !
		String result = StringUtils.replace(pcExpr, " and ", " && ");
		result = StringUtils.replace(result, " or ", " || ");
		result = StringUtils.replace(result, " not ", " ! ");
		return result;
	}


	/**
	 * Return the underlying AspectJ pointcut expression.
	 */
	public PointcutExpression getPointcutExpression() {
		return obtainPointcutExpression();
	}

	// ClassFilter
	@Override
	public boolean matches(Class<?> targetClass) {
		// 使用PointCutExpression.couldMatchJoinPointsInType(targetClass)检查类是否匹配
		PointcutExpression pointcutExpression = obtainPointcutExpression();
		try {
			try {
				return pointcutExpression.couldMatchJoinPointsInType(targetClass);
			}
			catch (ReflectionWorldException ex) {
				logger.debug("PointcutExpression matching rejected target class - trying fallback expression", ex);
				// Actually this is still a "maybe" - treat the pointcut as dynamic if we don't know enough yet
				PointcutExpression fallbackExpression = getFallbackPointcutExpression(targetClass);
				if (fallbackExpression != null) {
					return fallbackExpression.couldMatchJoinPointsInType(targetClass);
				}
			}
		}
		catch (Throwable ex) {
			logger.debug("PointcutExpression matching rejected target class", ex);
		}
		return false;
	}

	// 方法匹配
	@Override
	public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
		obtainPointcutExpression();
		ShadowMatch shadowMatch = getTargetShadowMatch(method, targetClass);
		
		// 在 Spring 中对 this、target、@this、@target、@annotation 进行特殊处理——我们可以优化，因为我们知道我们完全有这个类，并且在运行时永远不会有匹配的子类。
		
		// 1.1 永远匹配
		if (shadowMatch.alwaysMatches()) {
			return true;
		}
		// 1.2 永远不匹配
		else if (shadowMatch.neverMatches()) {
			return false;
		}
		else {
			// 1.3 可能匹配 -> 动态匹配的情况下
			if (hasIntroductions) {
				return true;
			}
			// A match test returned maybe - if there are any subtype sensitive variables
			// involved in the test (this, target, at_this, at_target, at_annotation) then
			// we say this is not a match as in Spring there will never be a different
			// runtime subtype.
			RuntimeTestWalker walker = getRuntimeTestWalker(shadowMatch);
			return (!walker.testsSubtypeSensitiveVars() || walker.testTargetInstanceOfResidue(targetClass));
		}
	}

	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		// MethodMatcher 匹配方法，借助的PointcutExpression和ShadowMatch去匹配的
		return matches(method, targetClass, false);
	}

	@Override
	public boolean isRuntime() {
		// 是否需要动态匹配
		// 比如AspectJ表达式使用啦 -- args或者@Args
		return obtainPointcutExpression().mayNeedDynamicTest();
	}

	@Override
	public boolean matches(Method method, Class<?> targetClass, Object... args) {
		obtainPointcutExpression();
		ShadowMatch shadowMatch = getTargetShadowMatch(method, targetClass);

		// Bind Spring AOP proxy to AspectJ "this" and Spring AOP target to AspectJ target,
		// consistent with return of MethodInvocationProceedingJoinPoint
		ProxyMethodInvocation pmi = null;
		Object targetObject = null;
		Object thisObject = null;
		try {
			MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
			targetObject = mi.getThis();
			if (!(mi instanceof ProxyMethodInvocation)) {
				throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
			}
			pmi = (ProxyMethodInvocation) mi;
			thisObject = pmi.getProxy();
		}
		catch (IllegalStateException ex) {
			// No current invocation...
			if (logger.isDebugEnabled()) {
				logger.debug("Could not access current invocation - matching with limited context: " + ex);
			}
		}

		try {
			JoinPointMatch joinPointMatch = shadowMatch.matchesJoinPoint(thisObject, targetObject, args);

			/*
			 * Do a final check to see if any this(TYPE) kind of residue match. For
			 * this purpose, we use the original method's (proxy method's) shadow to
			 * ensure that 'this' is correctly checked against. Without this check,
			 * we get incorrect match on this(TYPE) where TYPE matches the target
			 * type but not 'this' (as would be the case of JDK dynamic proxies).
			 * <p>See SPR-2979 for the original bug.
			 */
			if (pmi != null && thisObject != null) {  // there is a current invocation
				RuntimeTestWalker originalMethodResidueTest = getRuntimeTestWalker(getShadowMatch(method, method));
				if (!originalMethodResidueTest.testThisInstanceOfResidue(thisObject.getClass())) {
					return false;
				}
				if (joinPointMatch.matches()) {
					bindParameters(pmi, joinPointMatch);
				}
			}

			return joinPointMatch.matches();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to evaluate join point for arguments " + Arrays.asList(args) +
						" - falling back to non-match", ex);
			}
			return false;
		}
	}

	@Nullable
	protected String getCurrentProxiedBeanName() {
		return ProxyCreationContext.getCurrentProxiedBeanName();
	}


	/**
	 * Get a new pointcut expression based on a target class's loader rather than the default.
	 */
	@Nullable
	private PointcutExpression getFallbackPointcutExpression(Class<?> targetClass) {
		try {
			ClassLoader classLoader = targetClass.getClassLoader();
			if (classLoader != null && classLoader != this.pointcutClassLoader) {
				return buildPointcutExpression(classLoader);
			}
		}
		catch (Throwable ex) {
			logger.debug("Failed to create fallback PointcutExpression", ex);
		}
		return null;
	}

	private RuntimeTestWalker getRuntimeTestWalker(ShadowMatch shadowMatch) {
		if (shadowMatch instanceof DefensiveShadowMatch) {
			return new RuntimeTestWalker(((DefensiveShadowMatch) shadowMatch).primary);
		}
		return new RuntimeTestWalker(shadowMatch);
	}

	private void bindParameters(ProxyMethodInvocation invocation, JoinPointMatch jpm) {
		// Note: Can't use JoinPointMatch.getClass().getName() as the key, since
		// Spring AOP does all the matching at a join point, and then all the invocations
		// under this scenario, if we just use JoinPointMatch as the key, then
		// 'last man wins' which is not what we want at all.
		// Using the expression is guaranteed to be safe, since 2 identical expressions
		// are guaranteed to bind in exactly the same way.
		invocation.setUserAttribute(resolveExpression(), jpm);
	}

	private ShadowMatch getTargetShadowMatch(Method method, Class<?> targetClass) {
		Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
		if (targetMethod.getDeclaringClass().isInterface()) {
			// Try to build the most specific interface possible for inherited methods to be
			// considered for sub-interface matches as well, in particular for proxy classes.
			// Note: AspectJ is only going to take Method.getDeclaringClass() into account.
			Set<Class<?>> ifcs = ClassUtils.getAllInterfacesForClassAsSet(targetClass);
			if (ifcs.size() > 1) {
				try {
					Class<?> compositeInterface = ClassUtils.createCompositeInterface(
							ClassUtils.toClassArray(ifcs), targetClass.getClassLoader());
					targetMethod = ClassUtils.getMostSpecificMethod(targetMethod, compositeInterface);
				}
				catch (IllegalArgumentException ex) {
					// Implemented interfaces probably expose conflicting method signatures...
					// Proceed with original target method.
				}
			}
		}
		return getShadowMatch(targetMethod, method);
	}

	private ShadowMatch getShadowMatch(Method targetMethod, Method originalMethod) {
		// Avoid lock contention for known Methods through concurrent access...
		ShadowMatch shadowMatch = this.shadowMatchCache.get(targetMethod);
		if (shadowMatch == null) {
			synchronized (this.shadowMatchCache) {
				// Not found - now check again with full lock...
				PointcutExpression fallbackExpression = null;
				shadowMatch = this.shadowMatchCache.get(targetMethod);
				if (shadowMatch == null) {
					Method methodToMatch = targetMethod;
					try {
						try {
							shadowMatch = obtainPointcutExpression().matchesMethodExecution(methodToMatch);
						}
						catch (ReflectionWorldException ex) {
							// Failed to introspect target method, probably because it has been loaded
							// in a special ClassLoader. Let's try the declaring ClassLoader instead...
							try {
								fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
								if (fallbackExpression != null) {
									shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
								}
							}
							catch (ReflectionWorldException ex2) {
								fallbackExpression = null;
							}
						}
						if (targetMethod != originalMethod && (shadowMatch == null ||
								(shadowMatch.neverMatches() && Proxy.isProxyClass(targetMethod.getDeclaringClass())))) {
							// Fall back to the plain original method in case of no resolvable match or a
							// negative match on a proxy class (which doesn't carry any annotations on its
							// redeclared methods).
							methodToMatch = originalMethod;
							try {
								shadowMatch = obtainPointcutExpression().matchesMethodExecution(methodToMatch);
							}
							catch (ReflectionWorldException ex) {
								// Could neither introspect the target class nor the proxy class ->
								// let's try the original method's declaring class before we give up...
								try {
									fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
									if (fallbackExpression != null) {
										shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
									}
								}
								catch (ReflectionWorldException ex2) {
									fallbackExpression = null;
								}
							}
						}
					}
					catch (Throwable ex) {
						// Possibly AspectJ 1.8.10 encountering an invalid signature
						logger.debug("PointcutExpression matching rejected target method", ex);
						fallbackExpression = null;
					}
					if (shadowMatch == null) {
						shadowMatch = new ShadowMatchImpl(org.aspectj.util.FuzzyBoolean.NO, null, null, null);
					}
					else if (shadowMatch.maybeMatches() && fallbackExpression != null) {
						shadowMatch = new DefensiveShadowMatch(shadowMatch,
								fallbackExpression.matchesMethodExecution(methodToMatch));
					}
					this.shadowMatchCache.put(targetMethod, shadowMatch);
				}
			}
		}
		return shadowMatch;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AspectJExpressionPointcut)) {
			return false;
		}
		AspectJExpressionPointcut otherPc = (AspectJExpressionPointcut) other;
		return ObjectUtils.nullSafeEquals(this.getExpression(), otherPc.getExpression()) &&
				ObjectUtils.nullSafeEquals(this.pointcutDeclarationScope, otherPc.pointcutDeclarationScope) &&
				ObjectUtils.nullSafeEquals(this.pointcutParameterNames, otherPc.pointcutParameterNames) &&
				ObjectUtils.nullSafeEquals(this.pointcutParameterTypes, otherPc.pointcutParameterTypes);
	}

	@Override
	public int hashCode() {
		int hashCode = ObjectUtils.nullSafeHashCode(this.getExpression());
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutDeclarationScope);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutParameterNames);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutParameterTypes);
		return hashCode;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("AspectJExpressionPointcut: (");
		for (int i = 0; i < this.pointcutParameterTypes.length; i++) {
			sb.append(this.pointcutParameterTypes[i].getName());
			sb.append(" ");
			sb.append(this.pointcutParameterNames[i]);
			if ((i+1) < this.pointcutParameterTypes.length) {
				sb.append(", ");
			}
		}
		sb.append(") ");
		if (getExpression() != null) {
			sb.append(getExpression());
		}
		else {
			sb.append("<pointcut expression not set>");
		}
		return sb.toString();
	}

	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization, just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		// pointcutExpression will be initialized lazily by checkReadyToMatch()
		this.shadowMatchCache = new ConcurrentHashMap<>(32);
	}


	/**
	 * Handler for the Spring-specific {@code bean()} pointcut designator
	 * extension to AspectJ.
	 * <p>This handler must be added to each pointcut object that needs to
	 * handle the {@code bean()} PCD. Matching context is obtained
	 * automatically by examining a thread local variable and therefore a matching
	 * context need not be set on the pointcut.
	 */
	private class BeanPointcutDesignatorHandler implements PointcutDesignatorHandler {

		private static final String BEAN_DESIGNATOR_NAME = "bean";

		@Override
		public String getDesignatorName() {
			return BEAN_DESIGNATOR_NAME;
		}

		@Override
		public ContextBasedMatcher parse(String expression) {
			return new BeanContextMatcher(expression);
		}
	}


	/**
	 * Matcher class for the BeanNamePointcutDesignatorHandler.
	 * <p>Dynamic match tests for this matcher always return true,
	 * since the matching decision is made at the proxy creation time.
	 * For static match tests, this matcher abstains to allow the overall
	 * pointcut to match even when negation is used with the bean() pointcut.
	 */
	private class BeanContextMatcher implements ContextBasedMatcher {

		private final NamePattern expressionPattern;

		public BeanContextMatcher(String expression) {
			this.expressionPattern = new NamePattern(expression);
		}

		@Override
		@SuppressWarnings("rawtypes")
		@Deprecated
		public boolean couldMatchJoinPointsInType(Class someClass) {
			return (contextMatch(someClass) == FuzzyBoolean.YES);
		}

		@Override
		@SuppressWarnings("rawtypes")
		@Deprecated
		public boolean couldMatchJoinPointsInType(Class someClass, MatchingContext context) {
			return (contextMatch(someClass) == FuzzyBoolean.YES);
		}

		@Override
		public boolean matchesDynamically(MatchingContext context) {
			return true;
		}

		@Override
		public FuzzyBoolean matchesStatically(MatchingContext context) {
			return contextMatch(null);
		}

		@Override
		public boolean mayNeedDynamicTest() {
			return false;
		}

		private FuzzyBoolean contextMatch(@Nullable Class<?> targetType) {
			String advisedBeanName = getCurrentProxiedBeanName();
			if (advisedBeanName == null) {  // no proxy creation in progress
				// abstain; can't return YES, since that will make pointcut with negation fail
				return FuzzyBoolean.MAYBE;
			}
			if (BeanFactoryUtils.isGeneratedBeanName(advisedBeanName)) {
				return FuzzyBoolean.NO;
			}
			if (targetType != null) {
				boolean isFactory = FactoryBean.class.isAssignableFrom(targetType);
				return FuzzyBoolean.fromBoolean(
						matchesBean(isFactory ? BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName : advisedBeanName));
			}
			else {
				return FuzzyBoolean.fromBoolean(matchesBean(advisedBeanName) ||
						matchesBean(BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName));
			}
		}

		private boolean matchesBean(String advisedBeanName) {
			return BeanFactoryAnnotationUtils.isQualifierMatch(
					this.expressionPattern::matches, advisedBeanName, beanFactory);
		}
	}


	private static class DefensiveShadowMatch implements ShadowMatch {

		private final ShadowMatch primary;

		private final ShadowMatch other;

		public DefensiveShadowMatch(ShadowMatch primary, ShadowMatch other) {
			this.primary = primary;
			this.other = other;
		}

		@Override
		public boolean alwaysMatches() {
			return this.primary.alwaysMatches();
		}

		@Override
		public boolean maybeMatches() {
			return this.primary.maybeMatches();
		}

		@Override
		public boolean neverMatches() {
			return this.primary.neverMatches();
		}

		@Override
		public JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args) {
			try {
				return this.primary.matchesJoinPoint(thisObject, targetObject, args);
			}
			catch (ReflectionWorldException ex) {
				return this.other.matchesJoinPoint(thisObject, targetObject, args);
			}
		}

		@Override
		public void setMatchingContext(MatchingContext aMatchContext) {
			this.primary.setMatchingContext(aMatchContext);
			this.other.setMatchingContext(aMatchContext);
		}
	}

}
