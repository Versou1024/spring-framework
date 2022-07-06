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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Before;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.aspectj.weaver.tools.PointcutParameter;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for AOP Alliance {@link org.aopalliance.aop.Advice} classes
 * wrapping an AspectJ aspect or an AspectJ-annotated advice method.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 */
@SuppressWarnings("serial")
public abstract class AbstractAspectJAdvice implements Advice, AspectJPrecedenceInformation, Serializable {
	// Aop联盟下的 AbstractAspectJAdvice 配合AspectJ做增强处理

	/**
	 * Key used in ReflectiveMethodInvocation userAttributes map for the current joinpoint.
	 */
	protected static final String JOIN_POINT_KEY = JoinPoint.class.getName();
	// org.aspectj.lang.JoinPoint


	/**
	 * Lazily instantiate joinpoint for the current invocation.
	 * Requires MethodInvocation to be bound with ExposeInvocationInterceptor.
	 * <p>Do not use if access is available to the current ReflectiveMethodInvocation
	 * (in an around advice).
	 * @return current AspectJ joinpoint, or through an exception if we're not in a
	 * Spring AOP invocation.
	 */
	public static JoinPoint currentJoinPoint() {
		// 1. 从 ExposeInvocationInterceptor 中获取执行的 MethodInvocation
		MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		ProxyMethodInvocation pmi = (ProxyMethodInvocation) mi;
		// 2. 从用户属性中根据"JOIN_POINT_KEY",获取连接点
		JoinPoint jp = (JoinPoint) pmi.getUserAttribute(JOIN_POINT_KEY);
		if (jp == null) {
			// 2.1 jp为null，表名之前没有jp，因此设置到属性中
			jp = new MethodInvocationProceedingJoinPoint(pmi);
			pmi.setUserAttribute(JOIN_POINT_KEY, jp);
		}
		return jp;
	}


	private final Class<?> declaringClass; // 通知方法所在Class

	private final String methodName; // 通知方法名

	private final Class<?>[] parameterTypes; // 通知方法形参类型

	protected transient Method aspectJAdviceMethod;

	private final AspectJExpressionPointcut pointcut; // 通知方法的表达式接入点 -- AbstractAspectJAdvice 就是用于解析AspectJ表达式的接入点

	private final AspectInstanceFactory aspectInstanceFactory; // Aspect类的实例工厂

	/**
	 * The name of the aspect (ref bean) in which this advice was defined
	 * (used when determining advice precedence so that we can determine
	 * whether two pieces of advice come from the same aspect).
	 */
	private String aspectName = "";

	/**
	 * The order of declaration of this advice within the aspect.
	 */
	private int declarationOrder;
	// 通知方法的参数名字
	@Nullable
	private String[] argumentNames; 

	// 如果在异常通知后需要绑定返回值，则为非 null。
	// 比如@AfterThrowing,就应该有一个throwingName
	@Nullable
	private String throwingName;

	// 如果在返回通知后需要绑定返回值，则为非 null。
	// 比如@AfterReturning,就应该有一个returningName
	@Nullable
	private String returningName;

	// 当returningName在形参名中时
	// discoveredReturningType就是对应的返回值形参类型
	private Class<?> discoveredReturningType = Object.class;

	// 当throwingName在形参名中时
	// discoveredThrowingType就是对应的异常形参类型
	private Class<?> discoveredThrowingType = Object.class;

	// JoinPoint 参数的索引（当前仅在索引 0 处支持，如果存在的话）。
	private int joinPointArgumentIndex = -1;

	// JoinPoint.StaticPart 参数的索引（当前仅在索引 0 处支持，如果存在的话）
	private int joinPointStaticPartArgumentIndex = -1;

	// 用于存储: 通知方法的形参名 -> 形参所在的性参数组的索引位置
	@Nullable
	private Map<String, Integer> argumentBindings;

	// 标志位: 表示通知增强上的方法的形参是否被内省完
	private boolean argumentsIntrospected = false;

	// 当returningName在形参名中时
	// discoveredReturningGenericType就是对应的带有泛型的返回值形参类型
	@Nullable
	private Type discoveredReturningGenericType;
	// Note: Unlike return type, no such generic information is needed for the throwing type,
	// since Java doesn't allow exception types to be parameterized.


