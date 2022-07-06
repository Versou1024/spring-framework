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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.Interceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.UnknownAdviceTypeException;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * {@link org.springframework.beans.factory.FactoryBean} implementation that builds an
 * AOP proxy based on beans in Spring {@link org.springframework.beans.factory.BeanFactory}.
 *
 * <p>{@link org.aopalliance.intercept.MethodInterceptor MethodInterceptors} and
 * {@link org.springframework.aop.Advisor Advisors} are identified by a list of bean
 * names in the current bean factory, specified through the "interceptorNames" property.
 * The last entry in the list can be the name of a target bean or a
 * {@link org.springframework.aop.TargetSource}; however, it is normally preferable
 * to use the "targetName"/"target"/"targetSource" properties instead.
 *
 * <p>Global interceptors and advisors can be added at the factory level. The specified
 * ones are expanded in an interceptor list where an "xxx*" entry is included in the
 * list, matching the given prefix with the bean names (e.g. "global*" would match
 * both "globalBean1" and "globalBean2", "*" all defined interceptors). The matching
 * interceptors get applied according to their returned order value, if they implement
 * the {@link org.springframework.core.Ordered} interface.
 *
 * <p>Creates a JDK proxy when proxy interfaces are given, and a CGLIB proxy for the
 * actual target class if not. Note that the latter will only work if the target class
 * does not have final methods, as a dynamic subclass will be created at runtime.
 *
 * <p>It's possible to cast a proxy obtained from this factory to {@link Advised},
 * or to obtain the ProxyFactoryBean reference and programmatically manipulate it.
 * This won't work for existing prototype references, which are independent. However,
 * it will work for prototypes subsequently obtained from the factory. Changes to
 * interception will work immediately on singletons (including existing references).
 * However, to change interfaces or target it's necessary to obtain a new instance
 * from the factory. This means that singleton instances obtained from the factory
 * do not have the same object identity. However, they do have the same interceptors
 * and target, and changing any reference will change all objects.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #setInterceptorNames
 * @see #setProxyInterfaces
 * @see org.aopalliance.intercept.MethodInterceptor
 * @see org.springframework.aop.Advisor
 * @see Advised
 */
@SuppressWarnings("serial")
public class ProxyFactoryBean extends ProxyCreatorSupport implements FactoryBean<Object>, BeanClassLoaderAware, BeanFactoryAware {
	/**
	 * ProxyCreatorSupport有三个子类：一点点发展起来的
	 * ProxyFactory 是只能通过代码硬编码进行编写 一般都是给spring自己使用。
	 * ProxyFactoryBean 是将我们的AOP和IOC融合起来，
	 * AspectJProxyFactory 是目前大家最常用的 起到集成AspectJ和Spring
	 * 
	 * 
	 * 该接口实现了FactoryBean和BeanFactoryAware接口哦
	 */

	/**
	 * This suffix in a value in an interceptor list indicates to expand globals.
	 */
	public static final String GLOBAL_SUFFIX = "*";


	protected final Log logger = LogFactory.getLog(getClass());

	// 引用的 bean 应该是 Interceptor、Advisor 或 Advice 类型
	// note: interceptorNames 引用的bean 应该是 Interceptor、Advisor 或 Advice 类型。
	// interceptorNames列表中的最后一个条目可以是ioc容器中任何bean的名称。
	// 如果它既不是 Advice 也不是 Advisor，则添加一个新的 SingletonTargetSource 来包装它。
	// 如果设置了“target”或“targetSource”或“targetName”属性，则无法使用此类目标 bean，
	// ❗️❗️❗️ 根据interceptorNames查询出来的Interceptor如果是Advice或者MethodInterceptor都将转换为
	// DefaultPointcutAdvisor -> 意味着它的连接点将是POINT.TRUE,即任何类的任何方法哦
	// 本身就是Advisor将直接返回哦
	@Nullable
	private String[] interceptorNames;

	// 设置目标bean的名称。这是在“interceptorNames”数组末尾指定目标名称的替代方法。
	// 您还可以分别通过“target”/“targetSource”属性直接指定目标对象或TargetSource对象
	@Nullable
	private String targetName; // 目标类的名字

