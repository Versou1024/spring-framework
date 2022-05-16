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

package org.springframework.aop.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator should be registered yet multiple concrete
 * implementations are available. This class provides a simple escalation protocol,
 * allowing a caller to request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AopNamespaceUtils
 */
public abstract class AopConfigUtils {

	/**
	 * The bean name of the internally managed auto-proxy creator.
	 */
	public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
			"org.springframework.aop.config.internalAutoProxyCreator"; // 这是注册自动代理创建器，默认的BeanName（若想覆盖，需要使用这个BeanName）

	/**
	 * Stores the auto proxy creator classes in escalation order.
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

	// 按照升级顺序 存储自动代理创建器（注意这里是升级的顺序 一个比一个强的）
	static {
		// Set up the escalation list...
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class); // 升级最低
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class); // 升级最高
	}


	// 这两个：注册的是`InfrastructureAdvisorAutoProxyCreator`
	// 调用处为：AutoProxyRegistrar#registerBeanDefinitions（它是一个ImportBeanDefinitionRegistrar实现类）
	// 而AutoProxyRegistrar使用处为CachingConfigurationSelector，和`@EnableCaching`注解有关
	// 其次就是AopNamespaceUtils有点用，这个下面再分析
	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, @Nullable Object source) {
		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
	}

	// 下面这两个是注入：AspectJAwareAdvisorAutoProxyCreator
	// 目前没有地方默认调用~~~~和Aop的xml配置方案有关的
	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	// 这个就是最常用的，注入的是：AnnotationAwareAspectJAutoProxyCreator  注解驱动的自动代理创建器
	// `@EnableAspectJAutoProxy`注入进来的就是它了
	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	// 这两个方法，很显然，就是处理注解的两个属性值
	// proxyTargetClass：true表示强制使用CGLIB的动态代理
	// exposeProxy：true暴露当前代理对象到线程上绑定
	// 最终都会放到自动代理创建器得BeanDefinition 里面去~~~创建代理的时候会用到此属性值
	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
	}

	public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	//========上面的注册自动代理创建器IfNecessary之类的方法，最终都是调用了这里========
	@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		// 这里相当于，如果你自己定义了一个名称为这个的自动代理创建器，那也是ok的（需要注意的是使用工厂方法@Bean的方式定义，这里是会报错的）
		// 备注：请尽量不要自定义自动代理创建器，也不要轻易使用低级别的创建器，若你对原理不是非常懂的话，慎重
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// 想要注册的自动代理器如果并不是Spring容器中已经注册的
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				// 这个处理非常有意思，总之就是`InfrastructureAdvisorAutoProxyCreator`/AspectJAwareAdvisorAutoProxyCreator/AnnotationAwareAspectJAutoProxyCreator的一个逻辑~~~~
				// （就是防止怕用户注册错了，做了一个容错处理~~~）
				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName()); // 容器中已注入的
				int requiredPriority = findPriorityForClass(cls); // 要求注入的
				// currentPriority < requiredPriority
				// 如果当前用户注册进来的Aop代理创建类的级别，是低于我们要求注入的cls的级别的，Spring内部也会对它进行提升成我们要求的那个cls类型
				// 这样我符合我们的建议：最好不要自己去使用低级别的自动代理创建器
				if (currentPriority < requiredPriority) {
					apcDefinition.setBeanClassName(cls.getName());
				}
			}
			return null;
		}

		// 绝大部分情况下都会走这里，new一个Bean定义信息出来，然后order属性值为HIGHEST_PRECEDENCE
		// role是：ROLE_INFRASTRUCTURE属于Spring框架自己使用的Bean
		// BeanName为：AUTO_PROXY_CREATOR_BEAN_NAME
		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		// 此处注意，增加了一个属性：最高优先级执行
		beanDefinition.setSource(source); // 一般框架自己的三个的source都是null值
		// 角色为Spring自己使用
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		// 注册此Bean定义信息
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
		return beanDefinition;
	}

	private static int findPriorityForClass(Class<?> clazz) {
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	private static int findPriorityForClass(@Nullable String className) {
		for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
			Class<?> clazz = APC_PRIORITY_LIST.get(i);
			if (clazz.getName().equals(className)) {
				return i;
			}
		}
		throw new IllegalArgumentException(
				"Class name [" + className + "] is not a known auto-proxy creator class");
	}

}