	/**
	 * Create a new AbstractAspectJAdvice for the given advice method.
	 * @param aspectJAdviceMethod the AspectJ-style advice method
	 * @param pointcut the AspectJ expression pointcut
	 * @param aspectInstanceFactory the factory for aspect instances
	 */
	public AbstractAspectJAdvice(Method aspectJAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aspectInstanceFactory) {

		Assert.notNull(aspectJAdviceMethod, "Advice method must not be null");
		// @Aspect标注的切面类
		this.declaringClass = aspectJAdviceMethod.getDeclaringClass();
		// AspectJ相关通知注解的通知方法的方法名
		this.methodName = aspectJAdviceMethod.getName();
		// AspectJ相关通知注解的通知方法的形参类型
		this.parameterTypes = aspectJAdviceMethod.getParameterTypes();
		// AspectJ相关通知注解的通知方法
		this.aspectJAdviceMethod = aspectJAdviceMethod;
		// AspectJ表达式切入点
		this.pointcut = pointcut;
		// AspectJ实例工厂
		this.aspectInstanceFactory = aspectInstanceFactory;
	}


	/**
	 * Return the AspectJ-style advice method.
	 */
	public final Method getAspectJAdviceMethod() {
		return this.aspectJAdviceMethod;
	}

	/**
	 * Return the AspectJ expression pointcut.
	 */
	public final AspectJExpressionPointcut getPointcut() {
		calculateArgumentBindings();
		return this.pointcut;
	}

	/**
	 * Build a 'safe' pointcut that excludes the AspectJ advice method itself.
	 * @return a composable pointcut that builds on the original AspectJ expression pointcut
	 * @see #getPointcut()
	 */
	public final Pointcut buildSafePointcut() {
		Pointcut pc = getPointcut();
		MethodMatcher safeMethodMatcher = MethodMatchers.intersection(
				new AdviceExcludingMethodMatcher(this.aspectJAdviceMethod), pc.getMethodMatcher());
		return new ComposablePointcut(pc.getClassFilter(), safeMethodMatcher);
	}

	/**
	 * Return the factory for aspect instances.
	 */
	public final AspectInstanceFactory getAspectInstanceFactory() {
		return this.aspectInstanceFactory;
	}

	/**
	 * Return the ClassLoader for aspect instances.
	 */
	@Nullable
	public final ClassLoader getAspectClassLoader() {
		return this.aspectInstanceFactory.getAspectClassLoader();
	}

	@Override
	public int getOrder() {
		return this.aspectInstanceFactory.getOrder();
	}


	/**
	 * Set the name of the aspect (bean) in which the advice was declared.
	 */
	public void setAspectName(String name) {
		this.aspectName = name;
	}

	@Override
	public String getAspectName() {
		return this.aspectName;
	}

	/**
	 * Set the declaration order of this advice within the aspect.
	 */
	public void setDeclarationOrder(int order) {
		this.declarationOrder = order;
	}

	@Override
	public int getDeclarationOrder() {
		return this.declarationOrder;
	}

	/**
	 * Set by creator of this advice object if the argument names are known.
	 * <p>This could be for example because they have been explicitly specified in XML,
	 * or in an advice annotation.
	 * @param argNames comma delimited list of arg names
	 */
	public void setArgumentNames(String argNames) {
		String[] tokens = StringUtils.commaDelimitedListToStringArray(argNames);
		setArgumentNamesFromStringArray(tokens);
	}

