/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.context.annotation;

import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers an auto proxy creator against the current {@link BeanDefinitionRegistry}
 * as appropriate based on an {@code @Enable*} annotation having {@code mode} and
 * {@code proxyTargetClass} attributes set to the correct values.
 *
 * @author Chris Beams
 * @since 3.1
 * @see org.springframework.cache.annotation.EnableCaching
 * @see org.springframework.transaction.annotation.EnableTransactionManagement
 */
public class AutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
	/*
	 * ❗️❗️❗️
	 * 简而言之:
	 *  	将自动代理创建器AutoProxyCreator加载到BeanDefinitionRegistry中去
	 * 		一般而言可以注册的自动代理创建器都是 AnnotationAwareAspectJAutoProxyCreator 这一个
	 * 		简单说一下: 它可以做到两点
	 * 			a: 扫描ioc容器中的Advisor类型
	 * 			b: 将@Aspect注解标注的切面类中的所有@Before\@Pointcut\@After\@Around\@AfterThrowing\@AfterReturning标注的通知增强方法转换为对应的Advisor
	 * 		然后对所有的bean初始化之后进行拦截
	 * 			将a和b中Advisor汇总 [当然期间是可以通过一些方法判断Advisor是否合格,比如Advisor的beanName是否满足正则表达式等等,忽略~~]
	 * 			对拦截到的bean判断[第一步bean是否为AOP内部框架或者BeanDefinition自己标注过自己不需要代理,第二步是否有Advisor的ClassFilter或者MethodMatcher匹配到]
	 * 			如果允许代理,且有对应的Advisor,就是用ProxyFactory开始为其创建代理对象吧
	 * 
	 */

	private final Log logger = LogFactory.getLog(getClass());

	/**
	 * Register, escalate, and configure the standard auto proxy creator (APC) against the
	 * given registry. Works by finding the nearest annotation declared on the importing
	 * {@code @Configuration} class that has both {@code mode} and {@code proxyTargetClass}
	 * attributes. If {@code mode} is set to {@code PROXY}, the APC is registered; if
	 * {@code proxyTargetClass} is set to {@code true}, then the APC is forced to use
	 * subclass (CGLIB) proxying.
	 * <p>Several {@code @Enable*} annotations expose both {@code mode} and
	 * {@code proxyTargetClass} attributes. It is important to note that most of these
	 * capabilities end up sharing a {@linkplain AopConfigUtils#AUTO_PROXY_CREATOR_BEAN_NAME
	 * single APC}. For this reason, this implementation doesn't "care" exactly which
	 * annotation it finds -- as long as it exposes the right {@code mode} and
	 * {@code proxyTargetClass} attributes, the APC can be registered and configured all
	 * the same.
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		// 属于Import的一种方式：ImportBeanDefinitionRegistrar，还有一种就是ImportSelector/DeferredImportSelector，以及最普通的直接导入@Configuration的配置类或非配置类
		// 针对给定的BeanDefinitionRegistry 注册、升级和配置标准自动代理创建者 (auto proxy creator)。
		// 通过查找在导入的@Configuration类上具有mode和proxyTargetClass属性的声明的注解来工作。
		// 		如果注解的mode设置为PROXY ，则注册 APC；
		// 		如果注解的proxyTargetClass设置为true ，则 APC = Auto Proxy Creator 被迫使用子类 (CGLIB) 代理。

		// 几个@Enable*注释公开了mode和proxyTargetClass属性： @EnableAsync @EnableTransactionManagement @EnableCaching
		// 值得注意的是，这些功能中的大多数最终都共享一个自动代理创建器(auto proxy creator) 。
		// 出于这个原因，这个实现并不“关心”它找到了哪个注解——只要它公开了正确的mode和proxyTargetClass属性，APC 就可以注册和配置。

		// 这里面需要特别注意的是：这里是拿到所有的注解类型~~~而不是只拿@EnableAspectJAutoProxy这个类型的
		// 原因：因为mode、proxyTargetClass等属性会直接影响到代理得方式，而拥有这些属性的注解至少有：
		// @EnableTransactionManagement、@EnableAsync、@EnableCaching等~~~~
		// 甚至还有启用AOP的注解：@EnableAspectJAutoProxy它也能设置`proxyTargetClass`这个属性的值，因此也会产生关联影响~
		
		// 1. importingClassMetadata 是使用@EnableCaching或@EnableTransactionManagement等注解的类的注解元信息
		boolean candidateFound = false;
		Set<String> annTypes = importingClassMetadata.getAnnotationTypes();
		for (String annType : annTypes) {
			// 2. 遍历配置类上的每一个注解构成的AnnotationAttributes
			AnnotationAttributes candidate = AnnotationConfigUtils.attributesFor(importingClassMetadata, annType);
			if (candidate == null) {
				continue;
			}
			// 3. 直到可以在当前注解的AnnotationAttributes中找到mode和proxyTargetClass属性
			// 说明：如果你是比如@Configuration或者@Order的时,mode和proxyTargetClass就是null
			// 只有在 @EnableCaching \ @EnableTransactionManagement \ @EnableAsync 等等有这两个值 
			// 但实际上哈: 只有 @EnableCaching \ @EnableTransactionManagement 源码流程中使用到这个哦
			Object mode = candidate.get("mode");
			Object proxyTargetClass = candidate.get("proxyTargetClass");

			// 4. 注解存在AdviceMode类型的mode属性,且存在Boolean类型的proxyTargetClass属性
			if (mode != null && proxyTargetClass != null && AdviceMode.class == mode.getClass() && Boolean.class == proxyTargetClass.getClass()) {
				// 4.1 标记候选的注解被找到
				candidateFound = true; 
				// 4.2 动态代理 -> 允许使用JDK或Cglib代理,而是AspectJ的静态地啊你哦
				if (mode == AdviceMode.PROXY) { 
					// 4.3 ❗️❗️❗️
					// 开始注册自动代理创建器啦
					// but but -> 如果 @EnableCaching 和 @EnableAspectJAware 注解同时开启 -- 具体原因请见: AopConfigUtils 中的描述吧
					// 两者都会使用 AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry) 方法
					AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
					if ((Boolean) proxyTargetClass) {
						// 4.4 强制使用Cglib代理
						AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
						return;
					}
				}
			}
		}
		if (!candidateFound && logger.isInfoEnabled()) {
			String name = getClass().getSimpleName();
			logger.info(String.format("%s was imported but no annotations were found " +
					"having both 'mode' and 'proxyTargetClass' attributes of type " +
					"AdviceMode and boolean respectively. This means that auto proxy " +
					"creator registration and configuration may not have occurred as " +
					"intended, and components may not be proxied as expected. Check to " +
					"ensure that %s has been @Import'ed on the same class where these " +
					"annotations are declared; otherwise remove the import of %s " +
					"altogether.", name, name, name));
		}
	}

}
