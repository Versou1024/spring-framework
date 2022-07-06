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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Base class for AOP proxy configuration managers.
 * These are not themselves AOP proxies, but subclasses of this class are
 * normally factories from which AOP proxy instances are obtained directly.
 *
 * <p>This class frees subclasses of the housekeeping of Advices
 * and Advisors, but doesn't actually implement proxy creation
 * methods, which are provided by subclasses.
 *
 * <p>This class is serializable; subclasses need not be.
 * This class is used to hold snapshots of proxies.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.framework.AopProxy
 */
public class AdvisedSupport extends ProxyConfig implements Advised {
	/*
	 * AdvisedSupport 从类名就可以看出，主要是实现Advised接口
	 *
	 * AdvisedSupport 组合有目标对象TargetSource
	 * AdvisedSupport 用来注册被代理目标对象、通知和需要代理的接口
	 * AdvisedSupport本身不会提供创建代理的任何方法，专注于通过advisorChainFactory生成拦截器链。
	 * 以及管理各种的Advisor
	 * 
	 * note: 管理的各种的Advisor都是为同一个代理类服务的哦
	 */

	/** use serialVersionUID from Spring 2.0 for interoperability. */
	private static final long serialVersionUID = 2651364800145442165L;


	/**
	 * Canonical TargetSource when there's no target, and behavior is
	 * supplied by the advisors.
	 */
	public static final TargetSource EMPTY_TARGET_SOURCE = EmptyTargetSource.INSTANCE;


	/** Package-protected to allow direct access for efficiency. */
	TargetSource targetSource = EMPTY_TARGET_SOURCE; 
	// 持有目标对象

	/** Whether the Advisors are already filtered for the specific target class. */
	private boolean preFiltered = false; 
	// 对于给定的targetClass，是否这些 Advisors 已经被过滤

	/** The AdvisorChainFactory to use. */
	AdvisorChainFactory advisorChainFactory = new DefaultAdvisorChainFactory(); 
	// advisorChainFactory 工厂
	// 默认是 DefaultAdvisorChainFactory.

	/** Cache with Method as key and advisor chain List as value. */
	private transient Map<MethodCacheKey, List<Object>> methodCache; 
	// 缓存：以 method 为key，以 Advisor 列表为value

	/**
	 * Interfaces to be implemented by the proxy. Held in List to keep the order
	 * of registration, to create JDK proxy with specified order of interfaces.
	 */
	private List<Class<?>> interfaces = new ArrayList<>(); 
	// 代理类需要实现的所有接口，会被一个List集合保持注册顺序，在创建JDK代理时是有用的

	/**
	 * List of Advisors. If an Advice is added, it will be wrapped
	 * in an Advisor before being added to this List.
	 */
	private List<Advisor> advisors = new ArrayList<>(); 
	// Advisor列表，如果一个Advice被添加，就会被一个Advisor给包装在Advice被添加到List之前

	/**
	 * Array updated on changes to the advisors list, which is easier
	 * to manipulate internally.
	 */
	private Advisor[] advisorArray = new Advisor[0];


	/**
	 * No-arg constructor for use as a JavaBean.
	 */
	public AdvisedSupport() {
		// 初始化 methodCache
		this.methodCache = new ConcurrentHashMap<>(32);
	}

	/**
	 * Create a AdvisedSupport instance with the given parameters.
	 * @param interfaces the proxied interfaces
	 */
	public AdvisedSupport(Class<?>... interfaces) {
		this();
		setInterfaces(interfaces);
	}


	/**
	 * Set the given object as target.
	 * Will create a SingletonTargetSource for the object.
	 * @see #setTargetSource
	 * @see org.springframework.aop.target.SingletonTargetSource
	 */
	public void setTarget(Object target) {
		setTargetSource(new SingletonTargetSource(target));
	}

	@Override
	public void setTargetSource(@Nullable TargetSource targetSource) {
		this.targetSource = (targetSource != null ? targetSource : EMPTY_TARGET_SOURCE);
	}

	@Override
	public TargetSource getTargetSource() {
		return this.targetSource;
	}

	/**
	 * Set a target class to be proxied, indicating that the proxy
	 * should be castable to the given class.
	 * <p>Internally, an {@link org.springframework.aop.target.EmptyTargetSource}
	 * for the given target class will be used. The kind of proxy needed
	 * will be determined on actual creation of the proxy.
	 * <p>This is a replacement for setting a "targetSource" or "target",
	 * for the case where we want a proxy based on a target class
	 * (which can be an interface or a concrete class) without having
	 * a fully capable TargetSource available.
	 * @see #setTargetSource
	 * @see #setTarget
	 */
	public void setTargetClass(@Nullable Class<?> targetClass) {
		this.targetSource = EmptyTargetSource.forClass(targetClass);
	}