	// 设置是否自动检测代理接口。
	// 默认为“真”。如果未指定接口，请关闭此标志以为完整的目标类创建 CGLIB 代理。
	private boolean autodetectInterfaces = true;

	// 设置单例属性的值。管理这个ProxyFactoryBean工厂是否应该总是返回相同的代理对象
	private boolean singleton = true;

	// 将Advice适配为MethodInterceptor的Advisor类型来使用
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

	// 是否在创建玩完单例/原型的代理对象后是哦福需要冻结ProxyConfig的开关
	private boolean freezeProxy = false;

	// proxyClassLoader 用来指定获取代理类时使用的类加载器,即AopProxy.getProxy(ClassLoader)方法
	@Nullable
	private transient ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();

	private transient boolean classLoaderConfigured = false;

	// ioc容器
	@Nullable
	private transient BeanFactory beanFactory;

	// advisorChain是否已经被初始化了，只会初始化一次
	private boolean advisorChainInitialized = false; 

	// 如果是单例的代理对象,那么使用singletonInstance来缓存创建单例Bean代理对象哦
	@Nullable
	private Object singletonInstance;

	// ProxyFactoryBean没有构造器

	/**
	 * Set the names of the interfaces we're proxying. If no interface
	 * is given, a CGLIB for the actual class will be created.
	 * <p>This is essentially equivalent to the "setInterfaces" method,
	 * but mirrors TransactionProxyFactoryBean's "setProxyInterfaces".
	 * @see #setInterfaces
	 * @see AbstractSingletonProxyFactoryBean#setProxyInterfaces
	 */
	public void setProxyInterfaces(Class<?>[] proxyInterfaces) throws ClassNotFoundException {
		// 设置我们正在代理的接口的名称。如果没有给出接口，则为实际类创建一个 CGLIB。
		setInterfaces(proxyInterfaces);
	}

	/**
	 * Set the list of Advice/Advisor bean names. This must always be set
	 * to use this factory bean in a bean factory.
	 * <p>The referenced beans should be of type Interceptor, Advisor or Advice
	 * The last entry in the list can be the name of any bean in the factory.
	 * If it's neither an Advice nor an Advisor, a new SingletonTargetSource
	 * is added to wrap it. Such a target bean cannot be used if the "target"
	 * or "targetSource" or "targetName" property is set, in which case the
	 * "interceptorNames" array must contain only Advice/Advisor bean names.
	 * <p><b>NOTE: Specifying a target bean as final name in the "interceptorNames"
	 * list is deprecated and will be removed in a future Spring version.</b>
	 * Use the {@link #setTargetName "targetName"} property instead.
	 * @see org.aopalliance.intercept.MethodInterceptor
	 * @see org.springframework.aop.Advisor
	 * @see org.aopalliance.aop.Advice
	 * @see org.springframework.aop.target.SingletonTargetSource
	 */
	public void setInterceptorNames(String... interceptorNames) {
		this.interceptorNames = interceptorNames;
	}

	/**
	 * Set the name of the target bean. This is an alternative to specifying
	 * the target name at the end of the "interceptorNames" array.
	 * <p>You can also specify a target object or a TargetSource object
	 * directly, via the "target"/"targetSource" property, respectively.
	 * @see #setInterceptorNames(String[])
	 * @see #setTarget(Object)
	 * @see #setTargetSource(org.springframework.aop.TargetSource)
	 */
	public void setTargetName(String targetName) {
		this.targetName = targetName;
	}

	/**
	 * Set whether to autodetect proxy interfaces if none specified.
	 * <p>Default is "true". Turn this flag off to create a CGLIB
	 * proxy for the full target class if no interfaces specified.
	 * @see #setProxyTargetClass
	 */
	public void setAutodetectInterfaces(boolean autodetectInterfaces) {
		this.autodetectInterfaces = autodetectInterfaces;
	}

	/**
	 * Set the value of the singleton property. Governs whether this factory
	 * should always return the same proxy instance (which implies the same target)
	 * or whether it should return a new prototype instance, which implies that
	 * the target and interceptors may be new instances also, if they are obtained
	 * from prototype bean definitions. This allows for fine control of
	 * independence/uniqueness in the object graph.
	 */
	public void setSingleton(boolean singleton) {
		this.singleton = singleton;
	}

