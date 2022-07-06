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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {
	/*
	 * ❗️❗️❗️❗️❗️❗️
	 * 创建Jdk动态代理，没啥好说，直接看方法吧
	 */

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	/*
	 * NOTE: We could avoid the code duplication between this class and the CGLIB
	 * proxies by refactoring "invoke" into a template method. However, this approach
	 * adds at least 10% performance overhead versus a copy-paste solution, so we sacrifice
	 * elegance for performance. (We have a good test suite to ensure that the different
	 * proxies behave the same :-)
	 * This way, we can also more easily take advantage of minor optimizations in each class.
	 */

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/** Config used to configure this proxy. */
	private final AdvisedSupport advised; /** 这里就保存这个AOP代理所有的配置信息  包括所有的增强器等等 */

	// 标记equals方法和hashCode方法是否定义在了接口上=====

	/**
	 * Is the {@link #equals} method defined on the proxied interfaces?
	 */
	private boolean equalsDefined;

	/**
	 * Is the {@link #hashCode} method defined on the proxied interfaces?
	 */
	private boolean hashCodeDefined;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		// 内部再校验一次：必须有至少一个增强器和非空的目标实例才行
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		this.advised = config;
	}


	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		// ❗️❗️❗️
		// 获取暴露的接口 -- 这个方法可以探究一下
		// 这一个步骤很重要，就是去找接口 我们看到最终代理的接口就是这里返回的所有接口们（除了我们自己的接口，还有Spring默认的一些接口）  大致过程如下：
		//1、获取目标对象自己实现的接口们(最终肯定都会被代理的)
		//2、是否添加`SpringProxy`这个接口：目标对象实现过就不添加了，没实现过就添加true --> SpringProxy接口的作用就是标记Spring创建的代理类
		//3、是否新增`Advised`接口，注意不是Advice通知接口。 实现过就不实现了，没实现过并且advised.isOpaque()=false就添加（默认是会添加的）
		//4、是否新增DecoratingProxy接口。传入的参数decoratingProxy为true，并且没实现过就添加（显然这里，首次进来是会添加的）
		//5、代理类的接口一共是目标对象的接口+上面三个接口SpringProxy、Advised、DecoratingProxy（SpringProxy是个标记接口而已，其余的接口都有对应的方法的）
		// DecoratingProxy 这个接口Spring4.3后才提供主要能够从中获取代理的最终的目标类
		Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
		// 查找定义的Equals以及HashCode方法
		findDefinedEqualsAndHashCodeMethods(proxiedInterfaces);
		// 很简单，直接调用Proxy.newProxyInstance
		// 第三个参数传的this，处理器就是自己嘛   到此一个代理对象就此new出来啦
		return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
	}

	/**
	 * Finds any {@link #equals} or {@link #hashCode} method that may be defined
	 * on the supplied set of interfaces.
	 * @param proxiedInterfaces the interfaces to introspect
	 */
	private void findDefinedEqualsAndHashCodeMethods(Class<?>[] proxiedInterfaces) {
		
		// 1.找找看看接口里有没有自己定义equals方法和hashCode方法，这个很重要  然后标记一下
		// 注意此处用的是getDeclaredMethods，只会找自己的
		for (Class<?> proxiedInterface : proxiedInterfaces) {
			Method[] methods = proxiedInterface.getDeclaredMethods();
			for (Method method : methods) {
				if (AopUtils.isEqualsMethod(method)) {
					// 有equals方法被代理
					this.equalsDefined = true;
				}
				if (AopUtils.isHashCodeMethod(method)) {
					// 有hashCode方法被代理
					this.hashCodeDefined = true;
				}
				// 提前结束循环
				// 小技巧：两个都找到了 就没必要继续循环勒
				if (this.equalsDefined && this.hashCodeDefined) {
					return;
				}
			}
		}
	}


	/**
	 * Implementation of {@code InvocationHandler.invoke}.
	 * <p>Callers will see exactly the exception thrown by the target,
	 * unless a hook method throws an exception.
	 */
	@Override
	@Nullable
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// ❗️❗️❗️
		// 对于这部分代码和采用CGLIB的大部分逻辑都是一样的，Spring对此的解释很有意思：
		// 本来是可以抽取出来的，使得代码看起来更优雅。但是因为此会带来10%得性能损耗，所以Spring最终采用了粘贴复制的方式各用一份
		// Spring说它提供了基础的套件，来保证两个的执行行为是一致的。
		// proxy:指的是我们所代理的那个真实的对象； method:指的是我们所代理的那个真实对象的某个方法的Method对象  args:指的是调用那个真实对象方法的参数。

		// 此处重点分析一下此方法，这样在CGLIB的时候，就可以一带而过了~~~因为大致逻辑是一样的
		// InvocationHandler#invoke 方法

		Object oldProxy = null;
		boolean setProxyContext = false;


		// 1. 进入invoke方法后，最终操作的是targetSource对象
		// 因为InvocationHandler持久的就是targetSource，最终通过getTarget拿到目标对象
		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			// 2. “通常情况”Spring AOP不会对equals、hashCode方法进行拦截增强,所以此处做了处理
			// equalsDefined为false（表示自己的接口中没有定义过equals方法）- 那就交给代理去处理
			// hashCode同理，只要你自己没有实现过此方法，那就交给代理吧
			// 需要注意的是：这里统一指的是，如果接口上有此方法，但是你自己并没有实现equals和hashCode方法，那就走AOP这里的实现
			// 如果接口上没有定义此方法，只是实现类里自己@Override了HashCode，那是无效的，就是普通执行吧
			if (!this.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// 2.1 target没有equals方法,但目前执行的就是equals方法
				// 那就是走JDKDynamicAopProxy的equals()逻辑 -> 必须要对方args[0]也是JDK代理,并且代理中的targetSource\proxiedInterface代理接口\advisor使用的增强器一致
				// 就返回true
				return equals(args[0]);
			}
			else if (!this.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// 2.2 target没有hashCode方法，但目前执行的就是hashCode()方法
				// 那就是走JDKDynamicAopProxy的hashCode逻辑
				return hashCode();
			}
			// 3. ❗️❗️❗️下面两段做了很有意思的处理：DecoratingProxy和Advised接口的方法 -- 都是是最终调用了AdvisedSupport的方法去执行
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// 3.1 DecoratingProxy 只有一行方法,那就返回返回代理的最最里面的目标对象
				// 由于只有一个方法,因此不需要判断方法是啥,直接去通过AdvisedSupport获取最终的targetClass吧
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}
			else if (!this.advised.opaque && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				// 3.2 Advised接口下的方法都是委托为advised执行的哦 -- invokeJoinpointUsingReflection即使很简单的对AdvisedSupport做一个反射处理而已哦
				//  method.invoke(target, args);
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}
			
			// 执行到这 -- 就是就需要注意篇

			Object retVal; // 这个是最终该方法的返回值~~~~

			// 4. 是否暴露代理对象，默认false可配置为true，如果暴露就意味着允许在线程内共享代理对象，
			// 注意这是在线程内，也就是说同一线程的任意地方都能通过AopContext获取该代理对象，这应该算是比较高级一点的用法了。
			// 这里缓存一份代理对象在oldProxy里~~~后面有用
			if (this.advised.exposeProxy) {
				// Make invocation available if necessary.
				// 是否暴露代理 -- 如果需要暴露，会直接绑定到该线程的ThreadLocal上
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// 5.通过目标源targetSource获取目标对象 (此处Spring建议获取目标对象靠后获取  而不是放在上面)
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			// 6. ❗️❗️❗️ 获取当前被代理的method上需要执行的过滤器链 interception chain
			// 这里的拦截器可以MethodInterceptor也可以是动态的InterceptorAndDynamicMethodMatcher的
			// 获取作用在这个方法上的所有拦截器链~~~  参见DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice方法
			// 其中: 一些前置通知[MethodBeforeAdvice], 后置通知[AfterReturningAdvice], 异常通知接口[ThrowsAdvice]几个接口,必须适配为相应的MethodInterceptor
			// 就需要适配为相应的 前置通知适配器MethodBeforeAdviceAdapter / 后置通知适配器AfterReturningAdviceAdapter / 异常通知适配器ThrowsAdviceAdapter
			// 会根据切点表达式去匹配这个方法。因此其实每个方法都会进入这里，只是有很多方法得chain是Empty而已
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// 7.1 检查当前执行的方法是否有任何增强通知advice。这样做我们可以回退到目标的直接反射调用，并避免创建 MethodInvocation。
			if (chain.isEmpty()) {
				// 7.1.1 若拦截器为空，那就直接调用目标方法了
				// 参数适配：主要处理参数中存在可变参数的情况
				Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				// 7.1.2 直接对method进行反射调用，无须增强
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			// 7.2 有拦截器链的情况下,就需要构建一个MethodInvocation,因为拦截器的执行方法为MethodInterceptor.invoke(MethodInvocation invocation) 
			else {
				// 7.2.1 创建一个invocation ，此处为ReflectiveMethodInvocation  最终是通过它，去执行前置加强、后置加强等等逻辑
				MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
				// 7.2.2 ❗️❗️❗️
				// 此处会执行所有的拦截器链  交给AOP联盟的MethodInvocation去处理。当然实现还是我们Spring得ReflectiveMethodInvocation
				retVal = invocation.proceed();
			}


			// 8.1 获取执行完后返回值的类型 -> 前面已经执行完拦截器的增强通知
			// 这里会对返回类型进行适配操作
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target && returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// 8.1.1 返回值非空,并且等于默认值,并且返回值类型不是Object,并且代理对象proxy是returnType的实例,并且...
				// 需要将返回的对象修改为代理对象才是合理的❗️❗️❗️
				retVal = proxy;
			}
			// 8.2 或者返回值为null，而方法返回类型非Void就直接报错吧
			else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}
			// 正常情况：也是最多的情况，会直接返回retVal
			return retVal;
		}
		finally {
			// 9.1 释放~~
			if (target != null && !targetSource.isStatic()) {
				// 对于 SingletonTargetSource.isStatic 返回true，必须要额外处理
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// 9.2 前面要求暴露Proxy，将Proxy设置到ThreadLocal上，这里执行完后，就需要转换为之前的oldProxy
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy) {
			otherProxy = (JdkDynamicAopProxy) other;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = (JdkDynamicAopProxy) ih;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}

}