	@Override
	@Nullable
	public Class<?> getTargetClass() {
		return this.targetSource.getTargetClass();
	}

	@Override
	public void setPreFiltered(boolean preFiltered) {
		this.preFiltered = preFiltered;
	}

	@Override
	public boolean isPreFiltered() {
		return this.preFiltered;
	}

	/**
	 * Set the advisor chain factory to use.
	 * <p>Default is a {@link DefaultAdvisorChainFactory}.
	 */
	public void setAdvisorChainFactory(AdvisorChainFactory advisorChainFactory) {
		Assert.notNull(advisorChainFactory, "AdvisorChainFactory must not be null");
		this.advisorChainFactory = advisorChainFactory;
	}

	/**
	 * Return the advisor chain factory to use (never {@code null}).
	 */
	public AdvisorChainFactory getAdvisorChainFactory() {
		return this.advisorChainFactory;
	}


	/**
	 * Set the interfaces to be proxied.
	 */
	public void setInterfaces(Class<?>... interfaces) {
		Assert.notNull(interfaces, "Interfaces must not be null");
		this.interfaces.clear();
		for (Class<?> ifc : interfaces) {
			addInterface(ifc);
		}
	}

	/**
	 * Add a new proxied interface.
	 * @param intf the additional interface to proxy
	 */
	public void addInterface(Class<?> intf) {
		Assert.notNull(intf, "Interface must not be null");
		if (!intf.isInterface()) {
			throw new IllegalArgumentException("[" + intf.getName() + "] is not an interface");
		}
		if (!this.interfaces.contains(intf)) {
			this.interfaces.add(intf);
			// 改变advice状态
			adviceChanged();
		}
	}

	/**
	 * Remove a proxied interface.
	 * <p>Does nothing if the given interface isn't proxied.
	 * @param intf the interface to remove from the proxy
	 * @return {@code true} if the interface was removed; {@code false}
	 * if the interface was not found and hence could not be removed
	 */
	public boolean removeInterface(Class<?> intf) {
		return this.interfaces.remove(intf);
	}

	@Override
	public Class<?>[] getProxiedInterfaces() {
		return ClassUtils.toClassArray(this.interfaces);
	}

	@Override
	public boolean isInterfaceProxied(Class<?> intf) {
		// 是否有这个代理的接口
		for (Class<?> proxyIntf : this.interfaces) {
			if (intf.isAssignableFrom(proxyIntf)) {
				return true;
			}
		}
		return false;
	}


	@Override
	public final Advisor[] getAdvisors() {
		return this.advisorArray;
	}

	@Override
	public void addAdvisor(Advisor advisor) {
		// 添加到advisors中
		int pos = this.advisors.size();
		addAdvisor(pos, advisor);
	}

