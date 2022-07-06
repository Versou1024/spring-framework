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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Dispatcher;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.core.KotlinDetector;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * CGLIB-based {@link AopProxy} implementation for the Spring AOP framework.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} object. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>{@link DefaultAopProxyFactory} will automatically create CGLIB-based
 * proxies if necessary, for example in case of proxying a target class
 * (see the {@link DefaultAopProxyFactory attendant javadoc} for details).
 *
 * <p>Proxies created using this class are thread-safe if the underlying
 * (target) class is thread-safe.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Chris Beams
 * @author Dave Syer
 * @see org.springframework.cglib.proxy.Enhancer
 * @see AdvisedSupport#setProxyTargetClass
 * @see DefaultAopProxyFactory
 */
@SuppressWarnings("serial")
class CglibAopProxy implements AopProxy, Serializable {
	// 基于Cglib创建aop代理对象

	// CGLIB 回调数组索引的常量
	private static final int AOP_PROXY = 0;
	private static final int INVOKE_TARGET = 1;
	private static final int NO_OVERRIDE = 2;
	private static final int DISPATCH_TARGET = 3;
	private static final int DISPATCH_ADVISED = 4;
	private static final int INVOKE_EQUALS = 5;
	private static final int INVOKE_HASHCODE = 6;


	/** Logger available to subclasses; static to optimize serialization. */
	protected static final Log logger = LogFactory.getLog(CglibAopProxy.class);

	/** Keeps track of the Classes that we have validated for final methods. */
	private static final Map<Class<?>, Boolean> validatedClasses = new WeakHashMap<>();


	// 无需解释
	protected final AdvisedSupport advised;

	// 设置用于创建代理的构造函数参数。
	@Nullable
	protected Object[] constructorArgs;

	// 设置用于创建代理的构造函数参数的形参
	@Nullable
	protected Class<?>[] constructorArgTypes;

	/** Dispatcher used for methods on Advised. */
	private final transient AdvisedDispatcher advisedDispatcher;
	// Dispatcher 用于 Advised 上的方法。

	private transient Map<Method, Integer> fixedInterceptorMap = Collections.emptyMap();

	private transient int fixedInterceptorOffset;


	/**
	 * Create a new CglibAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public CglibAopProxy(AdvisedSupport config) throws AopConfigException {
		// 唯一构造器 -- 初始话CglibAopProxy
		Assert.notNull(config, "AdvisedSupport must not be null");
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		this.advised = config;
		this.advisedDispatcher = new AdvisedDispatcher(this.advised);
	}

	/**
	 * Set constructor arguments to use for creating the proxy.
	 * @param constructorArgs the constructor argument values
	 * @param constructorArgTypes the constructor argument types
	 */
	public void setConstructorArguments(@Nullable Object[] constructorArgs, @Nullable Class<?>[] constructorArgTypes) {
		// 设置用于创建代理的构造函数参数。
		if (constructorArgs == null || constructorArgTypes == null) {
			throw new IllegalArgumentException("Both 'constructorArgs' and 'constructorArgTypes' need to be specified");
		}
		if (constructorArgs.length != constructorArgTypes.length) {
			throw new IllegalArgumentException("Number of 'constructorArgs' (" + constructorArgs.length +
					") must match number of 'constructorArgTypes' (" + constructorArgTypes.length + ")");
		}
		this.constructorArgs = constructorArgs;
		this.constructorArgTypes = constructorArgTypes;
	}


