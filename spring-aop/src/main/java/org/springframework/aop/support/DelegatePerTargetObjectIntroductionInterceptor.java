/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.support;

import java.util.Map;
import java.util.WeakHashMap;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * Convenient implementation of the
 * {@link org.springframework.aop.IntroductionInterceptor} interface.
 *
 * <p>This differs from {@link DelegatingIntroductionInterceptor} in that a single
 * instance of this class can be used to advise multiple target objects, and each target
 * object will have its <i>own</i> delegate (whereas DelegatingIntroductionInterceptor
 * shares the same delegate, and hence the same state across all targets).
 *
 * <p>The {@code suppressInterface} method can be used to suppress interfaces
 * implemented by the delegate class but which should not be introduced to the
 * owning AOP proxy.
 *
 * <p>An instance of this class is serializable if the delegates are.
 *
 * <p><i>Note: There are some implementation similarities between this class and
 * {@link DelegatingIntroductionInterceptor} that suggest a possible refactoring
 * to extract a common ancestor class in the future.</i>
 *
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @since 2.0
 * @see #suppressInterface
 * @see DelegatingIntroductionInterceptor
 */
@SuppressWarnings("serial")
public class DelegatePerTargetObjectIntroductionInterceptor extends IntroductionInfoSupport implements IntroductionInterceptor {
	// 使用场景之一: 当前切面类的上的某个字段使用了@DeclareParents时,就需要为这个切面类多生成一个对应的SpringAop的advice或Advisor
	// 而使用的就是这里的 DelegatePerTargetObjectIntroductionInterceptor

	/**
	 * Hold weak references to keys as we don't want to interfere with garbage collection..
	 */
	private final Map<Object, Object> delegateMap = new WeakHashMap<>();

	// @DeclareParents注解的defaultImpl属性
	private Class<?> defaultImplType;
	
	// 标记有@DeclareParents注解的字段的类型Type 
	private Class<?> interfaceType;


	public DelegatePerTargetObjectIntroductionInterceptor(Class<?> defaultImplType, Class<?> interfaceType) {
		// @DeclareParents注解的defaultImpl属性 -- 代理类额外实现的接口的需要将方法的调用最终委托给这里的faultImplType使用
		this.defaultImplType = defaultImplType;
		// 标记有@DeclareParents注解的字段的类型Type -- 也就是代理类需要额外实现的接口
		this.interfaceType = interfaceType;
		// 现在根据 defaultImplType 创建一个新委托（但不要将其存储在地图中）。
		// 我们这样做有两个原因：1）如果在实例化委托时出现问题，则尽早失败 2）仅填充一次接口映射
		Object delegate = createNewDelegate();
		implementInterfacesOnObject(delegate);
		// 抑制 IntroductionInterceptor/DynamicIntroductionAdvice 两个接口
		suppressInterface(IntroductionInterceptor.class);
		suppressInterface(DynamicIntroductionAdvice.class);
	}


	/**
	 * Subclasses may need to override this if they want to perform custom
	 * behaviour in around advice. However, subclasses should invoke this
	 * method, which handles introduced interfaces and forwarding to the target.
	 */
	@Override
	@Nullable
	public Object invoke(MethodInvocation mi) throws Throwable {
		// 该MethodInterceptor的执行方法
		
		// 1. 首先检查方法是否在代理类需要额外实现的接口中
		// 也就是这个MethodInterceptor委托代理的defaultImplType中是否有这个方法哦
		if (isMethodOnIntroducedInterface(mi)) {
			// 1.1 如果需要代理对象额外实现的接口中有声明这个方法
			// 那么就位每一个代理对象,生成一个委托对象,即使他们都是同一个defaultImplType类型的哦
			Object delegate = getIntroductionDelegateFor(mi.getThis());

			// 1.2 使用以下方法而不是直接反射，如果引入的方法抛出异常，我们可以正确处理 InvocationTargetException。
			// 简单的就是: 将mi.getMethod()委托给delegate对象完成
			Object retVal = AopUtils.invokeJoinpointUsingReflection(delegate, mi.getMethod(), mi.getArguments());

			// 1.3 如果可能的话，返回代理对象吧：如果委托返回自己，我们真的想返回代理。
			if (retVal == delegate && mi instanceof ProxyMethodInvocation) {
				retVal = ((ProxyMethodInvocation) mi).getProxy();
			}
			return retVal;
		}

		// 2. 继续处理原始方法
		return doProceed(mi);
	}

	/**
	 * Proceed with the supplied {@link org.aopalliance.intercept.MethodInterceptor}.
	 * Subclasses can override this method to intercept method invocations on the
	 * target object which is useful when an introduction needs to monitor the object
	 * that it is introduced into. This method is <strong>never</strong> called for
	 * {@link MethodInvocation MethodInvocations} on the introduced interfaces.
	 */
	protected Object doProceed(MethodInvocation mi) throws Throwable {
		// If we get here, just pass the invocation on.
		return mi.proceed();
	}

	private Object getIntroductionDelegateFor(Object targetObject) {
		// 1. 检查缓存delegateMap是否有为targetObject生成的委托对象
		synchronized (this.delegateMap) {
			// 1.0 缓存命中
			if (this.delegateMap.containsKey(targetObject)) {
				return this.delegateMap.get(targetObject);
			}
			// 1.1 根据defaultImplType创建一个新的实例对象就可以
			// 也就是说 -> ❗️❗️❗️
			// 一个@DeclareParents可以匹配多个类
			// 然后为每个类即当前的targetObject去生成一个对应的defaultImplType的实例对象
			// 因此我们可以知道defaultImplType创建的实例是原型的,@DeclareParents每匹配一个类让其额外实现其他接口,生成一个代理类,那么每个代理类就有
			// 对应的委托类去帮助实现哦
			else {
				Object delegate = createNewDelegate();
				this.delegateMap.put(targetObject, delegate);
				return delegate;
			}
		}
	}

	private Object createNewDelegate() {
		try {
			// ❗️❗️❗️ defaultImplType 必须空参构造
			return ReflectionUtils.accessibleConstructor(this.defaultImplType).newInstance();
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException("Cannot create default implementation for '" +
					this.interfaceType.getName() + "' mixin (" + this.defaultImplType.getName() + "): " + ex);
		}
	}

}
