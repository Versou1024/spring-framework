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

package org.springframework.aop.aspectj.annotation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJProxyUtils;
import org.springframework.aop.aspectj.SimpleAspectInstanceFactory;
import org.springframework.aop.framework.ProxyCreatorSupport;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * AspectJ-based proxy factory, allowing for programmatic building
 * of proxies which include AspectJ aspects (code style as well
 * Java 5 annotation style).
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 * @see #addAspect(Object)
 * @see #addAspect(Class)
 * @see #getProxy()
 * @see #getProxy(ClassLoader)
 * @see org.springframework.aop.framework.ProxyFactory
 */
@SuppressWarnings("serial")
public class AspectJProxyFactory extends ProxyCreatorSupport {
	/**
	 * ProxyCreatorSupport有三个子类：一点点发展起来的
	 * ProxyFactory 是只能通过代码硬编码进行编写 一般都是给spring自己使用。
	 * ProxyFactoryBean 是将我们的AOP和IOC融合起来，
	 * AspectJProxyFactory 是目前大家最常用的 起到集成AspectJ和Spring
	 * 
	 * ProxyFactory 与 ProxyFactoryBean 仍然在 org.springframework.aop.framework package 下
	 * 而 AspectJProxyFactory 确是在org.springframework.aop.aspectj.annotation
	 * 
	 * AspectJProxyFactory 主要是注意: addAspect()方法
	 * getProxy()方法就是createAopProxy().getProxy()不做过多参数
	 */

	// 补充一下:
	// 首先，为了让大家能更有效的理解AOP，先带大家过一下AOP中的术语：
	// 
	// 切面（Aspect)：指关注点模块化，这个关注点可能会横切多个对象。事务管理是企业级Java应用中有关横切关注点的例子。在Spring AOP中，切面可以使用在普通类中以＠Aspect注解来实现。
	// 连接点（Join point)：在Spring AOP中，一个连接点总是代表一个方法的执行，其实就代表增强的方法。
	// 通知（Advice)：在切面的某个特定的连接点上执行的动作。通知有多种类型，包括around， before和after等等。许多AOP框架，包括Spring在内，都是以拦截器做通知模型的，并维护着一个以连接点为中心的拦截器链。
	// 目标对象（Target)：目标对象指将要被增强的对象。即包含主业务逻辑的类的对象。
	// 切点（Pointcut)：匹配连接点的断言。通知和切点表达式相关联，并在满足这个切点的连接点上运行（例如，当执行某个特定名称的方法时）。切点表达式如何和连接点匹配是AOP的核心：Spring默认使用AspectJ切点语义。
	// 顾问（Advisor)： 顾问是Advice的一种包装体现，Advisor是Pointcut以及Advice的一个结合，用来管理Advice和Pointcut。
	// 织入（Weaving)：将通知切入连接点的过程叫织入
	// 引入（Introductions)：可以将其他接口和实现动态引入到targetClass中

	// Spring AOP与AspectJ的关系
	// 两者都可以用来实现动态代理。不同的是：
	// 1. AspectJ基于asm做字节码替换来实现AOP，可以在 类编译期 / 类加载期 织入切面。功能更强大，但是无论是从实现还是从使用上来说也更复杂。
	// 2.Spring AOP基于JDK动态代理和CGLIB动态代理实现AOP，因此只能在 运行期 织入切面，但是切点表达式使用了AspectJ。要开启AspectJ表达式的支持，需要引入aspectjweaver包。
	// 3.由于Spring AOP基于动态代理实现，因此只能增强方法，且只能增强能被覆写(@Override)的方法。而AspectJ不光能增强private的方法，还能增强字段、构造方法。
			
	// AspectJ支持的表达式类型可以通过类org.aspectj.weaver.tools.PointcutPrimitive查看，
	// Spring AOP并没有支持所有AspectJ表达式类型，而是选择了其中最实用的一些进行了支持。具体可以查看org.springframework.aop.aspectj.AspectJExpressionPointcut。

	private static final Map<Class<?>, Object> aspectCache = new ConcurrentHashMap<>();