	@Override
	public Object getProxy() {
		return getProxy(null);
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		// 获取基于Cglib的Proxy代理对象的核心方法
		// 它的两个getProxy()相对来说比较简单，就是使用CGLIB的方式，利用Enhancer创建了一个增强的实例
		// 这里面比较复杂的地方在：getCallbacks()这步是比较繁琐的
		// setCallbackFilter就是看看哪些方法需要拦截、哪些不需要~~~~


		if (logger.isTraceEnabled()) {
			logger.trace("Creating CGLIB proxy: " + this.advised.getTargetSource());
		}

		try {
			// 1. 获取目标的Class信息
			Class<?> rootClass = this.advised.getTargetClass();
			Assert.state(rootClass != null, "Target class must be available for creating a CGLIB proxy");

			// 2. Cglib代理的超类，就是targetClass，
			// 如果代理目标rootClass已经被是Cglib代理类，就从其中获取superClass以及interface
			// 再为其构建一个新的Cglib代理类
			Class<?> proxySuperClass = rootClass;
			if (rootClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
				proxySuperClass = rootClass.getSuperclass();
				Class<?>[] additionalInterfaces = rootClass.getInterfaces();
				for (Class<?> additionalInterface : additionalInterfaces) {
					this.advised.addInterface(additionalInterface);
				}
			}

			// 3. 验证Class,可以忽略~~就是做了一些日志打印工作
			validateClassIfNecessary(proxySuperClass, classLoader);

			// 4. 配置Cglib的Enhancer
			Enhancer enhancer = createEnhancer(); // 就是 new Enhancer();
			if (classLoader != null) {
				enhancer.setClassLoader(classLoader); // 设置ClassLoader
				if (classLoader instanceof SmartClassLoader && ((SmartClassLoader) classLoader).isClassReloadable(proxySuperClass)) {
					enhancer.setUseCache(false);
				}
			}
			enhancer.setSuperclass(proxySuperClass); // 设置需要代理的SuperClass
			// ❗️❗️❗️没有SpringProxy接口，就代理对象去添加SpringProxy接口
			// 没有Advised接口，且ProxyConfig的isOpaque标记为非透明的,就需要代理对象实现Advised接口
			// 没有DecoratingProxy接口，就需要代理对象实现DecoratingProxy接口 
			// 如何实现 -> 就是在拦截器中判断方法是否属于上述接口,是的话,就委托给可以处理的类完成
			enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised)); // 设置需要代理的interface
			// ❗️❗️❗️ cglib的命名策略
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE); // Cglib的命名策略 -- 主要是替换Tag，将"ByCGLIB" to "BySpringCGLIB"
			// ❗️❗️❗️ ClassLoader加载策略
			enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(classLoader)); //

			// 5. 获取增强配置即Callbacks
			Callback[] callbacks = getCallbacks(rootClass);
			Class<?>[] types = new Class<?>[callbacks.length];
			for (int x = 0; x < types.length; x++) {
				types[x] = callbacks[x].getClass();
			}
			// 5.1 
			// ❗️❗️❗️
			// 在上面的 getCallbacks 调用之后，fixedInterceptorMap 仅在此时填充
			enhancer.setCallbackFilter(new ProxyCallbackFilter(this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset));
			// 5.2 指定callback接口的是实现类的类型哦
			enhancer.setCallbackTypes(types);

			// Generate the proxy class and create a proxy instance.
			// 生成Proxy代理class，以及代理实例
			return createProxyClassAndInstance(enhancer, callbacks);
		}
		catch (CodeGenerationException | IllegalArgumentException ex) {
			throw new AopConfigException("Could not generate CGLIB subclass of " + this.advised.getTargetClass() +
					": Common causes of this problem include using a final class or a non-visible class",
					ex);
		}
		catch (Throwable ex) {
			// TargetSource.getTarget() failed
			throw new AopConfigException("Unexpected AOP exception", ex);
		}
	}

	protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
		// 1. 设置callbacks,以及关闭对构造器方法的拦截
		enhancer.setInterceptDuringConstruction(false);
		enhancer.setCallbacks(callbacks);
		// 2. 如果传递有构造器参数,就使用对应的有参构造器创建,否则使用无参构造器进行创建爱你
		return (this.constructorArgs != null && this.constructorArgTypes != null ?
				enhancer.create(this.constructorArgTypes, this.constructorArgs) :
				enhancer.create());
	}

	/**
	 * Creates the CGLIB {@link Enhancer}. Subclasses may wish to override this to return a custom
	 * {@link Enhancer} implementation.
	 */
	protected Enhancer createEnhancer() {
		return new Enhancer();
	}

	/**
	 * Checks to see whether the supplied {@code Class} has already been validated and
	 * validates it if not.
	 */
	private void validateClassIfNecessary(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader) {
		if (logger.isInfoEnabled()) {
			synchronized (validatedClasses) {
				// 1. 是否已经完成验证，即在validatedClasses缓存中记录过
				if (!validatedClasses.containsKey(proxySuperClass)) {
					// 1.1 验证标准：做一些日志输出
					doValidateClass(proxySuperClass, proxyClassLoader, ClassUtils.getAllInterfacesForClassAsSet(proxySuperClass));
					// 1.2 存入验证过的标识集
					validatedClasses.put(proxySuperClass, Boolean.TRUE);
				}
			}
		}
	}

	/**
	 * Checks for final methods on the given {@code Class}, as well as package-visible
	 * methods across ClassLoaders, and writes warnings to the log for each one found.
	 */
	private void doValidateClass(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader, Set<Class<?>> ifcs) {
		// 验证的内容: --
		// 代理目标类中的所有的方法
		// 		1. 非静态非私有的,但final不可继承的,记录一下info和debug日志
		// 		2. 非静态非私有的非公有的非保护的非同一个ClassLoader,记录一下debug日志
		if (proxySuperClass != Object.class) {
			Method[] methods = proxySuperClass.getDeclaredMethods();
			for (Method method : methods) {
				int mod = method.getModifiers();
				if (!Modifier.isStatic(mod) && !Modifier.isPrivate(mod)) {
					if (Modifier.isFinal(mod)) {
						if (logger.isInfoEnabled() && implementsInterface(method, ifcs)) {
							logger.info("Unable to proxy interface-implementing method [" + method + "] because " +
									"it is marked as final: Consider using interface-based JDK proxies instead!");
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Final method [" + method + "] cannot get proxied via CGLIB: " +
									"Calls to this method will NOT be routed to the target instance and " +
									"might lead to NPEs against uninitialized fields in the proxy instance.");
						}
					}
					else if (logger.isDebugEnabled() && !Modifier.isPublic(mod) && !Modifier.isProtected(mod) &&
							proxyClassLoader != null && proxySuperClass.getClassLoader() != proxyClassLoader) {
						logger.debug("Method [" + method + "] is package-visible across different ClassLoaders " +
								"and cannot get proxied via CGLIB: Declare this method as public or protected " +
								"if you need to support invocations through the proxy.");
					}
				}
			}
			// 3. 递归验证
			doValidateClass(proxySuperClass.getSuperclass(), proxyClassLoader, ifcs);
		}
	}

	private Callback[] getCallbacks(Class<?> rootClass) throws Exception {
		// 1. 是否暴露Proxy\是否冻结配置\是否静态
		boolean exposeProxy = this.advised.isExposeProxy();
		boolean isFrozen = this.advised.isFrozen();
		boolean isStatic = this.advised.getTargetSource().isStatic();

		// 2. 为Cglib代理类创建拦截增强器 -- Callback，这是最常规的，最普遍的
		// ❗️❗️❗️
		Callback aopInterceptor = new DynamicAdvisedInterceptor(this.advised);

		// 3. 以下if-else的拦截器，都是用来处理暴露Proxy，不影响原始流程
		// targetInterceptor 是不对方法做增强处理的 MethodInterceptor，直接在 intercept 调用目标对象的目标方法
		// 根据是否暴露Proxy代理对象，是否isStatic，分贝选择StaticUnadvisedExposedInterceptor\DynamicUnadvisedExposedInterceptor
		// StaticUnadvisedInterceptor\DynamicUnadvisedInterceptor
		// 其实就是简单的装饰器模式->实际的拦截通知操作是在aopInterceptor中完成的,上面的四个装饰类再次基础上提供暴露aop代理对象的能力等等
		Callback targetInterceptor;
		if (exposeProxy) {
			targetInterceptor = (isStatic ?
					new StaticUnadvisedExposedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedExposedInterceptor(this.advised.getTargetSource()));
		}
		else {
			targetInterceptor = (isStatic ?
					new StaticUnadvisedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedInterceptor(this.advised.getTargetSource()));
		}

		// 4. 不需要返回 this 的 method，可以用 StaticDispatcher 进行代理，即直接通过 target 对象调用目标方法
		Callback targetDispatcher = (isStatic ? new StaticDispatcher(this.advised.getTargetSource().getTarget()) : new SerializableNoOp());

		// 5. 主要的callbacks，包括：aopInterceptor、targetInterceptor
		Callback[] mainCallbacks = new Callback[] {
				aopInterceptor,  // 0. 常规advice -- DynamicAdvisedInterceptor
				targetInterceptor,  // 1.如果优化的话，直接引用target而不考虑advice -- StaticUnadvisedExposedInterceptor/DynamicUnadvisedExposedInterceptor/StaticUnadvisedInterceptor/DynamicUnadvisedInterceptor
				new SerializableNoOp(),  //  2.不需要增强的方法的拦截器 -- NoOp
				targetDispatcher,  // 3. 实现DecoratingProxy接口时或target为静态时,将代理类关于DecoratingProxy接口功能的实现委托路由给target去实现即可 --- StaticDispatcher
				this.advisedDispatcher, // 4. 实现Advised接口时,将代理类关于Advised接口的功能委托路由给AdvisedSupport去实现 -- AdvisedDispatcher
				new EqualsInterceptor(this.advised), // 5. 处理equals()拦截器 
				new HashCodeInterceptor(this.advised) // 6. 处理HashCode()拦截器
		};

		Callback[] callbacks;

		// 6.1 targetSource是静态的[即从targetSource中获取的对象都将是同一个]
		// 且ProxyConfig配置链被冻结，那么我们可以通过使用该方法的固定链将 AOP 调用直接发送到目标来进行一些优化。
		if (isStatic && isFrozen) {
			// 6.1.1 通过一些优化措施，将callBacks进行固定操作
			Method[] methods = rootClass.getMethods();
			Callback[] fixedCallbacks = new Callback[methods.length]; // 为每个方法创建对应的Callback的数组
			this.fixedInterceptorMap = new HashMap<>(methods.length); // 每个方法的callbak所在fixedCallbacks的数组索引位置

			// 6.1.2 这里的小内存优化（可以跳过没有建议的方法的创建）
			// 为每个方法都提前去创建它的通知拦截链
			for (int x = 0; x < methods.length; x++) {
				Method method = methods[x];
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, rootClass);
				// note: 这里将使用 FixedChainStaticTargetInterceptor 来包装为这个target的创建的擦痕
				fixedCallbacks[x] = new FixedChainStaticTargetInterceptor(chain, this.advised.getTargetSource().getTarget(), this.advised.getTargetClass());
				this.fixedInterceptorMap.put(method, x);
			}

			// Now copy both the callbacks from mainCallbacks
			// and fixedCallbacks into the callbacks array.
			callbacks = new Callback[mainCallbacks.length + fixedCallbacks.length];
			System.arraycopy(mainCallbacks, 0, callbacks, 0, mainCallbacks.length);
			System.arraycopy(fixedCallbacks, 0, callbacks, mainCallbacks.length, fixedCallbacks.length);
			this.fixedInterceptorOffset = mainCallbacks.length;
		}
		// 6.2 非静态,或非配置冻结的 -- 直接return
		else {
			callbacks = mainCallbacks;
		}
		return callbacks;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof CglibAopProxy &&
				AopProxyUtils.equalsInProxy(this.advised, ((CglibAopProxy) other).advised)));
	}

	@Override
	public int hashCode() {
		return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}


	/**
	 * Check whether the given method is declared on any of the given interfaces.
	 */
	private static boolean implementsInterface(Method method, Set<Class<?>> ifcs) {
		for (Class<?> ifc : ifcs) {
			if (ClassUtils.hasMethod(ifc, method)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Process a return value. Wraps a return of {@code this} if necessary to be the
	 * {@code proxy} and also verifies that {@code null} is not returned as a primitive.
	 */
	@Nullable
	private static Object processReturnType(Object proxy, @Nullable Object target, Method method, @Nullable Object returnValue) {
		// 和JDK处理返回值一样

		// 1. 返回值不为null，且等于target，那就应该将返回值替换为代理对象给返回回去
		if (returnValue != null && returnValue == target &&
				!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
			returnValue = proxy;
		}
		Class<?> returnType = method.getReturnType();
		// 2. 返回值为null，但是返回类型不为void,抛出异常
		if (returnValue == null && returnType != Void.TYPE && returnType.isPrimitive()) {
			throw new AopInvocationException("Null return value from advice does not match primitive return type for: " + method);
		}
		return returnValue;
	}


	/**
	 * Serializable replacement for CGLIB's NoOp interface.
	 * Public to allow use elsewhere in the framework.
	 */
	public static class SerializableNoOp implements NoOp, Serializable {
		// note: NoOp接口继承的Callback,也是一种拦截器->意味着啥也不做
		// 不需要任何拦截操作
	}


	/**
	 * Method interceptor used for static targets with no advice chain. The call
	 * is passed directly back to the target. Used when the proxy needs to be
	 * exposed and it can't be determined that the method won't return
	 * {@code this}.
	 */
	private static class StaticUnadvisedInterceptor implements MethodInterceptor, Serializable {
		// 装饰器模式
		// StaticUnadvisedInterceptor -- 静态、不增强、不暴露拦截器

		@Nullable
		private final Object target;

		public StaticUnadvisedInterceptor(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object retVal = methodProxy.invoke(this.target, args);
			return processReturnType(proxy, this.target, method, retVal);
		}
	}


	/**
	 * Method interceptor used for static targets with no advice chain, when the
	 * proxy is to be exposed.
	 */
	private static class StaticUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {
		// 装饰器模式
		// StaticUnadvisedExposedInterceptor -- 静态、不增强、可暴露的拦截器
		// 因此其intercept()中，只是对AopContext中上的ThreadLocal中的proxy进行移除和替换操作，不影响目标使用

		// 被装饰的对象, 实际的拦截工作还是交给target处理哦
		@Nullable
		private final Object target;

		public StaticUnadvisedExposedInterceptor(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			try {
				oldProxy = AopContext.setCurrentProxy(proxy);
				Object retVal = methodProxy.invoke(this.target, args);
				return processReturnType(proxy, this.target, method, retVal);
			}
			finally {
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Interceptor used to invoke a dynamic target without creating a method
	 * invocation or evaluating an advice chain. (We know there was no advice
	 * for this method.)
	 */
	private static class DynamicUnadvisedInterceptor implements MethodInterceptor, Serializable {
		// 装饰器模式
		// DynamicUnadvisedInterceptor -- 动态，不增强，不暴露拦截器
		// 相比于: 主要是额外提供将target从targetSource中释放的能力

		private final TargetSource targetSource;

		public DynamicUnadvisedInterceptor(TargetSource targetSource) {
			this.targetSource = targetSource;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object target = this.targetSource.getTarget();
			try {
				Object retVal = methodProxy.invoke(target, args);
				return processReturnType(proxy, target, method, retVal);
			}
			finally {
				if (target != null) {
					this.targetSource.releaseTarget(target);
				}
			}
		}
	}


	/**
	 * Interceptor for unadvised dynamic targets when the proxy needs exposing.
	 */
	private static class DynamicUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {
		// DynamicUnadvisedExposedInterceptor -- 动态、不增强、暴露拦截器
		// 装饰器模式: 此处扩展两个功能: 1 - 暴露aop代理对象; 2 - 需要将target从targetResource中释放调

		private final TargetSource targetSource;

		public DynamicUnadvisedExposedInterceptor(TargetSource targetSource) {
			this.targetSource = targetSource;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			Object target = this.targetSource.getTarget();
			try {
				oldProxy = AopContext.setCurrentProxy(proxy);
				Object retVal = methodProxy.invoke(target, args);
				return processReturnType(proxy, target, method, retVal);
			}
			finally {
				AopContext.setCurrentProxy(oldProxy);
				if (target != null) {
					this.targetSource.releaseTarget(target);
				}
			}
		}
	}


	/**
	 * Dispatcher for a static target. Dispatcher is much faster than
	 * interceptor. This will be used whenever it can be determined that a
	 * method definitely does not return "this"
	 */
	private static class StaticDispatcher implements Dispatcher, Serializable {
		// 注意: Dispatcher 也是实现了Callback,它的功能正如起名主要是帮助做路由: 即将代理类的方法执行路由给对方执行
		// 当代理需要实现DecoratingProxy接口时,将代理类关于DecoratingProxy接口功能的实现委托路由给target去实现即可

		@Nullable
		private final Object target;

		public StaticDispatcher(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object loadObject() {
			return this.target;
		}
	}


	/**
	 * Dispatcher for any methods declared on the Advised class.
	 */
	private static class AdvisedDispatcher implements Dispatcher, Serializable {
		// 注意: Dispatcher 也是实现了Callback,它的功能正如起名主要是帮助做路由: 即将代理类的方法执行路由给对方执行
		// 代理需要实现Advised接口时,将代理类的功能委托路由给AdvisedSupport去实现即可

		private final AdvisedSupport advised;

		public AdvisedDispatcher(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		public Object loadObject() {
			return this.advised;
		}
	}


	/**
	 * Dispatcher for the {@code equals} method.
	 * Ensures that the method call is always handled by this class.
	 */
	private static class EqualsInterceptor implements MethodInterceptor, Serializable {
		// 拦截equals()方法

		private final AdvisedSupport advised;

		public EqualsInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		// equals()方法将会当前EqualsInterceptor#intercept()拦截执行
		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
			// 1. equals()只有一个形参,如果等于proxy,就直接返回true
			Object other = args[0];
			if (proxy == other) {
				return true;
			}
			// 2. Spring的Cglib代理对象,就直接获取5号位置对应的Callback出来
			if (other instanceof Factory) {
				// 2.1 如果callback存在,且为EqualsInterceptor
				// 且持有的advised和当前运行的EqualsInterceptor相同,就返回true
				Callback callback = ((Factory) other).getCallback(INVOKE_EQUALS);
				if (!(callback instanceof EqualsInterceptor)) {
					return false;
				}
				AdvisedSupport otherAdvised = ((EqualsInterceptor) callback).advised;
				return AopProxyUtils.equalsInProxy(this.advised, otherAdvised);
			}
			else {
				return false;
			}
		}
	}


	/**
	 * Dispatcher for the {@code hashCode} method.
	 * Ensures that the method call is always handled by this class.
	 */
	private static class HashCodeInterceptor implements MethodInterceptor, Serializable {
		// 用于拦截HashCode()执行的方法

		private final AdvisedSupport advised;

		public HashCodeInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
			// CglibAopProxy代理对象的HashCode 与 目标类Target的hashCOde
			return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
		}
	}


	/**
	 * Interceptor used specifically for advised methods on a frozen, static proxy.
	 */
	private static class FixedChainStaticTargetInterceptor implements MethodInterceptor, Serializable {
		// 该拦截器专门用于: ProxyConfig是冻结的,且TargetSource是静态, 代理上的建议方法。

		private final List<Object> adviceChain;

		@Nullable
		private final Object target;

		@Nullable
		private final Class<?> targetClass;

		public FixedChainStaticTargetInterceptor(
				List<Object> adviceChain, @Nullable Object target, @Nullable Class<?> targetClass) {

			this.adviceChain = adviceChain;
			this.target = target;
			this.targetClass = targetClass;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			// 最终还是到 CglibMethodInvocation
			MethodInvocation invocation = new CglibMethodInvocation(
					proxy, this.target, method, args, this.targetClass, this.adviceChain, methodProxy);
			Object retVal = invocation.proceed();
			retVal = processReturnType(proxy, this.target, method, retVal);
			return retVal;
		}
	}


	/*
	 * 通用目的的AOP的callback,生成AOP的callback进行增强，当target是动态或没有冻结，就可以生成
	 * 所有的被代理得类的所有的方法调用，都会进入DynamicAdvisedInterceptor#intercept这个方法里面来（相当于JDK动态代理得invoke方法）
	 * 它实现了MethodInterceptor接口
	 */
	private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
		// note❗️❗️❗️: 这里的 MethodInterceptor 是继承的 Callback的
		// 不同于 MethodInterceptor extends Advice 的概念哦 -> 这个概念比较适合Jdk使用的哦
		// 主接口: 会触发--List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
		// 去获取当前执行方法的过滤器链条哦

		private final AdvisedSupport advised;

		public DynamicAdvisedInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false; // 是否向AOPContext中设置过代理对象
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
				// 1. 类似JDK
				if (this.advised.exposeProxy) {
					// 如果需要在拦截器暴露 proxy 对象，则把 proxy 对象添加到 ThreadLocal
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}
				// 2. 拿到目标对象--这里就是使用targetSource的意义，它提供多个实现类，从而实现了更多的可能性
				// 比如：SingletonTargetSource  HotSwappableTargetSource  PrototypeTargetSource  ThreadLocalTargetSource等等
				target = targetSource.getTarget();
				Class<?> targetClass = (target != null ? target.getClass() : null);
				// 3, 获取MethodInterceptor chain
				// 类似JDK创建代理的过程,也是去拿到和这个方法匹配的所有的通知增强器
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;
				// 4.1 如果拦截器集合为空，说明当前 method 不需要被增强，则通过 methodProxy 直接调用目标对象上的方法
				if (chain.isEmpty() && CglibMethodInvocation.isMethodProxyCompatible(method)) {
					// 4.1.1 我们可以跳过创建 MethodInvocation：直接调用目标。
					// 请注意，最终调用者必须是 InvokerInterceptor，因此我们知道它只对目标执行反射操作，并且没有热交换或花哨的代理
					// 对参数args进行转换 -- 处理可变参数的元素类型问题
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					try {
						// 4.1.2 直接对target的原始目标方法进行调用 -- 不需要任何拦截增强
						retVal = methodProxy.invoke(target, argsToUse);
					}
					catch (CodeGenerationException ex) {
						CglibMethodInvocation.logFastClassGenerationFailure(method);
						retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
					}
				}
				// 4.2 如果该方法有相应的拦截器需要执行
				else {
					// 4.2.1 CglibMethodInvocation这里采用的是CglibMethodInvocation，它是`ReflectiveMethodInvocation`的子类   到这里就和JDK Proxy保持一致勒
					// 创建 CglibMethodInvocation，用来管理方法拦截器责任链
					// 通过 proceed 方法驱动拦截器责任链的运行，并获取到返回值
					// ❗️一般需要拦截的话,就需要关注这里创建的CglibMethodInvocation,传递过去的形参有很多,然后接着调用啦proceed()方法继续处理哦❗️
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				// 4.2 处理返回值
				retVal = processReturnType(proxy, target, method, retVal);
				return retVal;
			}
			finally {
				// 5.1 类似JDK - 释放 + 恢复
				if (target != null && !targetSource.isStatic()) {
					targetSource.releaseTarget(target);
				}
				// 5.2 如果暴露过AOp代理对象,那就需要将之前old的代理对象set回去
				if (setProxyContext) {
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other ||
					(other instanceof DynamicAdvisedInterceptor &&
							this.advised.equals(((DynamicAdvisedInterceptor) other).advised)));
		}

		/**
		 * CGLIB uses this to drive proxy creation.
		 */
		@Override
		public int hashCode() {
			return this.advised.hashCode();
		}
	}


	/**
	 * Implementation of AOP Alliance MethodInvocation used by this AOP proxy.
	 */
	private static class CglibMethodInvocation extends ReflectiveMethodInvocation {
		// 继承了ReflectiveMethodInvocation -> ReflectiveMethodInvocation在JDK的代理对象中也有很大的作用哦

		// 对于Cglin而言需要扩展出一个methodProxy
		@Nullable
		private final MethodProxy methodProxy; 

		public CglibMethodInvocation(Object proxy, @Nullable Object target, Method method,
				Object[] arguments, @Nullable Class<?> targetClass,
				List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {

			super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);

			this.methodProxy = (isMethodProxyCompatible(method) ? methodProxy : null);
		}

		@Override
		@Nullable
		public Object proceed() throws Throwable {
			try {
				// 无变化
				return super.proceed();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				if (ReflectionUtils.declaresException(getMethod(), ex.getClass()) ||
						KotlinDetector.isKotlinType(getMethod().getDeclaringClass())) {
					// Propagate original exception if declared on the target method
					// (with callers expecting it). Always propagate it for Kotlin code
					// since checked exceptions do not have to be explicitly declared there.
					throw ex;
				}
				else {
					// Checked exception thrown in the interceptor but not declared on the
					// target method signature -> apply an UndeclaredThrowableException,
					// aligned with standard JDK dynamic proxy behavior.
					throw new UndeclaredThrowableException(ex);
				}
			}
		}

		/**
		 * Gives a marginal performance improvement versus using reflection to
		 * invoke the target when invoking public methods.
		 */
		@Override
		protected Object invokeJoinpoint() throws Throwable {
			// 主要是重写了 invokeJoinpoint() 方法
			// 在 ReflectiveMethodInvocation 中是使用的 method.invoke(target, args)
			// 而在 CglibMethodInvocation 中使用的是 methodProxy.invoke(this.target, this.arguments)
			if (this.methodProxy != null) {
				try {
					return this.methodProxy.invoke(this.target, this.arguments);
				}
				catch (CodeGenerationException ex) {
					logFastClassGenerationFailure(this.method);
				}
			}
			return super.invokeJoinpoint();
		}

		static boolean isMethodProxyCompatible(Method method) {
			// 是否为方法代理兼容
			// 公有方法，且非equals、hashcode、toString方法
			return (Modifier.isPublic(method.getModifiers()) &&
					method.getDeclaringClass() != Object.class && !AopUtils.isEqualsMethod(method) &&
					!AopUtils.isHashCodeMethod(method) && !AopUtils.isToStringMethod(method));
		}

		static void logFastClassGenerationFailure(Method method) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to generate CGLIB fast class for method: " + method);
			}
		}
	}


	/**
	 * CallbackFilter to assign Callbacks to methods.
	 */
	private static class ProxyCallbackFilter implements CallbackFilter {
		// ❗️❗️❗️代理Callback过滤器Filter
		// 目的: 为每个方法确定需要执行的
		// CallbackCallback[] mainCallbacks = new Callback[] {
		//   aopInterceptor,  // 0. 动态的Advised的拦截器 -- DynamicAdvisedInterceptor
		//   targetInterceptor,  // 1.如果优化的话，直接引用target而不考虑advice -- StaticUnadvisedExposedInterceptor/DynamicUnadvisedExposedInterceptor/StaticUnadvisedInterceptor/DynamicUnadvisedInterceptor
		//   new SerializableNoOp(),  //  2.不需要增强的方法的拦截器 -- NoOp
		//   targetDispatcher,  // 3. 实现DecoratingProxy接口时或target为静态时,将代理类关于DecoratingProxy接口功能的实现委托路由给target去实现即可 --- StaticDispatcher
		//   this.advisedDispatcher, // 4. 实现Advised接口时,将代理类关于Advised接口的功能委托路由给AdvisedSupport去实现 -- AdvisedDispatcher
		//   new EqualsInterceptor(this.advised), // 5. 处理equals()拦截器 
		//   new HashCodeInterceptor(this.advised) // 6. 处理HashCode()拦截器
		// };

		private final AdvisedSupport advised;

		// 对于静态的targetSource,以及冻结forzen的ProxyConfig,就会提前为每个方法生成对应的增强通知Callback
		// 其中value为Integer对应的就是
		private final Map<Method, Integer> fixedInterceptorMap;

		private final int fixedInterceptorOffset;

		public ProxyCallbackFilter(
				AdvisedSupport advised, Map<Method, Integer> fixedInterceptorMap, int fixedInterceptorOffset) {

			this.advised = advised;
			this.fixedInterceptorMap = fixedInterceptorMap;
			this.fixedInterceptorOffset = fixedInterceptorOffset;
		}

		/**
		 * Implementation of CallbackFilter.accept() to return the index of the
		 * callback we need.
		 * <p>The callbacks for each proxy are built up of a set of fixed callbacks
		 * for general use and then a set of callbacks that are specific to a method
		 * for use on static targets with a fixed advice chain.
		 * <p>The callback used is determined thus:
		 * <dl>
		 * <dt>For exposed proxies</dt>
		 * <dd>Exposing the proxy requires code to execute before and after the
		 * method/chain invocation. This means we must use
		 * DynamicAdvisedInterceptor, since all other interceptors can avoid the
		 * need for a try/catch block</dd>
		 * <dt>For Object.finalize():</dt>
		 * <dd>No override for this method is used.</dd>
		 * <dt>For equals():</dt>
		 * <dd>The EqualsInterceptor is used to redirect equals() calls to a
		 * special handler to this proxy.</dd>
		 * <dt>For methods on the Advised class:</dt>
		 * <dd>the AdvisedDispatcher is used to dispatch the call directly to
		 * the target</dd>
		 * <dt>For advised methods:</dt>
		 * <dd>If the target is static and the advice chain is frozen then a
		 * FixedChainStaticTargetInterceptor specific to the method is used to
		 * invoke the advice chain. Otherwise a DynamicAdvisedInterceptor is
		 * used.</dd>
		 * <dt>For non-advised methods:</dt>
		 * <dd>Where it can be determined that the method will not return {@code this}
		 * or when {@code ProxyFactory.getExposeProxy()} returns {@code false},
		 * then a Dispatcher is used. For static targets, the StaticDispatcher is used;
		 * and for dynamic targets, a DynamicUnadvisedInterceptor is used.
		 * If it possible for the method to return {@code this} then a
		 * StaticUnadvisedInterceptor is used for static targets - the
		 * DynamicUnadvisedInterceptor already considers this.</dd>
		 * </dl>
		 */
		@Override
		public int accept(Method method) {
			// 首选需要自动注入的callbacks的位置
			// 		Callback[] mainCallbacks = new Callback[] {
			//				aopInterceptor,  // 常规advice 0
			//				targetInterceptor,  //  如果优化的话，直接引用target不考虑advice 1
			//				new SerializableNoOp(),  //  不需要任何拦截操作 2
			//				targetDispatcher, //  3
			//				this.advisedDispatcher, // 4
			//				new EqualsInterceptor(this.advised), // Equlas 拦截器 5
			//				new HashCodeInterceptor(this.advised) // HashCode 拦截器 6
			//		};


			// 核心方法 -- 生成代理类的Class文件时，检查哪一个callback增强当前method
			
			// 1. 对于finalize()终结器方法
			if (AopUtils.isFinalizeMethod(method)) {
				// 调用finalize()不做代理
				logger.trace("Found finalize() method - using NO_OVERRIDE");
				return NO_OVERRIDE;
			}
			// 2. Advised 接口的方法由 AdvisedDispatcher 增强处理，即直接调用 advised 中的对应方法
			if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Method is declared on Advised interface: " + method);
				}
				return DISPATCH_ADVISED; 
			}
			// 3. equals 方法由 EqualsInterceptor 代理
			if (AopUtils.isEqualsMethod(method)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Found 'equals' method: " + method);
				}
				return INVOKE_EQUALS; // 5号callback
			}
			// 4. hashCode 方法由 HashCodeInterceptor 代理 - EqualsInterceptor
			if (AopUtils.isHashCodeMethod(method)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Found 'hashCode' method: " + method);
				}
				return INVOKE_HASHCODE; // 6号callback - HashCodeInterceptor
			}
			Class<?> targetClass = this.advised.getTargetClass();
			// 获取对应的代理chain
			// 5. chain 不为空，表示当前 method 需要被增强处理
			List<?> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
			boolean haveAdvice = !chain.isEmpty(); // 有aop通知
			boolean exposeProxy = this.advised.isExposeProxy();
			boolean isStatic = this.advised.getTargetSource().isStatic();
			boolean isFrozen = this.advised.isFrozen();

			// 6. 有aop通知，或者没有冻结配置
			if (haveAdvice || !isFrozen) {
				// 6.1 如果公开代理，让其在整个拦截器责任链种可见，则必须使用 AOP_PROXY，即 DynamicAdvisedInterceptor
				if (exposeProxy) {
					if (logger.isTraceEnabled()) {
						logger.trace("Must expose proxy on advised method: " + method);
					}
					return AOP_PROXY;
				}
				// 6.2 检查我们是否有使用固定拦截器优化处理
				// 通过 FixedChainStaticTargetInterceptor 对拦截器做特定的优化处理
				if (isStatic && isFrozen && this.fixedInterceptorMap.containsKey(method)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Method has advice and optimizations are enabled: " + method);
					}
					// 知道我们正在优化，所以我们可以使用 FixedStaticChainInterceptors。
					// FixedStaticChainInterceptors 已经提前优化好需要指定的拦截器的位置啦
					int index = this.fixedInterceptorMap.get(method);
					return (index + this.fixedInterceptorOffset);
				}
				// 6.3 
				// 有aop通知且配置被冻结,不暴露代理对象,非静态的
				// 有aop通知且配置没有被冻结,不暴露代理对象,非静态/静态
				// 无aop通知,且配置没有被冻结,不暴露代理对象
				else {
					if (logger.isTraceEnabled()) {
						logger.trace("Unable to apply any optimizations to advised method: " + method);
					}
					// 否则使用 AOP_PROXY，即 DynamicAdvisedInterceptor
					return AOP_PROXY;
				}
			}
			// 7. 没有aop通知，且已经冻结配置 -> 注意:对应的方法没有aop通知存在
			else {
				// 7.1 如果没有aop通知存在,但仍然需要暴露代理对象，或者 target 对象是动态的
				// 则通过 INVOKE_TARGET 来执行
				if (exposeProxy || !isStatic) {
					return INVOKE_TARGET;
				}
				Class<?> returnType = method.getReturnType();
				// 7.2 如果当前方法的返回类型是目标对象的类型，INVOKE_TARGET 来执行
				if (targetClass != null && returnType.isAssignableFrom(targetClass)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Method return type is assignable from target type and " +
								"may therefore return 'this' - using INVOKE_TARGET: " + method);
					}
					return INVOKE_TARGET;
				}
				// 7.3 
				else {
					if (logger.isTraceEnabled()) {
						logger.trace("Method return type ensures 'this' cannot be returned - " +
								"using DISPATCH_TARGET: " + method);
					}
					// 否则，通过 StaticDispatcher 进行代理
					return DISPATCH_TARGET;
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ProxyCallbackFilter)) {
				return false;
			}
			ProxyCallbackFilter otherCallbackFilter = (ProxyCallbackFilter) other;
			AdvisedSupport otherAdvised = otherCallbackFilter.advised;
			if (this.advised.isFrozen() != otherAdvised.isFrozen()) {
				return false;
			}
			if (this.advised.isExposeProxy() != otherAdvised.isExposeProxy()) {
				return false;
			}
			if (this.advised.getTargetSource().isStatic() != otherAdvised.getTargetSource().isStatic()) {
				return false;
			}
			if (!AopProxyUtils.equalsProxiedInterfaces(this.advised, otherAdvised)) {
				return false;
			}
			// Advice instance identity is unimportant to the proxy class:
			// All that matters is type and ordering.
			Advisor[] thisAdvisors = this.advised.getAdvisors();
			Advisor[] thatAdvisors = otherAdvised.getAdvisors();
			if (thisAdvisors.length != thatAdvisors.length) {
				return false;
			}
			for (int i = 0; i < thisAdvisors.length; i++) {
				Advisor thisAdvisor = thisAdvisors[i];
				Advisor thatAdvisor = thatAdvisors[i];
				if (!equalsAdviceClasses(thisAdvisor, thatAdvisor)) {
					return false;
				}
				if (!equalsPointcuts(thisAdvisor, thatAdvisor)) {
					return false;
				}
			}
			return true;
		}

		private static boolean equalsAdviceClasses(Advisor a, Advisor b) {
			return (a.getAdvice().getClass() == b.getAdvice().getClass());
		}

		private static boolean equalsPointcuts(Advisor a, Advisor b) {
			// If only one of the advisor (but not both) is PointcutAdvisor, then it is a mismatch.
			// Takes care of the situations where an IntroductionAdvisor is used (see SPR-3959).
			return (!(a instanceof PointcutAdvisor) ||
					(b instanceof PointcutAdvisor &&
							ObjectUtils.nullSafeEquals(((PointcutAdvisor) a).getPointcut(), ((PointcutAdvisor) b).getPointcut())));
		}

		@Override
		public int hashCode() {
			int hashCode = 0;
			Advisor[] advisors = this.advised.getAdvisors();
			for (Advisor advisor : advisors) {
				Advice advice = advisor.getAdvice();
				hashCode = 13 * hashCode + advice.getClass().hashCode();
			}
			hashCode = 13 * hashCode + (this.advised.isFrozen() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isExposeProxy() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isOptimize() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isOpaque() ? 1 : 0);
			return hashCode;
		}
	}

}
