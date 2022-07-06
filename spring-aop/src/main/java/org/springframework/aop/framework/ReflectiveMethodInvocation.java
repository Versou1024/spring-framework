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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.lang.Nullable;

/**
 * Spring's implementation of the AOP Alliance
 * {@link org.aopalliance.intercept.MethodInvocation} interface,
 * implementing the extended
 * {@link org.springframework.aop.ProxyMethodInvocation} interface.
 *
 * <p>Invokes the target object using reflection. Subclasses can override the
 * {@link #invokeJoinpoint()} method to change this behavior, so this is also
 * a useful base class for more specialized MethodInvocation implementations.
 *
 * <p>It is possible to clone an invocation, to invoke {@link #proceed()}
 * repeatedly (once per clone), using the {@link #invocableClone()} method.
 * It is also possible to attach custom attributes to the invocation,
 * using the {@link #setUserAttribute} / {@link #getUserAttribute} methods.
 *
 * <p><b>NOTE:</b> This class is considered internal and should not be
 * directly accessed. The sole reason for it being public is compatibility
 * with existing framework integrations (e.g. Pitchfork). For any other
 * purposes, use the {@link ProxyMethodInvocation} interface instead.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @see #invokeJoinpoint
 * @see #proceed
 * @see #invocableClone
 * @see #setUserAttribute
 * @see #getUserAttribute
 */