	private final AspectJAdvisorFactory aspectFactory = new ReflectiveAspectJAdvisorFactory();


	/**
	 * Create a new AspectJProxyFactory.
	 */
	public AspectJProxyFactory() {
	}

	/**
	 * Create a new AspectJProxyFactory.
	 * <p>Will proxy all interfaces that the given target implements.
	 * @param target the target object to be proxied
	 */
	public AspectJProxyFactory(Object target) {
		Assert.notNull(target, "Target object must not be null");
		// 获取target所有可用接口 -- 返回给定实例target实现的所有接口，其中包括由超类实现的接口。
		setInterfaces(ClassUtils.getAllInterfaces(target));
		// 设置目标target -- 其中会构造TargetSource哦
		setTarget(target);
	}

	/**
	 * Create a new {@code AspectJProxyFactory}.
	 * No target, only interfaces. Must add interceptors.
	 */
	public AspectJProxyFactory(Class<?>... interfaces) {
		setInterfaces(interfaces);
	}


	/**
	 * Add the supplied aspect instance to the chain. The type of the aspect instance
	 * supplied must be a singleton aspect. True singleton lifecycle is not honoured when
	 * using this method - the caller is responsible for managing the lifecycle of any
	 * aspects added in this way.
	 * @param aspectInstance the AspectJ aspect instance
	 */
	public void addAspect(Object aspectInstance) {
		// aspectInstance: aspect切面的实例
		// [切面名就是类名]
		
		Class<?> aspectClass = aspectInstance.getClass();
		String aspectName = aspectClass.getName();
		// 1. 创建aspectMetadata元数据
		AspectMetadata am = createAspectMetadata(aspectClass, aspectName);
		if (am.getAjType().getPerClause().getKind() != PerClauseKind.SINGLETON) {
			throw new IllegalArgumentException(
					"Aspect class [" + aspectClass.getName() + "] does not define a singleton aspect");
		}
		// 2. AspectInstanceFactory 中获取
		// 为这个切面实例aspectInstance创建一个SingletonMetadataAwareAspectInstanceFactory --> 其中持有对应的切面类的元数据AspectMetadata
		// ❗️❗️❗️ 后面就方便直接从SingletonMetadataAwareAspectInstanceFactory获取Aspect切面类的实例对象,以及元数据对象AspectMetadata
		addAdvisorsFromAspectInstanceFactory(new SingletonMetadataAwareAspectInstanceFactory(aspectInstance, aspectName));
	}

	/**
	 * Add an aspect of the supplied type to the end of the advice chain.
	 * @param aspectClass the AspectJ aspect class
	 */
	public void addAspect(Class<?> aspectClass) {
		// aspectClass 切面类的Class
		
		String aspectName = aspectClass.getName();
		AspectMetadata am = createAspectMetadata(aspectClass, aspectName);
		MetadataAwareAspectInstanceFactory instanceFactory = createAspectInstanceFactory(am, aspectClass, aspectName);
		addAdvisorsFromAspectInstanceFactory(instanceFactory);
	}


	/**
	 * Add all {@link Advisor Advisors} from the supplied {@link MetadataAwareAspectInstanceFactory}
	 * to the current chain. Exposes any special purpose {@link Advisor Advisors} if needed.
	 * @see AspectJProxyUtils#makeAdvisorChainAspectJCapableIfNecessary(List)
	 */
	private void addAdvisorsFromAspectInstanceFactory(MetadataAwareAspectInstanceFactory instanceFactory) {
		// instanceFactory 是为切面类实例创建的MetadataAwareAspectInstanceFactory工厂
		// 可以从中获取Aspect实例对象,以及AspectMetadata元数据对象
		
		// 1. ❗️❗️❗️获取相应的Advisor
		// 很复杂 -> 主要是将切面类中的@PointCut\@Before\@After\@Around\@AfterReturning\@AfterThrowing标注的通知增强方法
		// 给转换为 InstantiationModelAwarePointcutAdvisorImpl [这是一个PointcutAdvisor,其pointCut是AspectJExpressionPointcut取决于上述注解的AspectJ的语法,
		// 而Advice则却取决于不同注解,会生成不同的Advice,完成其参数适配后,调用通知增强方法]
		List<Advisor> advisors = this.aspectFactory.getAdvisors(instanceFactory);
		Class<?> targetClass = getTargetClass();
		Assert.state(targetClass != null, "Unresolvable target class");
		// 2. 查找适合targetClass的advisors
		// ❗️❗️❗️
		// 注入的切面类中有哪些通知增强方法即对应的Advisor是适合当前的targetClass
		advisors = AopUtils.findAdvisorsThatCanApply(advisors, targetClass);
		// 3. 对于AspectJ的Advisor查看是否有必要添加一个ExposeInvocationInterceptor在最前面
		AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary(advisors);
		// 4. advisors 根据@Order的值进行排序
		AnnotationAwareOrderComparator.sort(advisors);
		addAdvisors(advisors);
	}