	/**
	 * Specify the AdvisorAdapterRegistry to use.
	 * Default is the global AdvisorAdapterRegistry.
	 * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
	 */
	public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
		this.advisorAdapterRegistry = advisorAdapterRegistry;
	}

	@Override
	public void setFrozen(boolean frozen) {
		this.freezeProxy = frozen;
	}

	/**
	 * Set the ClassLoader to generate the proxy class in.
	 * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the
	 * containing BeanFactory for loading all bean classes. This can be
	 * overridden here for specific proxies.
	 */
	public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
		this.proxyClassLoader = classLoader;
		this.classLoaderConfigured = (classLoader != null);
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		if (!this.classLoaderConfigured) {
			this.proxyClassLoader = classLoader;
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		checkInterceptorNames();
	}


	/**
	 * Return a proxy. Invoked when clients obtain beans from this factory bean.
	 * Create an instance of the AOP proxy to be returned by this factory.
	 * The instance will be cached for a singleton, and create on each call to
	 * {@code getObject()} for a proxy.
	 * @return a fresh AOP proxy reflecting the current state of this factory
	 */
	@Override
	@Nullable
	public Object getObject() throws BeansException {
		// ❗️❗️❗️ 从ProxyFactoryBean中获取代理对象的getObject()方法
		
		
		// 1. 初始化advisorChain
		// 将interceptorNames转换为advisor，并且添加到超类AdvisedSupport.advisors集合中
		// 初始化完advisorChain后，就可以开始获取Proxy代理对象
		initializeAdvisorChain();
		
		// 1.1 获取单例的代理对象 -- 也是核心，如何生成代理对象的哦
		if (isSingleton()) {
			return getSingletonInstance();
		}
		// 1.2 获取一个新的原型的代理对象出来
		else {
			if (this.targetName == null) {
				logger.info("Using non-singleton proxies with singleton targets is often undesirable. " +
						"Enable prototype proxies by setting the 'targetName' property.");
			}
			return newPrototypeInstance();
		}
	}

	/**
	 * Return the type of the proxy. Will check the singleton instance if
	 * already created, else fall back to the proxy interface (in case of just
	 * a single one), the target bean type, or the TargetSource's target class.
	 * @see org.springframework.aop.TargetSource#getTargetClass
	 */
	@Override
	public Class<?> getObjectType() {
		// 当前FactoryBean中代理的对象是谁 -- 返回代理的类型。
		
		// 1. 是否已经实例化啦,如果实话,就直接获取getClass()
		synchronized (this) {
			if (this.singletonInstance != null) {
				return this.singletonInstance.getClass();
			}
		}
		// 2. 没有实例化的话
		// a:只有一个接口就直接返回该接口的class
		// b:多个接口的时候使用复合接口返回
		// c:没有任何接口,就是用targetName在ioc容器对应的beanType
		// d: targetClass()
		Class<?>[] ifcs = getProxiedInterfaces();
		if (ifcs.length == 1) {
			return ifcs[0];
		}
		else if (ifcs.length > 1) {
			return createCompositeInterface(ifcs);
		}
		else if (this.targetName != null && this.beanFactory != null) {
			return this.beanFactory.getType(this.targetName);
		}
		else {
			return getTargetClass();
		}
	}

	@Override
	public boolean isSingleton() {
		return this.singleton;
	}


	/**
	 * Create a composite interface Class for the given interfaces,
	 * implementing the given interfaces in one single Class.
	 * <p>The default implementation builds a JDK proxy class for the
	 * given interfaces.
	 * @param interfaces the interfaces to merge
	 * @return the merged interface as Class
	 * @see java.lang.reflect.Proxy#getProxyClass
	 */
	protected Class<?> createCompositeInterface(Class<?>[] interfaces) {
		return ClassUtils.createCompositeInterface(interfaces, this.proxyClassLoader);
	}

	/**
	 * Return the singleton instance of this class's proxy object,
	 * lazily creating it if it hasn't been created already.
	 * @return the shared singleton proxy
	 */
	private synchronized Object getSingletonInstance() {
		// ❗️❗️❗️
		
		// 1. 单例Proxy对象未实例化
		if (this.singletonInstance == null) {
			// 1.1 获取targetSource
			this.targetSource = freshTargetSource();
			// 1.2 默认是需要检查interface，且 AdvisedSupport.interfaces 为空，且未指定使用Cglib的情况 -- 
			// 就需要检查所有interfaces，设置进去
			if (this.autodetectInterfaces && getProxiedInterfaces().length == 0 && !isProxyTargetClass()) {
				Class<?> targetClass = getTargetClass();
				if (targetClass == null) {
					throw new FactoryBeanNotInitializedException("Cannot determine target class for proxy");
				}
				setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
			}
			// 1.3 是否在创建单例的代理对象后需要冻结ProxyConfig勒
			super.setFrozen(this.freezeProxy);
			// 1.4 获取并缓存单例代理对象
			this.singletonInstance = getProxy(createAopProxy());
		}
		return this.singletonInstance;
	}

	/**
	 * Create a new prototype instance of this class's created proxy object,
	 * backed by an independent AdvisedSupport configuration.
	 * @return a totally independent proxy, whose advice we may manipulate in isolation
	 */
	private synchronized Object newPrototypeInstance() {
		// 1. 在创建原型的代理对象的情况下，需要给代理Proxy新建一个独立的配置实例ProxyCreatorSupport。
		// 在这种情况下，没有代理将拥有此对象配置的实例，但会有独立的副本。
		ProxyCreatorSupport copy = new ProxyCreatorSupport(getAopProxyFactory());

		// 2. 获取targetSource对象
		TargetSource targetSource = freshTargetSource();
		// 3. 将当前ProxyFactoryBean的配置抄袭一份到硬编码创建代理对象的ProxyCreatorSupport中去
		// ❗️❗️❗️ -> 注意这里原型的代理对象原型的Interceptor需要通过freshAdvisorChain()去刷新的哦
		copy.copyConfigurationFrom(this, targetSource, freshAdvisorChain());
		// 4. 自动检查interface开关已开启\没有指定代理的接口\且没有指定使用Cglib代理模式
		if (this.autodetectInterfaces && getProxiedInterfaces().length == 0 && !isProxyTargetClass()) {
			Class<?> targetClass = targetSource.getTargetClass();
			if (targetClass != null) {
				copy.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
			}
		}
		// 5. 是哦福需要冻结配置
		copy.setFrozen(this.freezeProxy);
		// 6. 使用copy创建出AopProxy,然后创建出代理对象哦
		return getProxy(copy.createAopProxy());
	}

	/**
	 * Return the proxy object to expose.
	 * <p>The default implementation uses a {@code getProxy} call with
	 * the factory's bean class loader. Can be overridden to specify a
	 * custom class loader.
	 * @param aopProxy the prepared AopProxy instance to get the proxy from
	 * @return the proxy object to expose
	 * @see AopProxy#getProxy(ClassLoader)
	 */
	protected Object getProxy(AopProxy aopProxy) {
		return aopProxy.getProxy(this.proxyClassLoader);
	}

	/**
	 * Check the interceptorNames list whether it contains a target name as final element.
	 * If found, remove the final name from the list and set it as targetName.
	 */
	private void checkInterceptorNames() {
		if (!ObjectUtils.isEmpty(this.interceptorNames)) {
			String finalName = this.interceptorNames[this.interceptorNames.length - 1];
			if (this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE) {
				// The last name in the chain may be an Advisor/Advice or a target/TargetSource.
				// Unfortunately we don't know; we must look at type of the bean.
				if (!finalName.endsWith(GLOBAL_SUFFIX) && !isNamedBeanAnAdvisorOrAdvice(finalName)) {
					// The target isn't an interceptor.
					this.targetName = finalName;
					if (logger.isDebugEnabled()) {
						logger.debug("Bean with name '" + finalName + "' concluding interceptor chain " +
								"is not an advisor class: treating it as a target or TargetSource");
					}
					this.interceptorNames = Arrays.copyOf(this.interceptorNames, this.interceptorNames.length - 1);
				}
			}
		}
	}

	/**
	 * Look at bean factory metadata to work out whether this bean name,
	 * which concludes the interceptorNames list, is an Advisor or Advice,
	 * or may be a target.
	 * @param beanName bean name to check
	 * @return {@code true} if it's an Advisor or Advice
	 */
	private boolean isNamedBeanAnAdvisorOrAdvice(String beanName) {
		Assert.state(this.beanFactory != null, "No BeanFactory set");
		Class<?> namedBeanClass = this.beanFactory.getType(beanName);
		if (namedBeanClass != null) {
			return (Advisor.class.isAssignableFrom(namedBeanClass) || Advice.class.isAssignableFrom(namedBeanClass));
		}
		// Treat it as an target bean if we can't tell.
		if (logger.isDebugEnabled()) {
			logger.debug("Could not determine type of bean with name '" + beanName +
					"' - assuming it is neither an Advisor nor an Advice");
		}
		return false;
	}

	/**
	 * Create the advisor (interceptor) chain. Advisors that are sourced
	 * from a BeanFactory will be refreshed each time a new prototype instance
	 * is added. Interceptors added programmatically through the factory API
	 * are unaffected by such changes.
	 */
	private synchronized void initializeAdvisorChain() throws AopConfigException, BeansException {
		// 创建 advisor (interceptor) chain。每次添加新的原型实例时，都会刷新来自 BeanFactory 的advisor。
		// 通过工厂 API 以编程方式添加的拦截器不受此类更改的影响。

		// 1. 第一次需要初始化
		if (!this.advisorChainInitialized && !ObjectUtils.isEmpty(this.interceptorNames)) {
			// 1.1 BeanFactory不允许为空的
			if (this.beanFactory == null) {
				throw new IllegalStateException("No BeanFactory available anymore (probably due to serialization) " +
						"- cannot resolve interceptor names " + Arrays.asList(this.interceptorNames));
			}

			// 1.2 不允许interceptorNames中最后一个interceptor是全局的，否则报错
			if (this.interceptorNames[this.interceptorNames.length - 1].endsWith(GLOBAL_SUFFIX) &&
					this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE) {
				throw new AopConfigException("Target required after globals");
			}

			// 1.3 从 bean 名称实现拦截器链。
			for (String name : this.interceptorNames) {
				// 1.3.1 如果拦截器的名称是以*结尾的，说明它要去全局里面都搜索出来
				// 全局：去自己容器以及父容器中找，类型为Advisor.class以及Interceptor.class所有的，名称是以这个名称为开头的prefix的Bean.
				// 最终也一样交给addAdvisorOnChainCreation(bean, name);   相当于一个批量处理吧  在特殊场景还是很有用处的
				if (name.endsWith(GLOBAL_SUFFIX)) {
					if (!(this.beanFactory instanceof ListableBeanFactory)) {
						throw new AopConfigException(
								"Can only use global advisors or interceptors with a ListableBeanFactory");
					}
					// 添加全局的指定前缀的Interceptor以及Advisor
					// 并且将MethodInterceptor或者Advice都包装为相应的DefaultPointcutAdvisor
					// 然后调用addAdvisor()添加
					addGlobalAdvisors((ListableBeanFactory) this.beanFactory,
							name.substring(0, name.length() - GLOBAL_SUFFIX.length()));
				}

				// 1.3.2 绝大部分情况肯定都走这里：精确匹配
				else {
					// 1.3.2.1 如果我们到达这里，我们需要添加一个命名拦截器。我们必须检查它是单例还是原型。
					Object advice;
					// 1.3.2.2 singleton默认为true,或者拦截器为单例的,都直接拿出来使用
					if (this.singleton || this.beanFactory.isSingleton(name)) {
						// Add the real Advisor/Advice to the chain.
						// 从容器里把这个Bean拿出来~~~~~~~~~~~~~
						advice = this.beanFactory.getBean(name);
					}
					// 1.3.2.3 多例的--这里每次都是new一个新的
					else {
						// 这里只是原型拦截器的占位符的Advisor
						// ❗️❗️❗️ 将在创建原型对象时调用freshAdvisorChain(),将这个占位符替换为最终使用的拦截器
						advice = new PrototypePlaceholderAdvisor(name);
					}
					// 这个方法的作用还挺大的：将advice对象添加到通知器链中
					// 方法中首先会调用namedBeanToAdvisor(next)方法，将从ioc容器获取的普通对象转换成通知器Advisor对象。  详细如下：
					addAdvisorOnChainCreation(advice);
				}
			}

			// 初始化完成
			this.advisorChainInitialized = true;
		}
	}


	/**
	 * Return an independent advisor chain.
	 * We need to do this every time a new prototype instance is returned,
	 * to return distinct instances of prototype Advisors and Advices.
	 */
	private List<Advisor> freshAdvisorChain() {
		// 在初始化拦截器initializeAdvisorChain()方法的代码中有这样一行:
		// advice = new PrototypePlaceholderAdvisor(name); -- 针对原型代理对象以及原型拦截器对象
		// 该方法保证: 每次在创建原型代理对象时,使用的Advisor都是刷新出来的新的原型拦截器
		
		// 1. 获取所有的Advisor进行遍历
		Advisor[] advisors = getAdvisors();
		List<Advisor> freshAdvisors = new ArrayList<>(advisors.length);
		for (Advisor advisor : advisors) {
			// 1.1 处理PrototypePlaceholderAdvisor
			if (advisor instanceof PrototypePlaceholderAdvisor) {
				PrototypePlaceholderAdvisor pa = (PrototypePlaceholderAdvisor) advisor;
				if (logger.isDebugEnabled()) {
					logger.debug("Refreshing bean named '" + pa.getBeanName() + "'");
				}
				// Replace the placeholder with a fresh prototype instance resulting from a getBean lookup
				if (this.beanFactory == null) {
					throw new IllegalStateException("No BeanFactory available anymore (probably due to " +
							"serialization) - cannot resolve prototype advisor '" + pa.getBeanName() + "'");
				}
				// 1.1.1 从pa中获取原型拦截器的名字,然后从BeanFactory中获取出来 ❗️❗️❗️-> 这样就保证每次在创建原型代理对象时,使用的Advisor都是刷新出来的新的原型拦截器
				Object bean = this.beanFactory.getBean(pa.getBeanName());
				// 1.1.2 对拦截器的bean进行一下包装
				Advisor refreshedAdvisor = namedBeanToAdvisor(bean);
				freshAdvisors.add(refreshedAdvisor);
			}
			// 1.2 非PrototypePlaceholderAdvisor类型的,直接加入到freshAdvisors
			else {
				freshAdvisors.add(advisor);
			}
		}
		return freshAdvisors;
	}

	/**
	 * Add all global interceptors and pointcuts.
	 */
	private void addGlobalAdvisors(ListableBeanFactory beanFactory, String prefix) {
		// 添加所有全局的拦截器和切入点 -- prefix是全局即父子ioc容器中以prefix开头的拦截器都找出来
		
		// 1. 找出所有的Advisor\Interceptor类型的
		// Interceptor类型包括MethodInterceptor和ConstructorInterceptor两种,后者在Spring中没有使用过
		String[] globalAdvisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Advisor.class);
		String[] globalInterceptorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Interceptor.class);
		// 2. 检查所有的Advisor\Interceptor类型的bean的前缀是以prefix开头的
		if (globalAdvisorNames.length > 0 || globalInterceptorNames.length > 0) {
			List<Object> beans = new ArrayList<>(globalAdvisorNames.length + globalInterceptorNames.length);
			for (String name : globalAdvisorNames) {
				if (name.startsWith(prefix)) {
					beans.add(beanFactory.getBean(name));
				}
			}
			for (String name : globalInterceptorNames) {
				if (name.startsWith(prefix)) {
					beans.add(beanFactory.getBean(name));
				}
			}
			// 3. 同样的对于操作类型的拦截器也是可以定义执行顺序的
			AnnotationAwareOrderComparator.sort(beans);
			for (Object bean : beans) {
				addAdvisorOnChainCreation(bean);
			}
		}
	}

	/**
	 * Invoked when advice chain is created.
	 * <p>Add the given advice, advisor or object to the interceptor list.
	 * Because of these three possibilities, we can't type the signature
	 * more strongly.
	 * @param next advice, advisor or target object
	 */
	private void addAdvisorOnChainCreation(Object next) {
		// 如有必要，我们需要转换为Advisor，以便我们的source reference与我们从超类拦截器中找到的内容相匹配。
		// 调用了: this.advisorAdapterRegistry.wrap(next); 方法
		// 1. Advisor无须包装直接返回
		// 2. MethodInterceptor包装为DefaultPointcutAdvisor后返回
		// 3. 其余的例如BeforeMethodAdvice等advice类型包装为DefaultPointcutAdvisor后返回
		addAdvisor(namedBeanToAdvisor(next));
	}

	/**
	 * Return a TargetSource to use when creating a proxy. If the target was not
	 * specified at the end of the interceptorNames list, the TargetSource will be
	 * this class's TargetSource member. Otherwise, we get the target bean and wrap
	 * it in a TargetSource if necessary.
	 */
	private TargetSource freshTargetSource() {
		// 获取TargetSource

		// 1. 没有指定targetName,就直接获取targetSource
		if (this.targetName == null) {
			// 不刷新目标：“interceptorNames”中未指定 bean 名称
			// beanName没有定义，就无法获取TargetSource，
			return this.targetSource;
		}
		// 2. 从ioc容器中根据targetName获取目标对象
		// 若bean就是targetSource就直接返回,否则使用SingletonTargetSource包装target
		else {
			if (this.beanFactory == null) {
				throw new IllegalStateException("No BeanFactory available anymore (probably due to serialization) " +
						"- cannot resolve target with name '" + this.targetName + "'");
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Refreshing target with name '" + this.targetName + "'");
			}
			Object target = this.beanFactory.getBean(this.targetName);
			return (target instanceof TargetSource ? (TargetSource) target : new SingletonTargetSource(target));
		}
	}

	/**
	 * Convert the following object sourced from calling getBean() on a name in the
	 * interceptorNames array to an Advisor or TargetSource.
	 */
	private Advisor namedBeanToAdvisor(Object next) {
		try {
			return this.advisorAdapterRegistry.wrap(next);
		}
		catch (UnknownAdviceTypeException ex) {
			// We expected this to be an Advisor or Advice,
			// but it wasn't. This is a configuration error.
			throw new AopConfigException("Unknown advisor type " + next.getClass() +
					"; can only include Advisor or Advice type beans in interceptorNames chain " +
					"except for last entry which may also be target instance or TargetSource", ex);
		}
	}

	/**
	 * Blow away and recache singleton on an advice change.
	 */
	@Override
	protected void adviceChanged() {
		super.adviceChanged();
		if (this.singleton) {
			logger.debug("Advice has changed; re-caching singleton instance");
			synchronized (this) {
				this.singletonInstance = null;
			}
		}
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.proxyClassLoader = ClassUtils.getDefaultClassLoader();
	}


	/**
	 * Used in the interceptor chain where we need to replace a bean with a prototype
	 * on creating a proxy.
	 */
	private static class PrototypePlaceholderAdvisor implements Advisor, Serializable {
		// ❗️❗️❗️在拦截器链中使用，如果指定的InterceptorName对应的拦截器是原型的,并且ProxyFactoryBean创建的代理对象也要求是原型的
		// 那么我们就需要在创建代理时用原型替换bean。

		private final String beanName;

		private final String message;

		public PrototypePlaceholderAdvisor(String beanName) {
			this.beanName = beanName;
			this.message = "Placeholder for prototype Advisor/Advice with bean name '" + beanName + "'";
		}

		public String getBeanName() {
			return this.beanName;
		}

		@Override
		public Advice getAdvice() {
			throw new UnsupportedOperationException("Cannot invoke methods: " + this.message);
		}

		@Override
		public boolean isPerInstance() {
			throw new UnsupportedOperationException("Cannot invoke methods: " + this.message);
		}

		@Override
		public String toString() {
			return this.message;
		}
	}

}