public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {
	// Reflective中文意思：可被反射的
	// 反射方法调用形参组合Bean，负责解耦

	protected final Object proxy; // 代理类

	@Nullable
	protected final Object target; // 目标类

	protected final Method method; // 方法

	protected Object[] arguments; // 参数

	@Nullable
	private final Class<?> targetClass; // 目标Class

	/**
	 * Lazily initialized map of user-specific attributes for this invocation.
	 */
	@Nullable
	private Map<String, Object> userAttributes; // 用户定义的属性

	/**
	 * List of MethodInterceptor and InterceptorAndDynamicMethodMatcher
	 * that need dynamic checks.
	 */
	protected final List<?> interceptorsAndDynamicMethodMatchers; // 缓存 MethodInterceptor 与 InterceptorAndDynamicMethodMatcher

	/**
	 * Index from 0 of the current interceptor we're invoking.
	 * -1 until we invoke: then the current interceptor.
	 */
	private int currentInterceptorIndex = -1; // 当前被执行的拦截器序号，-- 这里的拦截器就是对代理方法进行增强的处理


	/**
	 * Construct a new ReflectiveMethodInvocation with the given arguments.
	 * @param proxy the proxy object that the invocation was made on
	 * @param target the target object to invoke
	 * @param method the method to invoke
	 * @param arguments the arguments to invoke the method with
	 * @param targetClass the target class, for MethodMatcher invocations
	 * @param interceptorsAndDynamicMethodMatchers interceptors that should be applied,
	 * along with any InterceptorAndDynamicMethodMatchers that need evaluation at runtime.
	 * MethodMatchers included in this struct must already have been found to have matched
	 * as far as was possibly statically. Passing an array might be about 10% faster,
	 * but would complicate the code. And it would work only for static pointcuts.
	 */
	protected ReflectiveMethodInvocation(
			// 唯一的构造函数。注意是protected相当于只能本包内、以及子类可以调用。外部是不能直接初始化的此对象的（显然就是Spring内部使用的类了嘛）
			// invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
			// proxy：代理对象
			// target：目标对象
			// method：被代理的方法
			// args：方法的参数们
			// targetClass：目标方法的Class (target != null ? target.getClass() : null)
			// interceptorsAndDynamicMethodMatchers：拦截链。  
			// this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass)这个方法找出来的
			Object proxy, @Nullable Object target, Method method, @Nullable Object[] arguments,
			@Nullable Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {
		// 代理对象、目标对象、目标类型、读音方法、参数适配、过滤器链
		this.proxy = proxy;
		this.target = target;
		this.targetClass = targetClass;
		// 找到桥接方法，作为最后执行的方法。至于什么是桥接方法，自行百度关键字：bridge method
		// 桥接方法是 JDK 1.5 引入泛型后，为了使Java的泛型方法生成的字节码和 1.5 版本前的字节码相兼容，由编译器自动生成的方法（子类实现父类的泛型方法时会生成桥接方法）
		// 桥接方法说明:
		// 在字节码中，桥接方法会被标记 ACC_BRIDGE 和 ACC_SYNTHETIC
		// ACC_BRIDGE 用来说明 桥接方法是由 Java 编译器 生成的
		// ACC_SYNCTHETIC 用来表示 该类成员没有出现在源代码中，而是由编译器生成
		// 举例: 
		// public interface Consumer<T> {
		//    void accept(T t);
		// }
		// public class StringConsumer implements Consumer<String> {
		//    @Override
		//    public void accept(String s) {
		//        System.out.println("i consumed " + s);
		//    }
		// }
		// 实际上StringConsumer有两个方法
		// 第一个: public synctheitc bridge accept(Ljava.lang.Object) V -- 编译器自动生成的桥接方法
		// 第二个: public accept(Ljava.lang.String) V -- 用户实现的类
		// 第一个桥接方法中,主要就是检查传递进来的Object是否为String类型,是的话,就回去调用第二个非桥接的方法哦
		// 其目的就是: 编译器为了让子类有一个与父类的方法签名一致的方法，就在子类自动生成一个与父类的方法签名一致的桥接方法。
		// 如果用户使用的是 accept(new Object()) 讲导致这里使用的是桥接方法
		// 那么通过 BridgeMethodResolver.findBridgedMethod(method) 将找到最终的 accept(String s) 方法保证形参正确
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
		this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
	}
	
	// 以下是: 获取代理对象\目标对象\方法\参数等方法,忽略~~


	@Override
	public final Object getProxy() {
		return this.proxy;
	}

	@Override
	@Nullable
	public final Object getThis() {
		return this.target;
	}

	@Override
	public final AccessibleObject getStaticPart() {
		// 此处：getStaticPart返回的就是当前得method
		return this.method;
	}

	/**
	 * Return the method invoked on the proxied interface.
	 * May or may not correspond with a method invoked on an underlying
	 * implementation of that interface.
	 */
	@Override
	public final Method getMethod() {
		// 注意：这里返回的可能是桥接方法哦
		return this.method;
	}

	@Override
	public final Object[] getArguments() {
		return this.arguments;
	}

	@Override
	public void setArguments(Object... arguments) {
		this.arguments = arguments;
	}
	
	// ❗️❗️❗️
	@Override
	@Nullable
	public Object proceed() throws Throwable {
		// 这里就是核心了，执行 目标方法和增强通知 都是在此处搞定的
		// 这里面运用 递归调用 的方式，非常具有技巧性

		// 1. 默认是从-1开始
		// 这个if就是表示是否已经执行啦所有的拦截器
		if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
			// 比如有两个拦截器,那么this.interceptorsAndDynamicMethodMatchers.size()就是2
			// currentInterceptorIndex默认从-1开始,每次++this.currentInterceptorIndex获取一个拦截器执行
			// 第一次++this.currentInterceptorIndex后结果为0,在位置0的拦截器中调用MethodInvocation.proceed()又回到本方法
			// 即第二次进入该方法,又使用++this.currentInterceptorIndex后结果为1,在位置1的拦截器中继续调用MethodInvocation.proceed()又回到本方法
			// 第三次进入该方法,会在这里发现currentInterceptorIndex已经1,即所有的拦截器执行完,需要执行最终target的method啦
			return invokeJoinpoint();
		}

		// 2. ++this.currentInterceptorIndex就是从-1加到0，然后开始处理
		Object interceptorOrInterceptionAdvice = this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
		// 3.1 InterceptorAndDynamicMethodMatcher它是Spring内部使用的一个类。很简单，就是把MethodInterceptor实例和MethodMatcher放在了一起。
		// 一般使用InterceptorAndDynamicMethodMatcher的情况就是DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice()
		// 中发现MethodMatcher.isRuntime()为true即运行时检查,那么就会将对应的MethodInterceptor封装为InterceptorAndDynamicMethodMatcher
		if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
			// 3.1.1 在这里评估动态方法匹配器：静态部分已经在DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice()被评估并发现匹配。
			InterceptorAndDynamicMethodMatcher dm = (InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
			Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());
			// 3.2.1 去匹配这个拦截器是否适用于这个目标方法 -- 这个matches是动态匹配,在方法运行时匹配,因此会传入arguments数组哦
			// 如果动态匹配通过,就会执行当前拦截器
			if (dm.methodMatcher.matches(this.method, targetClass, this.arguments)) {
				return dm.interceptor.invoke(this);
			}
			// 3.2.2 如果不匹配。就跳过此拦截器，而继续执行下一个拦截器
			// 注意：这里是递归调用  并不是循环调用
			else {
				return proceed();
			}
		}
		// 3.2 属于静态匹配的拦截器,即类过滤器和方法过滤器已经在选择拦截器的时候,就已经静态匹配完啦
		// 静态匹配的地方: DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice() 
		// 同样传入invocation开始执行MethodInterceptor
		else {
			// note: 比如一个方法被前置通知和后置通知拦截,那么传递进来的interceptorsAndDynamicMethodMatchers应该有2个
			// 由于是普通的前置通知和后置通知拦截为静态的,那么已经完成了MethodMatcher的匹配,因此进入到这里
			// 然后触发前置通知的方法MethodBeforeAdviceInterceptor.invoke()代码中又触发这里传递进去的ReflectiveMethodInvocation.proceed()方法
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}

	/**
	 * Invoke the joinpoint using reflection.
	 * Subclasses can override this to use custom invocation.
	 * @return the return value of the joinpoint
	 * @throws Throwable if invoking the joinpoint resulted in an exception
	 */
	@Nullable
	protected Object invokeJoinpoint() throws Throwable {
		// 在所有拦截器执行完后,继续调用 ReflectiveMethodInvocation.proceed() 就会触发 invokeJoinpoint()
		// 其实现就是简单的：method.invoke(target, args);
		
		// 子类可以复写此方法去执行。
		// 比如它的唯一子类CglibAopProxy内部类CglibMethodInvocation就复写了这个方法,
		// 它对public的方法做了一个处理（public方法调用MethodProxy.invoke）
		// 此处传入的是target，而不能是proxy，否则进入死循环
		return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
	}


	/**
	 * This implementation returns a shallow copy of this invocation object,
	 * including an independent copy of the original arguments array.
	 * <p>We want a shallow copy in this case: We want to use the same interceptor
	 * chain and other object references, but we want an independent value for the
	 * current interceptor index.
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone() {
		Object[] cloneArguments = this.arguments;
		if (this.arguments.length > 0) {
			// Build an independent copy of the arguments array.
			cloneArguments = this.arguments.clone();
		}
		return invocableClone(cloneArguments);
	}

	/**
	 * This implementation returns a shallow copy of this invocation object,
	 * using the given arguments array for the clone.
	 * <p>We want a shallow copy in this case: We want to use the same interceptor
	 * chain and other object references, but we want an independent value for the
	 * current interceptor index.
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone(Object... arguments) {
		// Force initialization of the user attributes Map,
		// for having a shared Map reference in the clone.
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}

		// Create the MethodInvocation clone.
		try {
			ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
			clone.arguments = arguments;
			return clone;
		}
		catch (CloneNotSupportedException ex) {
			throw new IllegalStateException(
					"Should be able to clone object of type [" + getClass() + "]: " + ex);
		}
	}


	@Override
	public void setUserAttribute(String key, @Nullable Object value) {
		if (value != null) {
			if (this.userAttributes == null) {
				this.userAttributes = new HashMap<>();
			}
			this.userAttributes.put(key, value);
		}
		else {
			if (this.userAttributes != null) {
				this.userAttributes.remove(key);
			}
		}
	}

	@Override
	@Nullable
	public Object getUserAttribute(String key) {
		return (this.userAttributes != null ? this.userAttributes.get(key) : null);
	}

	/**
	 * Return user attributes associated with this invocation.
	 * This method provides an invocation-bound alternative to a ThreadLocal.
	 * <p>This map is initialized lazily and is not used in the AOP framework itself.
	 * @return any user attributes associated with this invocation
	 * (never {@code null})
	 */
	public Map<String, Object> getUserAttributes() {
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}
		return this.userAttributes;
	}


	@Override
	public String toString() {
		// Don't do toString on target, it may be proxied.
		StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
		sb.append(this.method).append("; ");
		if (this.target == null) {
			sb.append("target is null");
		}
		else {
			sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
		}
		return sb.toString();
	}

}