	@Override
	public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
		if (advisor instanceof IntroductionAdvisor) {
			// 一种特殊类型的Advisor，即IntroductionAdvisor
			validateIntroductionAdvisor((IntroductionAdvisor) advisor);
		}
		addAdvisorInternal(pos, advisor);
	}

	@Override
	public boolean removeAdvisor(Advisor advisor) {
		int index = indexOf(advisor);
		if (index == -1) {
			return false;
		}
		else {
			removeAdvisor(index);
			return true;
		}
	}

	@Override
	public void removeAdvisor(int index) throws AopConfigException {
		if (isFrozen()) {
			throw new AopConfigException("Cannot remove Advisor: Configuration is frozen.");
		}
		if (index < 0 || index > this.advisors.size() - 1) {
			throw new AopConfigException("Advisor index " + index + " is out of bounds: " +
					"This configuration only has " + this.advisors.size() + " advisors.");
		}

		Advisor advisor = this.advisors.remove(index);
		if (advisor instanceof IntroductionAdvisor) {
			IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
			// We need to remove introduction interfaces.
			for (Class<?> ifc : ia.getInterfaces()) {
				removeInterface(ifc);
			}
		}

		updateAdvisorArray();
		adviceChanged();
	}

	@Override
	public int indexOf(Advisor advisor) {
		Assert.notNull(advisor, "Advisor must not be null");
		return this.advisors.indexOf(advisor);
	}

	@Override
	public boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException {
		Assert.notNull(a, "Advisor a must not be null");
		Assert.notNull(b, "Advisor b must not be null");
		int index = indexOf(a);
		if (index == -1) {
			return false;
		}
		removeAdvisor(index);
		addAdvisor(index, b);
		return true;
	}

	/**
	 * Add all of the given advisors to this proxy configuration.
	 * @param advisors the advisors to register
	 */
	public void addAdvisors(Advisor... advisors) {
		addAdvisors(Arrays.asList(advisors));
	}

	/**
	 * Add all of the given advisors to this proxy configuration.
	 * @param advisors the advisors to register
	 */
	public void addAdvisors(Collection<Advisor> advisors) {
		if (isFrozen()) {
			throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
		}
		if (!CollectionUtils.isEmpty(advisors)) {
			for (Advisor advisor : advisors) {
				if (advisor instanceof IntroductionAdvisor) {
					validateIntroductionAdvisor((IntroductionAdvisor) advisor);
				}
				Assert.notNull(advisor, "Advisor must not be null");
				this.advisors.add(advisor);
			}
			updateAdvisorArray();
			adviceChanged();
		}
	}

	private void validateIntroductionAdvisor(IntroductionAdvisor advisor) {
		advisor.validateInterfaces();
		// If the advisor passed validation, we can make the change.
		Class<?>[] ifcs = advisor.getInterfaces();
		for (Class<?> ifc : ifcs) {
			addInterface(ifc);
		}
	}

	private void addAdvisorInternal(int pos, Advisor advisor) throws AopConfigException {
		Assert.notNull(advisor, "Advisor must not be null");
		// 如果已经冻结AopConfig，直接报错
		if (isFrozen()) {
			throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
		}
		// 超过 advisors.size() 就直接报出异常
		if (pos > this.advisors.size()) {
			throw new IllegalArgumentException("Illegal position " + pos + " in advisor list with size " + this.advisors.size());
		}
		// 添加到 advisors
		this.advisors.add(pos, advisor);
		// 由于添加了新的Advisor，需要将advisors数组重新赋值
		updateAdvisorArray();
		// 由于添加了新的Advisor，需要将methodCache清空
		adviceChanged();
	}

	/**
	 * Bring the array up to date with the list.
	 */
	protected final void updateAdvisorArray() {
		this.advisorArray = this.advisors.toArray(new Advisor[0]);
	}

	/**
	 * Allows uncontrolled access to the {@link List} of {@link Advisor Advisors}.
	 * <p>Use with care, and remember to {@link #updateAdvisorArray() refresh the advisor array}
	 * and {@link #adviceChanged() fire advice changed events} when making any modifications.
	 */
	protected final List<Advisor> getAdvisorsInternal() {
		return this.advisors;
	}


	@Override
	public void addAdvice(Advice advice) throws AopConfigException {
		// 直接添加一个Advice
		int pos = this.advisors.size();
		// 在最后的位置，加入这个添加的Advice
		addAdvice(pos, advice);
	}

	/**
	 * Cannot add introductions this way unless the advice implements IntroductionInfo.
	 */
	@Override
	public void addAdvice(int pos, Advice advice) throws AopConfigException {
		Assert.notNull(advice, "Advice must not be null");
		if (advice instanceof IntroductionInfo) {
			// We don't need an IntroductionAdvisor for this kind of introduction:
			// It's fully self-describing.
			addAdvisor(pos, new DefaultIntroductionAdvisor(advice, (IntroductionInfo) advice));
		}
		else if (advice instanceof DynamicIntroductionAdvice) {
			// We need an IntroductionAdvisor for this kind of introduction.
			throw new AopConfigException("DynamicIntroductionAdvice may only be added as part of IntroductionAdvisor");
		}
		else {
			// 普通的Advice - 包装为默认的Advisor -- DefaultPointcutAdvisor
			addAdvisor(pos, new DefaultPointcutAdvisor(advice));
		}
	}

	@Override
	public boolean removeAdvice(Advice advice) throws AopConfigException {
		int index = indexOf(advice);
		if (index == -1) {
			return false;
		}
		else {
			removeAdvisor(index);
			return true;
		}
	}

	@Override
	public int indexOf(Advice advice) {
		Assert.notNull(advice, "Advice must not be null");
		for (int i = 0; i < this.advisors.size(); i++) {
			Advisor advisor = this.advisors.get(i);
			if (advisor.getAdvice() == advice) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Is the given advice included in any advisor within this proxy configuration?
	 * @param advice the advice to check inclusion of
	 * @return whether this advice instance is included
	 */
	public boolean adviceIncluded(@Nullable Advice advice) {
		if (advice != null) {
			for (Advisor advisor : this.advisors) {
				if (advisor.getAdvice() == advice) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Count advices of the given class.
	 * @param adviceClass the advice class to check
	 * @return the count of the interceptors of this class or subclasses
	 */
	public int countAdvicesOfType(@Nullable Class<?> adviceClass) {
		int count = 0;
		if (adviceClass != null) {
			for (Advisor advisor : this.advisors) {
				if (adviceClass.isInstance(advisor.getAdvice())) {
					count++;
				}
			}
		}
		return count;
	}


	/**
	 * Determine a list of {@link org.aopalliance.intercept.MethodInterceptor} objects
	 * for the given method, based on this configuration.
	 * @param method the proxied method
	 * @param targetClass the target class
	 * @return a List of MethodInterceptors (may also include InterceptorAndDynamicMethodMatchers)
	 */
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, @Nullable Class<?> targetClass) {
		// ❗️❗️❗️ -> 很重要的 -> 代理对象的方法的执行,都需要调用这个类,来确保可以在当前方法上生效的MethodInterceptor
		// 只有MethodInterceptor或Advice的MethodMatcher匹配当前方法,那么这个增强通知或者拦截器就会生效哦
		// 1. 以这个Method生成一个key，准备缓存
		// 此处小技巧：当你的key比较复杂事，可以用类来处理。然后重写它的equals、hashCode、toString、compare等方法
		MethodCacheKey cacheKey = new MethodCacheKey(method);
		// 2. 从缓存中获取过滤器链 -- 使用Object的原因在于拦截器可以是 MethodInterceptor也可以是InterceptorAndDynamicMethodMatcher的
		List<Object> cached = this.methodCache.get(cacheKey);
		if (cached == null) {
			// 2.1 缓存未命中，就需要为其生成过滤器链，然后存入缓存
			// 这个方法最终在这 DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice
			// DefaultAdvisorChainFactory：生成通知器链的工厂，实现了interceptor链的获取过程
			cached = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);
			this.methodCache.put(cacheKey, cached);
		}
		return cached;
	}

	/**
	 * Invoked when advice has changed.
	 */
	protected void adviceChanged() {
		this.methodCache.clear();
	}

	/**
	 * Call this method on a new instance created by the no-arg constructor
	 * to create an independent copy of the configuration from the given object.
	 * @param other the AdvisedSupport object to copy configuration from
	 */
	protected void copyConfigurationFrom(AdvisedSupport other) {
		copyConfigurationFrom(other, other.targetSource, new ArrayList<>(other.advisors));
	}

	/**
	 * Copy the AOP configuration from the given AdvisedSupport object,
	 * but allow substitution of a fresh TargetSource and a given interceptor chain.
	 * @param other the AdvisedSupport object to take proxy configuration from
	 * @param targetSource the new TargetSource
	 * @param advisors the Advisors for the chain
	 */
	protected void copyConfigurationFrom(AdvisedSupport other, TargetSource targetSource, List<Advisor> advisors) {
		copyFrom(other);
		this.targetSource = targetSource;
		this.advisorChainFactory = other.advisorChainFactory;
		this.interfaces = new ArrayList<>(other.interfaces);
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				validateIntroductionAdvisor((IntroductionAdvisor) advisor);
			}
			Assert.notNull(advisor, "Advisor must not be null");
			this.advisors.add(advisor);
		}
		updateAdvisorArray();
		adviceChanged();
	}

	/**
	 * Build a configuration-only copy of this AdvisedSupport,
	 * replacing the TargetSource.
	 */
	AdvisedSupport getConfigurationOnlyCopy() {
		AdvisedSupport copy = new AdvisedSupport();
		copy.copyFrom(this);
		copy.targetSource = EmptyTargetSource.forClass(getTargetClass(), getTargetSource().isStatic());
		copy.advisorChainFactory = this.advisorChainFactory;
		copy.interfaces = this.interfaces;
		copy.advisors = this.advisors;
		copy.updateAdvisorArray();
		return copy;
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.methodCache = new ConcurrentHashMap<>(32);
	}


	@Override
	public String toProxyConfigString() {
		return toString();
	}

	/**
	 * For debugging/diagnostic use.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getName());
		sb.append(": ").append(this.interfaces.size()).append(" interfaces ");
		sb.append(ClassUtils.classNamesToString(this.interfaces)).append("; ");
		sb.append(this.advisors.size()).append(" advisors ");
		sb.append(this.advisors).append("; ");
		sb.append("targetSource [").append(this.targetSource).append("]; ");
		sb.append(super.toString());
		return sb.toString();
	}


	/**
	 * Simple wrapper class around a Method. Used as the key when
	 * caching methods, for efficient equals and hashCode comparisons.
	 */
	private static final class MethodCacheKey implements Comparable<MethodCacheKey> {
		/**
		 * 作为缓存的key，即封装有method和对应的hashCode
		 */

		private final Method method;

		private final int hashCode;

		public MethodCacheKey(Method method) {
			this.method = method;
			this.hashCode = method.hashCode();
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof MethodCacheKey &&
					this.method == ((MethodCacheKey) other).method));
		}

		@Override
		public int hashCode() {
			return this.hashCode;
		}

		@Override
		public String toString() {
			return this.method.toString();
		}

		@Override
		public int compareTo(MethodCacheKey other) {
			int result = this.method.getName().compareTo(other.method.getName());
			if (result == 0) {
				result = this.method.toString().compareTo(other.method.toString());
			}
			return result;
		}
	}

}