	/**
	 * Create an {@link AspectMetadata} instance for the supplied aspect type.
	 */
	private AspectMetadata createAspectMetadata(Class<?> aspectClass, String aspectName) {
		AspectMetadata am = new AspectMetadata(aspectClass, aspectName); // 注意：这里面有很多东西
		if (!am.getAjType().isAspect()) {
			throw new IllegalArgumentException("Class [" + aspectClass.getName() + "] is not a valid aspect type");
		}
		return am;
	}

	/**
	 * Create a {@link MetadataAwareAspectInstanceFactory} for the supplied aspect type. If the aspect type
	 * has no per clause, then a {@link SingletonMetadataAwareAspectInstanceFactory} is returned, otherwise
	 * a {@link PrototypeAspectInstanceFactory} is returned.
	 */
	private MetadataAwareAspectInstanceFactory createAspectInstanceFactory(
			AspectMetadata am, Class<?> aspectClass, String aspectName) {

		MetadataAwareAspectInstanceFactory instanceFactory;
		if (am.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
			// Create a shared aspect instance.
			Object instance = getSingletonAspectInstance(aspectClass);
			instanceFactory = new SingletonMetadataAwareAspectInstanceFactory(instance, aspectName);
		}
		else {
			// Create a factory for independent aspect instances.
			instanceFactory = new SimpleMetadataAwareAspectInstanceFactory(aspectClass, aspectName);
		}
		return instanceFactory;
	}

	/**
	 * Get the singleton aspect instance for the supplied aspect type. An instance
	 * is created if one cannot be found in the instance cache.
	 */
	private Object getSingletonAspectInstance(Class<?> aspectClass) {
		// Quick check without a lock...
		Object instance = aspectCache.get(aspectClass);
		if (instance == null) {
			synchronized (aspectCache) {
				// To be safe, check within full lock now...
				instance = aspectCache.get(aspectClass);
				if (instance == null) {
					instance = new SimpleAspectInstanceFactory(aspectClass).getAspectInstance();
					aspectCache.put(aspectClass, instance);
				}
			}
		}
		return instance;
	}


	/**
	 * Create a new proxy according to the settings in this factory.
	 * <p>Can be called repeatedly. Effect will vary if we've added
	 * or removed interfaces. Can add and remove interceptors.
	 * <p>Uses a default class loader: Usually, the thread context class loader
	 * (if necessary for proxy creation).
	 * @return the new proxy
	 */
	@SuppressWarnings("unchecked")
	public <T> T getProxy() {
		return (T) createAopProxy().getProxy();
	}

	/**
	 * Create a new proxy according to the settings in this factory.
	 * <p>Can be called repeatedly. Effect will vary if we've added
	 * or removed interfaces. Can add and remove interceptors.
	 * <p>Uses the given class loader (if necessary for proxy creation).
	 * @param classLoader the class loader to create the proxy with
	 * @return the new proxy
	 */
	@SuppressWarnings("unchecked")
	public <T> T getProxy(ClassLoader classLoader) {
		return (T) createAopProxy().getProxy(classLoader);
	}

}
