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

package org.springframework.aop.framework.autoproxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that wraps each eligible bean with an AOP proxy, delegating to specified interceptors
 * before invoking the bean itself.
 *
 * <p>This class distinguishes between "common" interceptors: shared for all proxies it
 * creates, and "specific" interceptors: unique per bean instance. There need not be any
 * common interceptors. If there are, they are set using the interceptorNames property.
 * As with {@link org.springframework.aop.framework.ProxyFactoryBean}, interceptors names
 * in the current factory are used rather than bean references to allow correct handling
 * of prototype advisors and interceptors: for example, to support stateful mixins.
 * Any advice type is supported for {@link #setInterceptorNames "interceptorNames"} entries.
 *
 * <p>Such auto-proxying is particularly useful if there's a large number of beans that
 * need to be wrapped with similar proxies, i.e. delegating to the same interceptors.
 * Instead of x repetitive proxy definitions for x target beans, you can register
 * one single such post processor with the bean factory to achieve the same effect.
 *
 * <p>Subclasses can apply any strategy to decide if a bean is to be proxied, e.g. by type,
 * by name, by definition details, etc. They can also return additional interceptors that
 * should just be applied to the specific bean instance. A simple concrete implementation is
 * {@link BeanNameAutoProxyCreator}, identifying the beans to be proxied via given names.
 *
 * <p>Any number of {@link TargetSourceCreator} implementations can be used to create
 * a custom target source: for example, to pool prototype objects. Auto-proxying will
 * occur even if there is no advice, as long as a TargetSourceCreator specifies a custom
 * {@link org.springframework.aop.TargetSource}. If there are no TargetSourceCreators set,
 * or if none matches, a {@link org.springframework.aop.target.SingletonTargetSource}
 * will be used by default to wrap the target bean instance.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @since 13.10.2003
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {
	/*
	 * 这个类是声明式 Aop 编程中非常重要的一个角色：自动代理创建器
	 * AbstractAutoProxyCreator是对自动代理创建器的一个抽象实现。最重要的是，它实现了SmartInstantiationAwareBeanPostProcessor接口，
	 * 因此会介入到Spring IoC容器Bean实例化的过程，因此由此为入口进行展开~
	 * 解读如下：
	 *
	 * AbstractAutoProxyCreator 继承了 ProxyProcessorSupport, 所以它具有了ProxyConfig中动态代理应该具有的配置属性
	 * AbstractAutoProxyCreator 实现了 SmartInstantiationAwareBeanPostProcessor(包括实例化的前后置函数, 初始化的前后置函数) 并进行了实现
	 * 实现了 创建代理类的主方法 createProxy 方法
	 * 定义了抽象方法 getAdvicesAndAdvisorsForBean**(获取 Bean对应的 Advisor)**
	 * AbstractAutoProxyCreator 中等于是构建了创建 Aop 对象的主逻辑**（模版）**，而其子类 AbstractAdvisorAutoProxyCreator 实现了getAdvicesAndAdvisorsForBean 方法,
	 * 并且通过工具类 BeanFactoryAdvisorRetrievalHelper(PS: 它的方法findAdvisorBeans中实现类获取容器中所有 Advisor 的方法) 来获取其对应的 Advisor。它的主要子类如下：
	 *
	 * AbstractAutoProxyCreator 的几个实现类：
	 *
	 * AspectJAwareAdvisorAutoProxyCreator:
	 *  	通过解析 aop 命名空间的配置信息时生成的 AdvisorAutoProxyCreator,
	 *  	主要通过ConfigBeanDefinitionParser.parse() -> ConfigBeanDefinitionParser.configureAutoProxyCreator() -> AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary() -> AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary();
	 *  	与之对应的 Pointcut 是AspectJExpressionPointcut, Advisor 是 AspectJPointcutAdvisor, Advice 则是 AspectJAfterAdvice, AspectJAfterReturningAdvice, AspectJAfterThrowingAdvice, AspectJAroundAdvice
	 * AnnotationAwareAspectJAutoProxyCreator:
	 *  	基于@AspectJ注解生成的切面类的一个 AbstractAutoProxyCreator, 解析的工作交给了 AspectJAutoProxyBeanDefinitionParser,
	 *  	步骤如下 AspectJAutoProxyBeanDefinitionParser.parse() -> AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary() -> AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary()
	 * DefaultAdvisorAutoProxyCreator:
	 * 		主要是根据其中的 advisorBeanNamePrefix(类名前缀) 配置进行判断
	 * BeanNameAutoProxyCreator:
	 * 		通过类的名字来判断是否作用(正则匹配)
	 *
	 */

	/**
	 * Convenience constant for subclasses: Return value for "do not proxy".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Nullable
	protected static final Object[] DO_NOT_PROXY = null; // 不需要代理，返回的结果就是null

	/**
	 * Convenience constant for subclasses: Return value for
	 * "proxy without additional interceptors, just the common ones".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0]; // 代理不需要别的拦截器即interceptors


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Default is global AdvisorAdapterRegistry. */
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance(); // 实现类就是我们熟悉的它：	DefaultAdvisorAdapterRegistry

	/**
	 * Indicates whether or not the proxy should be frozen. Overridden from super
	 * to prevent the configuration from becoming frozen too early.
	 */
	private boolean freezeProxy = false;

	/** Default is no common interceptors. */
	private String[] interceptorNames = new String[0];

	private boolean applyCommonInterceptorsFirst = true;

	@Nullable
	private TargetSourceCreator[] customTargetSourceCreators;

	@Nullable
	private BeanFactory beanFactory;

	// 用于存放自动创建AOP代理过程，已经创建的TargetSource的beanName
	private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	// earlyProxyReferences缓存：该缓存用于保存已经创建过代理对象的cachekey，**避免重复创建**
	private final Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);

	// 缓存处理好的Proxy，以beanClass或beanName为cacheKey，然后以创建的AOP代理的class为key
	private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);

	// 以beanClass或beanName为key，然后存储是否需要增强代理的标志位即advised[是否需要被通知增强]
	private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether or not the proxy should be frozen, preventing advice
	 * from being added to it once it is created.
	 * <p>Overridden from the super class to prevent the proxy configuration
	 * from being frozen before the proxy is created.
	 */
	@Override
	public void setFrozen(boolean frozen) {
		this.freezeProxy = frozen;
	}

	@Override
	public boolean isFrozen() {
		return this.freezeProxy;
	}

	/**
	 * Specify the {@link AdvisorAdapterRegistry} to use.
	 * <p>Default is the global {@link AdvisorAdapterRegistry}.
	 * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
	 */
	public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
		this.advisorAdapterRegistry = advisorAdapterRegistry;
	}

	/**
	 * Set custom {@code TargetSourceCreators} to be applied in this order.
	 * If the list is empty, or they all return null, a {@link SingletonTargetSource}
	 * will be created for each bean.
	 * <p>Note that TargetSourceCreators will kick in even for target beans
	 * where no advices or advisors have been found. If a {@code TargetSourceCreator}
	 * returns a {@link TargetSource} for a specific bean, that bean will be proxied
	 * in any case.
	 * <p>{@code TargetSourceCreators} can only be invoked if this post processor is used
	 * in a {@link BeanFactory} and its {@link BeanFactoryAware} callback is triggered.
	 * @param targetSourceCreators the list of {@code TargetSourceCreators}.
	 * Ordering is significant: The {@code TargetSource} returned from the first matching
	 * {@code TargetSourceCreator} (that is, the first that returns non-null) will be used.
	 */
	public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
		this.customTargetSourceCreators = targetSourceCreators;
	}

	/**
	 * Set the common interceptors. These must be bean names in the current factory.
	 * They can be of any advice or advisor type Spring supports.
	 * <p>If this property isn't set, there will be zero common interceptors.
	 * This is perfectly valid, if "specific" interceptors such as matching
	 * Advisors are all we want.
	 */
	public void setInterceptorNames(String... interceptorNames) {
		this.interceptorNames = interceptorNames;
	}

	/**
	 * Set whether the common interceptors should be applied before bean-specific ones.
	 * Default is "true"; else, bean-specific interceptors will get applied first.
	 */
	public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
		this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the owning {@link BeanFactory}.
	 * May be {@code null}, as this post-processor doesn't need to belong to a bean factory.
	 */
	@Nullable
	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	@Nullable
	public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
		// ListableBeanFactory#getBeanNamesForType()的时候会根据每个BeanName去匹配类型合适的Bean，
		// 这里不例外，也会帮忙在proxyTypes找一下
		if (this.proxyTypes.isEmpty()) {
			return null;
		}
		Object cacheKey = getCacheKey(beanClass, beanName);
		// proxyTypes 存储的有beanName对应的proxy类的class
		return this.proxyTypes.get(cacheKey);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
		// 不做构造函数检测，返回null 让用空构造初始化吧
		return null;
	}

	@Override
	public Object getEarlyBeanReference(Object bean, String beanName) {
		// getEarlyBeanReference()它是为了解决单例bean之间的循环依赖问题，提前将代理对象暴露出去
		Object cacheKey = getCacheKey(bean.getClass(), beanName);
		this.earlyProxyReferences.put(cacheKey, bean);
		return wrapIfNecessary(bean, beanName, cacheKey);
	}

	// 这个很重要，在Bean实例化之前，先给一个机会，看看缓存里有木有，有就直接返回得了
	// 简单的说：其主要目的在于如果用户使用了自定义的TargetSource对象，则直接使用该对象生成目标对象，而不会使用Spring的默认逻辑生成目标对象
	// (并且这里会判断各个切面逻辑是否可以应用到当前bean上)
	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
		// 在实例化之前，如果beanName对应的bean是AOP代理，那么就生成AOP代理类，然后就不需要后续SPring进行实例化Bean了

		Object cacheKey = getCacheKey(beanClass, beanName);

		// beanName无效，或者targetSource并不包括这个beanName -- 可能是第一次实例化、可能没有代理、也可能是不用代理
		if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
			// 但是，如果advisedBeans包含cacheKey，表明不需要代理，返回null
			if (this.advisedBeans.containsKey(cacheKey)) {
				return null;
			}
			// 如果 beanClass 实现了 Advice、Pointcut、Advisor、AopInfrastructureBean 等属于AOP框架的接口，就说明是不要代理的
			// 存入到 advisedBeans ，value为false，表示这个BeanName已经处理过，但不需要进行代理增强
			// shouldSkip:默认都是返回false的。
			// AspectJAwareAdvisorAutoProxyCreator重写此方法：只要存在一个Advisor   ((AspectJPointcutAdvisor) advisor).getAspectName().equals(beanName)成立  就返回true
			if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) { // todo shouldSKip
				this.advisedBeans.put(cacheKey, Boolean.FALSE);
				return null;
			}
		}

		//到这，只有在TargetSource中没有进行缓存，并且应该被切面逻辑环绕，但是目前还未生成代理对象的bean才会通过此方法

		// Create proxy here if we have a custom TargetSource.
		// 如果我们有TargetSourceCreator，这里就会创建一个代理对象
		// getCustomTargetSource逻辑：存在TargetSourceCreator  并且 beanFactory.containsBean(beanName)
		// 然后遍历所有的TargetSourceCreator，调用getTargetSource谁先创建不为null就终止
		TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
		if (targetSource != null) {
			if (StringUtils.hasLength(beanName)) {
				// bean可以代理，将其beanName加入到targetSourceBeans集合中
				this.targetSourcedBeans.add(beanName);
			}
			// getAdvicesAndAdvisorsForBean：方法判断当前bean是否需要进行代理，若需要则返回满足条件的Advice或者Advisor集合
			// 这个方法由子类实现，AbstractAdvisorAutoProxyCreator和BeanNameAutoProxyCreator  代表中两种不同的代理方式
			// 主要是根据对方法getAdvicesAndAdvisorsForBean()的实现不一样，策略也就有两种了。它有两个直接实现：BeanNameAutoProxyCreator和AbstractAdvisorAutoProxyCreator。
			Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
			// 顾名思义，就是根据目标对象创建代理对象的核心逻辑了 下面详解
			Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
			// 缓存已经弄完的Proxy
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}
		// 一旦  getCustomTargetSource(beanClass, beanName) 返回null，那么就表明无需代理增强，直接返回null
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) {
		// 无特殊，直接返回true
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		return pvs;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		// 无特殊直接返回bean
		return bean;
	}

	/**
	 * Create a proxy with the configured interceptors if the bean is
	 * identified as one to proxy by the subclass.
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		// 代理对象是通过AbstractAutoProxyCreator中的postProcessAfterInitialization()创建的
		// 因此这个方法是蛮重要的，主要是wrapIfNecessary()方法会特别的重要
		// earlyProxyReferences缓存：该缓存用于保存已经创建过代理对象的cachekey，**避免重复创建**
		if (bean != null) {
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			if (this.earlyProxyReferences.remove(cacheKey) != bean) {
				return wrapIfNecessary(bean, beanName, cacheKey);
			}
		}
		return bean;
	}


	/**
	 * Build a cache key for the given bean class and bean name.
	 * <p>Note: As of 4.2.3, this implementation does not return a concatenated
	 * class/name String anymore but rather the most efficient cache key possible:
	 * a plain bean name, prepended with {@link BeanFactory#FACTORY_BEAN_PREFIX}
	 * in case of a {@code FactoryBean}; or if no bean name specified, then the
	 * given bean {@code Class} as-is.
	 * @param beanClass the bean class
	 * @param beanName the bean name
	 * @return the cache key for the given class and name
	 */
	protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
		// 通过beanClass，以及beanName创建cacheKey
		if (StringUtils.hasLength(beanName)) {
			// beanName不为空，beanClass属于FactoryBean，即那么就加上"&"前缀
			return (FactoryBean.class.isAssignableFrom(beanClass) ?
					BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
		}
		else {
			// beanName为null时，就返回beanClass
			return beanClass;
		}
	}

	/**
	 * Wrap the given bean if necessary, i.e. if it is eligible for being proxied.
	 * @param bean the raw bean instance
	 * @param beanName the name of the bean
	 * @param cacheKey the cache key for metadata access
	 * @return a proxy wrapping the bean, or the raw bean instance as-is
	 */
	protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
        // 若此Bean已经在targetSourcedBeans里，说明已经被代理过，那就直接返回即可
		// (postProcessBeforeInstantiation()中成功创建的代理对象都会将beanName加入到targetSourceBeans中)
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}

		// 如果该Bean是基础aop框架的Bean或者免代理的Bean，那也不处理
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}

		// 逻辑同上，对于实现了Advice，Advisor，AopInfrastructureBean接口的bean，都认为是spring aop的基础框架类，不能对他们创建代理对象，
		// 同时子类也可以覆盖shouldSkip方法来指定不对哪些bean进行代理
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}

		// Create proxy if we have advice.
		// getAdvicesAndAdvisorsForBean该方法由子类实现，如果有Advice切面切进去了，我们就要给他代理
		// 根据getAdvicesAndAdvisorsForBean()方法的具体实现的不同，AbstractAutoProxyCreator又分成了两类自动代理机制
		Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);

		// 需要代理，那就进来给它创建一个代理对象吧
		if (specificInterceptors != DO_NOT_PROXY) {
			// 缓存起来，赋值为true，说明此key是被处理了且被代理了的
			this.advisedBeans.put(cacheKey, Boolean.TRUE);

			// 创建这个代理对象
			Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
			// 创建好后缓存起来  避免重复创建
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		// 缓存起来，赋值为false，说明此key是被处理了但是不满足代理要求
		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}

	/**
	 * Return whether the given bean class represents an infrastructure class
	 * that should never be proxied.
	 * <p>The default implementation considers Advices, Advisors and
	 * AopInfrastructureBeans as infrastructure classes.
	 * @param beanClass the class of the bean
	 * @return whether the bean represents an infrastructure class
	 * @see org.aopalliance.aop.Advice
	 * @see org.springframework.aop.Advisor
	 * @see org.springframework.aop.framework.AopInfrastructureBean
	 * @see #shouldSkip
	 */
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		// Advice、Pointcut、Advisor、AopInfrastructureBean等属于AOP框架的类是不需要代理的
		boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
				Pointcut.class.isAssignableFrom(beanClass) ||
				Advisor.class.isAssignableFrom(beanClass) ||
				AopInfrastructureBean.class.isAssignableFrom(beanClass);
		if (retVal && logger.isTraceEnabled()) {
			logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
		}
		return retVal;
	}

	/**
	 * Subclasses should override this method to return {@code true} if the
	 * given bean should not be considered for auto-proxying by this post-processor.
	 * <p>Sometimes we need to be able to avoid this happening, e.g. if it will lead to
	 * a circular reference or if the existing target instance needs to be preserved.
	 * This implementation returns {@code false} unless the bean name indicates an
	 * "original instance" according to {@code AutowireCapableBeanFactory} conventions.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @return whether to skip the given bean
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
	 */
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		return AutoProxyUtils.isOriginalInstance(beanName, beanClass);
	}

	/**
	 * Create a target source for bean instances. Uses any TargetSourceCreators if set.
	 * Returns {@code null} if no custom TargetSource should be used.
	 * <p>This implementation uses the "customTargetSourceCreators" property.
	 * Subclasses can override this method to use a different mechanism.
	 * @param beanClass the class of the bean to create a TargetSource for
	 * @param beanName the name of the bean
	 * @return a TargetSource for this bean
	 * @see #setCustomTargetSourceCreators
	 */
	@Nullable
	protected TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
		// 这个方法也很重要，若我们自己要实现一个TargetSourceCreator ，就可议实现我们自定义的逻辑了
		// 这里条件苛刻：customTargetSourceCreators 必须不为null
		// 并且容器内还必须有这个Bean：beanFactory.containsBean(beanName)    备注：此BeanName指的即将需要被代理得BeanName，而不是TargetSourceCreator 的BeanName
		//下面会介绍我们自己自定义一个TargetSourceCreator 来实现我们自己的逻辑

		// We can't create fancy target sources for directly registered singletons.
		// customTargetSourceCreators 用来存储用户或Spring提前存入的定制化TargetSourceCreator创建器
		if (this.customTargetSourceCreators != null && this.beanFactory != null && this.beanFactory.containsBean(beanName)) {
			// 遍历所有定制的 targetSource 创建器，直到有一个返回非null的targetSource，就返回出去即可
			for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
				TargetSource ts = tsc.getTargetSource(beanClass, beanName);
				if (ts != null) {
					// Found a matching TargetSource.
					if (logger.isTraceEnabled()) {
						logger.trace("TargetSourceCreator [" + tsc +
								"] found custom TargetSource for bean with name '" + beanName + "'");
					}
					return ts;
				}
			}
		}

		// No custom TargetSource found.
		// 没有找到定制的TargetSourec
		return null;
	}

	/**
	 * Create an AOP proxy for the given bean.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @param specificInterceptors the set of interceptors that is
	 * specific to this bean (may be empty, but not null)
	 * @param targetSource the TargetSource for the proxy,
	 * already pre-configured to access the bean
	 * @return the AOP proxy for the bean
	 * @see #buildAdvisors
	 */
	protected Object createProxy(Class<?> beanClass, @Nullable String beanName, @Nullable Object[] specificInterceptors, TargetSource targetSource) {
		// createProxy -- 创建代理对象
		// specificInterceptors：作用在这个Bean上的增强器们，这里需要注意的地方：入参是targetSource  而不是target
		// 所以最终代理的是每次AOP代理处理方法调用时，目标实例都会用到TargetSource实现

		if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
			// AbstractAutoProxyCreator 与 AbstractBeanFactoryAwareAdvisingPostProcessor 作为两个AOP代理创建者
			// 一旦为某一个类创建AOP，那么就需要调用 exposeTargetClass 将 targetClass 作为属性存入到AOP代理对象即代理Bean的属性ORIGINAL_TARGET_CLASS_ATTRIBUTE中
			AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
		}

		// 这个我们非常熟悉了ProxyFactory   创建代理对象的三大方式之一~~~
		ProxyFactory proxyFactory = new ProxyFactory();
		// 复制当前类的相关配置，因为当前类它也是个ProxyConfig
		proxyFactory.copyFrom(this);

		if (proxyFactory.isProxyTargetClass()) {
			// Explicit handling of JDK proxy targets (for introduction advice scenarios)
			if (Proxy.isProxyClass(beanClass)) {
				// proxyFactory是Cglib代理，并且beanClass已经是代理类
				// Must allow for introductions; can't just set interfaces to the proxy's interfaces only.
				// 必须允许 引介增强
				for (Class<?> ifc : beanClass.getInterfaces()) {
					proxyFactory.addInterface(ifc);
				}
			}
		}
		else {
			// 看看是否是基于类的代理（CGLIB），若表面上是基于接口的代理  我们还需要进一步去检测
			// No proxyTargetClass flag enforced, let's apply our default checks...
			// shouldProxyTargetClass方法用于判断是否应该使用targetClass类而不是接口来进行代理
			// 默认实现为和该bean定义是否属性值preserveTargetClass为true有关。默认情况下都不会有此属性值的~~~~~
			if (shouldProxyTargetClass(beanClass, beanName)) {
				proxyFactory.setProxyTargetClass(true);
			}
			// 到此处，上面说了，就是把这个类实现的接口们，都放进proxyFactory（当然也会处理一些特殊的接口~~~不算数的）
			else {
				// 到这说明：JDK代理接口的方式
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
		}

		// buildAdvisors：整理合并得到最终的advisors ，毕竟interceptorNames还指定了一些拦截器的），合并之后用Register进行wrap包装一下，对于AspectJ一般不包装篇
		// 至于调用的先后顺序，通过applyCommonInterceptorsFirst参数可以进行设置，
		// 若applyCommonInterceptorsFirst为true，interceptorNames属性指定的Advisor优先调用。默认为true
		Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
		// 添加进工厂里
		proxyFactory.addAdvisors(advisors);
		// 把targetSource放进去  TargetSource的实现方式有多种 后面会介绍
		proxyFactory.setTargetSource(targetSource);

		// 在冻结Proxy配置前，这个方法是交给子类的，子类可以继续去定制此proxyFactory（Spring内部并没有搭理它）
		customizeProxyFactory(proxyFactory);

		// 沿用this得freezeProxy的属性值
		proxyFactory.setFrozen(this.freezeProxy);

		// 设置preFiltered的属性值，默认是false。子类：AbstractAdvisorAutoProxyCreator修改为true
		// preFiltered：字段意思为：是否已为特定目标类筛选Advisor
		// 这个字段和DefaultAdvisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice获取所有的Advisor有关
		// CglibAopProxy和JdkDynamicAopProxy都会调用此方法，然后递归执行所有的Advisor的
		if (advisorsPreFiltered()) {
			// advisorsPreFiltered()默认返回false，其子类AbstractAdvisorAutoProxyCreator重写返回true
			// 因为AbstractAdvisorAutoProxyCreator已经完成对ClassFilter、MethodMatcher进行了过滤，无须再次过滤
			proxyFactory.setPreFiltered(true);
		}

		// getProxyClassLoader():调用者可议指定  否则为：ClassUtils.getDefaultClassLoader()
		return proxyFactory.getProxy(getProxyClassLoader());
	}

	/**
	 * Determine whether the given bean should be proxied with its target class rather than its interfaces.
	 * <p>Checks the {@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE "preserveTargetClass" attribute}
	 * of the corresponding bean definition.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @return whether the given bean should be proxied with its target class
	 * @see AutoProxyUtils#shouldProxyTargetClass
	 */
	protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
		return (this.beanFactory instanceof ConfigurableListableBeanFactory &&
				AutoProxyUtils.shouldProxyTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName));
	}

	/**
	 * Return whether the Advisors returned by the subclass are pre-filtered
	 * to match the bean's target class already, allowing the ClassFilter check
	 * to be skipped when building advisors chains for AOP invocations.
	 * <p>Default is {@code false}. Subclasses may override this if they
	 * will always return pre-filtered Advisors.
	 * @return whether the Advisors are pre-filtered
	 * @see #getAdvicesAndAdvisorsForBean
	 * @see org.springframework.aop.framework.Advised#setPreFiltered
	 */
	protected boolean advisorsPreFiltered() {
		return false;
	}

	/**
	 * Determine the advisors for the given bean, including the specific interceptors
	 * as well as the common interceptor, all adapted to the Advisor interface.
	 * @param beanName the name of the bean
	 * @param specificInterceptors the set of interceptors that is
	 * specific to this bean (may be empty, but not null)
	 * @return the list of Advisors for the given bean
	 */
	protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors) {
		// 基于BeanFactory解析interceptorNames而来得Advisor数组~~~ 一般interceptorNames是空数组，不用关心
		Advisor[] commonInterceptors = resolveInterceptorNames();

		// 注意：此处用得事Object
		List<Object> allInterceptors = new ArrayList<>();

		if (specificInterceptors != null) {
			allInterceptors.addAll(Arrays.asList(specificInterceptors));

			// 若解析commonInterceptors来的有内容
			if (commonInterceptors.length > 0) {
				// commonInterceptors放在头部
				if (this.applyCommonInterceptorsFirst) {
					allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
				}
				// commonInterceptors放在末尾
				else {
					allInterceptors.addAll(Arrays.asList(commonInterceptors));
				}
			}
		}

		// 把每一个Advisor都用advisorAdapterRegistry.wrap()包装一下~~~~
		// 注意wrap方法，默认只支持那三种类型的Advice转换为Advisor的~~~
		Advisor[] advisors = new Advisor[allInterceptors.size()];
		for (int i = 0; i < allInterceptors.size(); i++) {
			advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
		}
		return advisors;
	}

	/**
	 * Resolves the specified interceptor names to Advisor objects.
	 * @see #setInterceptorNames
	 */
	private Advisor[] resolveInterceptorNames() {
		// 从BeanFactory中解析InterceptorNames名字对应的bean
		// 返回这个 Advisors[]
		// 每次都是重新解析interceptorNames，并不会有缓存
		BeanFactory bf = this.beanFactory;
		ConfigurableBeanFactory cbf = (bf instanceof ConfigurableBeanFactory ? (ConfigurableBeanFactory) bf : null);
		List<Advisor> advisors = new ArrayList<>();
		for (String beanName : this.interceptorNames) {
			if (cbf == null || !cbf.isCurrentlyInCreation(beanName)) {
				Assert.state(bf != null, "BeanFactory required for resolving interceptor names");
				Object next = bf.getBean(beanName);
				advisors.add(this.advisorAdapterRegistry.wrap(next));
			}
		}
		return advisors.toArray(new Advisor[0]);
	}

	/**
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory a ProxyFactory that is already configured with
	 * TargetSource and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}


	/**
	 * Return whether the given bean is to be proxied, what additional
	 * advices (e.g. AOP Alliance interceptors) and advisors to apply.
	 * @param beanClass the class of the bean to advise
	 * @param beanName the name of the bean
	 * @param customTargetSource the TargetSource returned by the
	 * {@link #getCustomTargetSource} method: may be ignored.
	 * Will be {@code null} if no custom target source is in use.
	 * @return an array of additional interceptors for the particular bean;
	 * or an empty array if no additional interceptors but just the common ones;
	 * or {@code null} if no proxy at all, not even with the common interceptors.
	 * See constants DO_NOT_PROXY and PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS.
	 * @throws BeansException in case of errors
	 * @see #DO_NOT_PROXY
	 * @see #PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
	 */
	@Nullable
	protected abstract Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
			@Nullable TargetSource customTargetSource) throws BeansException; // todo

}