	public void setArgumentNamesFromStringArray(String... args) {
		this.argumentNames = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			this.argumentNames[i] = StringUtils.trimWhitespace(args[i]);
			if (!isVariableName(this.argumentNames[i])) {
				throw new IllegalArgumentException(
						"'argumentNames' property of AbstractAspectJAdvice contains an argument name '" +
						this.argumentNames[i] + "' that is not a valid Java identifier");
			}
		}
		if (this.argumentNames != null) {
			if (this.aspectJAdviceMethod.getParameterCount() == this.argumentNames.length + 1) {
				// May need to add implicit join point arg name...
				Class<?> firstArgType = this.aspectJAdviceMethod.getParameterTypes()[0];
				if (firstArgType == JoinPoint.class ||
						firstArgType == ProceedingJoinPoint.class ||
						firstArgType == JoinPoint.StaticPart.class) {
					String[] oldNames = this.argumentNames;
					this.argumentNames = new String[oldNames.length + 1];
					this.argumentNames[0] = "THIS_JOIN_POINT";
					System.arraycopy(oldNames, 0, this.argumentNames, 1, oldNames.length);
				}
			}
		}
	}

	public void setReturningName(String name) {
		throw new UnsupportedOperationException("Only afterReturning advice can be used to bind a return value");
	}

	/**
	 * We need to hold the returning name at this level for argument binding calculations,
	 * this method allows the afterReturning advice subclass to set the name.
	 */
	protected void setReturningNameNoCheck(String name) {
		// name could be a variable or a type...
		if (isVariableName(name)) {
			this.returningName = name;
		}
		else {
			// assume a type
			try {
				this.discoveredReturningType = ClassUtils.forName(name, getAspectClassLoader());
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Returning name '" + name  +
						"' is neither a valid argument name nor the fully-qualified " +
						"name of a Java type on the classpath. Root cause: " + ex);
			}
		}
	}

	protected Class<?> getDiscoveredReturningType() {
		return this.discoveredReturningType;
	}

	@Nullable
	protected Type getDiscoveredReturningGenericType() {
		return this.discoveredReturningGenericType;
	}

	public void setThrowingName(String name) {
		throw new UnsupportedOperationException("Only afterThrowing advice can be used to bind a thrown exception");
	}

	/**
	 * We need to hold the throwing name at this level for argument binding calculations,
	 * this method allows the afterThrowing advice subclass to set the name.
	 */
	protected void setThrowingNameNoCheck(String name) {
		// name could be a variable or a type...
		if (isVariableName(name)) {
			this.throwingName = name;
		}
		else {
			// assume a type
			try {
				this.discoveredThrowingType = ClassUtils.forName(name, getAspectClassLoader());
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Throwing name '" + name  +
						"' is neither a valid argument name nor the fully-qualified " +
						"name of a Java type on the classpath. Root cause: " + ex);
			}
		}
	}

	protected Class<?> getDiscoveredThrowingType() {
		return this.discoveredThrowingType;
	}

	private boolean isVariableName(String name) {
		char[] chars = name.toCharArray();
		if (!Character.isJavaIdentifierStart(chars[0])) {
			return false;
		}
		for (int i = 1; i < chars.length; i++) {
			if (!Character.isJavaIdentifierPart(chars[i])) {
				return false;
			}
		}
		return true;
	}


	/**
	 * Do as much work as we can as part of the set-up so that argument binding
	 * on subsequent advice invocations can be as fast as possible.
	 * <p>If the first argument is of type JoinPoint or ProceedingJoinPoint then we
	 * pass a JoinPoint in that position (ProceedingJoinPoint for around advice).
	 * <p>If the first argument is of type {@code JoinPoint.StaticPart}
	 * then we pass a {@code JoinPoint.StaticPart} in that position.
	 * <p>Remaining arguments have to be bound by pointcut evaluation at
	 * a given join point. We will get back a map from argument name to
	 * value. We need to calculate which advice parameter needs to be bound
	 * to which argument name. There are multiple strategies for determining
	 * this binding, which are arranged in a ChainOfResponsibility.
	 */
	public final synchronized void calculateArgumentBindings() {
		// 1. 最简单的情况,不需要传递参数
		if (this.argumentsIntrospected || this.parameterTypes.length == 0) {
			return;
		}

		// 2. 目前还未绑定的参数数量 -- 第一个参数必须是JointPoint/ProceedJoinPoint/JoinPoint.StaticPart
		int numUnboundArgs = this.parameterTypes.length;
		Class<?>[] parameterTypes = this.aspectJAdviceMethod.getParameterTypes();
		if (maybeBindJoinPoint(parameterTypes[0]) || maybeBindProceedingJoinPoint(parameterTypes[0]) ||
				maybeBindJoinPointStaticPart(parameterTypes[0])) {
			numUnboundArgs--;
		}

		// 3. 如果还有剩下的形参,还需要继续绑定哦
		if (numUnboundArgs > 0) {
			// 3.1 需要按从切入点匹配返回的名称绑定参数
			bindArgumentsByName(numUnboundArgs);
		}

		this.argumentsIntrospected = true;
	}

	private boolean maybeBindJoinPoint(Class<?> candidateParameterType) {
		// AspectJ注解标注的通知方法的形参为JoinPoint -> 非@Around都使用简单的JoinPoint
		if (JoinPoint.class == candidateParameterType) {
			this.joinPointArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	private boolean maybeBindProceedingJoinPoint(Class<?> candidateParameterType) {
		// 	AspectJ注解标注的通知方法的形参为ProceedingJoinPoint -> 只有@Around是使用这个ProceedingJoinPoint
		if (ProceedingJoinPoint.class == candidateParameterType) {
			if (!supportsProceedingJoinPoint()) {
				throw new IllegalArgumentException("ProceedingJoinPoint is only supported for around advice");
			}
			this.joinPointArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	protected boolean supportsProceedingJoinPoint() {
		return false;
	}

	private boolean maybeBindJoinPointStaticPart(Class<?> candidateParameterType) {
		if (JoinPoint.StaticPart.class == candidateParameterType) {
			this.joinPointStaticPartArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	private void bindArgumentsByName(int numArgumentsExpectingToBind) {
		// 1. 若未加载参数名,则使用参数名发现器去通知增强方法处获取形参名数组
		if (this.argumentNames == null) {
			this.argumentNames = createParameterNameDiscoverer().getParameterNames(this.aspectJAdviceMethod);
		}
		// 2. 我们已经能够确定 arg 名称。
		if (this.argumentNames != null) {
			bindExplicitArguments(numArgumentsExpectingToBind); // 绑定显式参数
		}
		else {
			throw new IllegalStateException("Advice method [" + this.aspectJAdviceMethod.getName() + "] " +
					"requires " + numArgumentsExpectingToBind + " arguments to be bound by name, but " +
					"the argument names were not specified and could not be discovered.");
		}
	}

	/**
	 * Create a ParameterNameDiscoverer to be used for argument binding.
	 * <p>The default implementation creates a {@link DefaultParameterNameDiscoverer}
	 * and adds a specifically configured {@link AspectJAdviceParameterNameDiscoverer}.
	 */
	protected ParameterNameDiscoverer createParameterNameDiscoverer() {
		// We need to discover them, or if that fails, guess,
		// and if we can't guess with 100% accuracy, fail.
		DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
		AspectJAdviceParameterNameDiscoverer adviceParameterNameDiscoverer =
				new AspectJAdviceParameterNameDiscoverer(this.pointcut.getExpression());
		adviceParameterNameDiscoverer.setReturningName(this.returningName);
		adviceParameterNameDiscoverer.setThrowingName(this.throwingName);
		// Last in chain, so if we're called and we fail, that's bad...
		adviceParameterNameDiscoverer.setRaiseExceptions(true);
		discoverer.addDiscoverer(adviceParameterNameDiscoverer);
		return discoverer;
	}

	private void bindExplicitArguments(int numArgumentsLeftToBind) {
		Assert.state(this.argumentNames != null, "No argument names available");
		this.argumentBindings = new HashMap<>();

		int numExpectedArgumentNames = this.aspectJAdviceMethod.getParameterCount();
		if (this.argumentNames.length != numExpectedArgumentNames) {
			throw new IllegalStateException("Expecting to find " + numExpectedArgumentNames +
					" arguments to bind by name in advice, but actually found " +
					this.argumentNames.length + " arguments.");
		}
		// ❗️❗️❗️
		// Spring AOP提供使用org.aspectj.lang.JoinPoint类型获取连接点数据，
		// 任何通知方法的第一个参数都可以是JoinPoint(环绕通知是ProceedingJoinPoint，JoinPoint子类)，
		// 当然第一个参数位置也可以是JoinPoint.StaticPart类型，这个只返回连接点的静态部分。
		// JoinPoint 提供以下方法
		// toString\toShortString\toLongString\getThis\getTarget\getArgs\getSignature\gteKind\getStaticPart
		// JoinPoint.StaticPart 提供以下方法
		// getSignature\getKing\getId\getSourceLocation
		// ProceedJoinPoint 在JoinPoint的基础上扩展,用于环绕通知,使用proceed()方法来执行目标方法：

		// 1. 需要显示参数绑定的长度减去剩余需要绑定的参数
		// argumentBindings中存入key为形参名,value为形参所在通知方法的形参数组的索引
		int argumentIndexOffset = this.parameterTypes.length - numArgumentsLeftToBind;
		for (int i = argumentIndexOffset; i < this.argumentNames.length; i++) {
			this.argumentBindings.put(this.argumentNames[i], i);
		}

		// 2. 是否可以绑定返回值
		// @AfterReturning(value = "target(com.yyq.aspectJAdvanced.SmartSeller)", returning = "retVal")

		if (this.returningName != null) {
			// 2.1 returningName无法被绑定
			if (!this.argumentBindings.containsKey(this.returningName)) {
				throw new IllegalStateException("Returning argument name '" + this.returningName +
						"' was not bound in advice arguments");
			}
			// 2.2 returningName在形参名数组中存在,获取对应的索引index位置,然后拿到对应的形参类型\泛型通用类型
			else {
				Integer index = this.argumentBindings.get(this.returningName);
				this.discoveredReturningType = this.aspectJAdviceMethod.getParameterTypes()[index];
				this.discoveredReturningGenericType = this.aspectJAdviceMethod.getGenericParameterTypes()[index];
			}
		}
		// 3. 是否可以绑定异常值
		// @AfterThrowing(value = "target(com.yyq.aspectJAdvanced.SmartSeller)", throwing = "iae")
		if (this.throwingName != null) {
			// 3.1 throwingName不在形参名数组中,报错
			if (!this.argumentBindings.containsKey(this.throwingName)) {
				throw new IllegalStateException("Throwing argument name '" + this.throwingName +
						"' was not bound in advice arguments");
			}
			// 3.2 throwingName在形参名数组中,获取对应的所以index位置,拿到对对应的形参类型值
			else {
				Integer index = this.argumentBindings.get(this.throwingName);
				this.discoveredThrowingType = this.aspectJAdviceMethod.getParameterTypes()[index];
			}
		}

		// 4. 相应地配置切入点表达式
		// 自动获取：通过切入点表达式可以将相应的参数自动传递给通知方法，例如前边章节讲过的返回值和异常是如何传递给通知方法的
		// 举例:下面的param参数就来自其中
		// @Before(value="execution(* test(*)) && args(param)", argNames="param")
		// 		public void before1(String param) {
		// 			System.out.println("===param:" + param);
		// }
		// 更加复杂的情况比如
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
		configurePointcutParameters(this.argumentNames, argumentIndexOffset);
	}

	/**
	 * All parameters from argumentIndexOffset onwards are candidates for
	 * pointcut parameters - but returning and throwing vars are handled differently
	 * and must be removed from the list if present.
	 */
	private void configurePointcutParameters(String[] argumentNames, int argumentIndexOffset) {
		// 1. 如果在bindExplicitArguments已经处理过: returningName/throwingName
		// 那么 numParametersToRemove 就需要 ++
		int numParametersToRemove = argumentIndexOffset;
		if (this.returningName != null) {
			numParametersToRemove++;
		}
		if (this.throwingName != null) {
			numParametersToRemove++;
		}
		String[] pointcutParameterNames = new String[argumentNames.length - numParametersToRemove];
		Class<?>[] pointcutParameterTypes = new Class<?>[pointcutParameterNames.length];
		Class<?>[] methodParameterTypes = this.aspectJAdviceMethod.getParameterTypes();

		// 2. 开始处理
		int index = 0;
		for (int i = 0; i < argumentNames.length; i++) {
			// 2.1 跳过已经处理过的形参 -- 一般用来跳过第一个形参为JoinPoint\ProceedJoinPoint
			if (i < argumentIndexOffset) {
				continue;
			}
			// 2.2 跳过 returningName\throwingName
			if (argumentNames[i].equals(this.returningName) ||
				argumentNames[i].equals(this.throwingName)) {
				continue;
			}
			pointcutParameterNames[index] = argumentNames[i];
			pointcutParameterTypes[index] = methodParameterTypes[i];
			index++;
		}
		// 3. 向pointCut中设置剩余的参数名和参数类型 -> 因为对于非PointCut\非ReturningName\非ThrowingName的值
		// 就需要依靠 AspectJExpressionPointcut 来帮助设置
		this.pointcut.setParameterNames(pointcutParameterNames);
		this.pointcut.setParameterTypes(pointcutParameterTypes);
	}

	/**
	 * Take the arguments at the method execution join point and output a set of arguments
	 * to the advice method.
	 * @param jp the current JoinPoint
	 * @param jpMatch the join point match that matched this execution join point
	 * @param returnValue the return value from the method execution (may be null)
	 * @param ex the exception thrown by the method execution (may be null)
	 * @return the empty array if there are no arguments
	 */
	protected Object[] argBinding(JoinPoint jp, @Nullable JoinPointMatch jpMatch,
			@Nullable Object returnValue, @Nullable Throwable ex) {
		// 在方法执行连接点获取参数并将一组参数输出到通知方法。
		// 参数绑定

		// 1. 计算参数绑定 -> 已经所有参数计算完毕
		// a: 第一个参数是否为JointPoint\JoinPoint.StaticPart\ProceedJoinPoint
		// b: 如果是@AfterReturning或@AfterThrowing,就查看是否绑定了returningName和throwingName
		// c: 其余需要绑定的参数,交给AspectJExpressionPointcut后续在表达式中进行处理
		// 比如更加复杂的情况: 
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
		calculateArgumentBindings();

		// AMC start
		Object[] adviceInvocationArgs = new Object[this.parameterTypes.length];
		int numBound = 0;

		// 2.1 使用了JoinPoint/ProceedJoinPoint作为第一个形参
		if (this.joinPointArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointArgumentIndex] = jp;
			numBound++;
		}
		// 2.2 使用了JoinPoint.StaticPart作为第一个形参
		else if (this.joinPointStaticPartArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointStaticPartArgumentIndex] = jp.getStaticPart();
			numBound++;
		}

		// 2.3 
		if (!CollectionUtils.isEmpty(this.argumentBindings)) {
			// 2.3.1 匹配此执行连接点的连接点匹配后的参数
			if (jpMatch != null) {
				// 2.3.1.1 遍历匹配切入点的每组参数
				// 看看每组参数所在的形参位置,设置到adviceInvocationArgs中去
				PointcutParameter[] parameterBindings = jpMatch.getParameterBindings();
				for (PointcutParameter parameter : parameterBindings) {
					String name = parameter.getName();
					Integer index = this.argumentBindings.get(name);
					adviceInvocationArgs[index] = parameter.getBinding();
					numBound++;
				}
			}
			
			// note: ex和returnValue是需要传递进来的哦
			
			// 2.3.2 绑定返回值
			if (this.returningName != null) {
				Integer index = this.argumentBindings.get(this.returningName);
				adviceInvocationArgs[index] = returnValue;
				numBound++;
			}
			//2.3.3 绑定抛出的出异常 
			if (this.throwingName != null) {
				Integer index = this.argumentBindings.get(this.throwingName);
				adviceInvocationArgs[index] = ex;
				numBound++;
			}
		}

		// 3. 还剩余未处理的形参,报出异常
		if (numBound != this.parameterTypes.length) {
			throw new IllegalStateException("Required to bind " + this.parameterTypes.length +
					" arguments, but only bound " + numBound + " (JoinPointMatch " +
					(jpMatch == null ? "was NOT" : "WAS") + " bound in invocation)");
		}

		return adviceInvocationArgs;
	}


	/**
	 * Invoke the advice method.
	 * @param jpMatch the JoinPointMatch that matched this execution join point
	 * @param returnValue the return value from the method execution (may be null)
	 * @param ex the exception thrown by the method execution (may be null)
	 * @return the invocation result
	 * @throws Throwable in case of invocation failure
	 */
	protected Object invokeAdviceMethod(@Nullable JoinPointMatch jpMatch, @Nullable Object returnValue, @Nullable Throwable ex) throws Throwable {
		// getJoinPoint() 获取 切入点AspectJ表达式
		return invokeAdviceMethodWithGivenArgs(argBinding(getJoinPoint(), jpMatch, returnValue, ex));
	}

	// 如上所述，但在这种情况下，我们得到了连接点
	protected Object invokeAdviceMethod(JoinPoint jp, @Nullable JoinPointMatch jpMatch, @Nullable Object returnValue, @Nullable Throwable t) throws Throwable {
		// 可以发现 -> 通知方法的形参绑定是需要这里传递进来 jp\jpMatch\returnvalue\ex 四个值
		
		
		// 准备开始执行AspectJ注解标注的通知方法
		// argBinding(jp, jpMatch, returnValue, t) 完成对AspectJ注解标注的通知方法的形参的绑定
		// 主要核心是在 --> argBinding(jp, jpMatch, returnValue, t) -> 处理通知方法的形参绑定
		return invokeAdviceMethodWithGivenArgs(argBinding(jp, jpMatch, returnValue, t));
	}

	protected Object invokeAdviceMethodWithGivenArgs(Object[] args) throws Throwable {
		// 1. 开始执行AspectJ注解标注的通知方法
		Object[] actualArgs = args;
		// 2. 通知方法的形参数量为0,则actualArgs设为null
		if (this.aspectJAdviceMethod.getParameterCount() == 0) {
			actualArgs = null;
		}
		// 3. 调用增强通知方法 -- AspectJAdviceMethod
		try {
			ReflectionUtils.makeAccessible(this.aspectJAdviceMethod);
			return this.aspectJAdviceMethod.invoke(this.aspectInstanceFactory.getAspectInstance(), actualArgs);
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("Mismatch on arguments to advice method [" +
					this.aspectJAdviceMethod + "]; pointcut expression [" +
					this.pointcut.getPointcutExpression() + "]", ex);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}

	/**
	 * Overridden in around advice to return proceeding join point.
	 */
	protected JoinPoint getJoinPoint() {
		// 绑定参数时 -- 用来获取JoinPoint的
		return currentJoinPoint();
	}

	/**
	 * Get the current join point match at the join point we are being dispatched on.
	 */
	@Nullable
	protected JoinPointMatch getJoinPointMatch() {
		// 在我们被调度的连接点获取当前连接点匹配MethodInvocation
		MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		return getJoinPointMatch((ProxyMethodInvocation) mi);
	}

	// Note: We can't use JoinPointMatch.getClass().getName() as the key, since
	// Spring AOP does all the matching at a join point, and then all the invocations.
	// Under this scenario, if we just use JoinPointMatch as the key, then
	// 'last man wins' which is not what we want at all.
	// Using the expression is guaranteed to be safe, since 2 identical expressions
	// are guaranteed to bind in exactly the same way.
	@Nullable
	protected JoinPointMatch getJoinPointMatch(ProxyMethodInvocation pmi) {
		//  从pmi待拦截的方法信息中,截取出有用的JoinPointMatch切入点匹配信息是很有关的 -> 具体和参数绑定有关哦
		// 比如更加复杂的情况: 
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
		
		
		// 1. 获取AspectJ表达式
		String expression = this.pointcut.getExpression();
		// 2. 用户属性中根据expression获取出JoinPointMach 
		// note: 这里为啥可以提前取出来的原因就是, 在AspectJExpressionPointCut中做方法匹配的时候,会对AspectJ表达式进行解析
		// 期间会将回去对这个表达式expression创建一个JoinPointMatch
		return (expression != null ? (JoinPointMatch) pmi.getUserAttribute(expression) : null);
	}


	@Override
	public String toString() {
		return getClass().getName() + ": advice method [" + this.aspectJAdviceMethod + "]; " +
				"aspect name '" + this.aspectName + "'";
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


	/**
	 * MethodMatcher that excludes the specified advice method.
	 * @see AbstractAspectJAdvice#buildSafePointcut()
	 */
	private static class AdviceExcludingMethodMatcher extends StaticMethodMatcher {

		private final Method adviceMethod;

		public AdviceExcludingMethodMatcher(Method adviceMethod) {
			this.adviceMethod = adviceMethod;
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return !this.adviceMethod.equals(method);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AdviceExcludingMethodMatcher)) {
				return false;
			}
			AdviceExcludingMethodMatcher otherMm = (AdviceExcludingMethodMatcher) other;
			return this.adviceMethod.equals(otherMm.adviceMethod);
		}

		@Override
		public int hashCode() {
			return this.adviceMethod.hashCode();
		}

		@Override
		public String toString() {
			return getClass().getName() + ": " + this.adviceMethod;
		}
	}

}
