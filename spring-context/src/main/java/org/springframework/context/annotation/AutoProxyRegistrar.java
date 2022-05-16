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
	/**
	 * 为当前BeanDefinitionRegistry注册一个自动代理创建者
	 * 其基于@Enable*注释的mode和proxyTargetClass属性
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
		// 属于Import的一种方式：ImportBeanDefinitionRegistrar，还有一种就是ImportSelector，以及最普通的直接导入@Configuration的配置类或非配置类
		// 针对给定注册表注册、升级和配置标准自动代理创建者 (auto proxy creator)。
		// 通过查找在导入的@Configuration类上具有mode和proxyTargetClass属性的声明的注解来工作。
		// 		如果注解的mode设置为PROXY ，则注册 APC；
		// 		如果注解的proxyTargetClass设置为true ，则 APC 被迫使用子类 (CGLIB) 代理。

		// 几个@Enable*注释公开了mode和proxyTargetClass属性： @EnableAsync @EnableTransactionManagement
		// 值得注意的是，这些功能中的大多数最终都共享一个自动代理创建器(auto proxy creator) 。
		// 出于这个原因，这个实现并不“关心”它找到了哪个注解——只要它公开了正确的mode和proxyTargetClass属性，APC 就可以注册和配置。

		// 这里面需要特别注意的是：这里是拿到所有的注解类型~~~而不是只拿@EnableAspectJAutoProxy这个类型的
		// 原因：因为mode、proxyTargetClass等属性会直接影响到代理得方式，而拥有这些属性的注解至少有：
		// @EnableTransactionManagement、@EnableAsync、@EnableCaching等~~~~
		// 甚至还有启用AOP的注解：@EnableAspectJAutoProxy它也能设置`proxyTargetClass`这个属性的值，因此也会产生关联影响~
		boolean candidateFound = false;
		Set<String> annTypes = importingClassMetadata.getAnnotationTypes();
		for (String annType : annTypes) {
			AnnotationAttributes candidate = AnnotationConfigUtils.attributesFor(importingClassMetadata, annType);
			if (candidate == null) {
				continue;
			}
			// 拿到注解里的这两个属性
			// 说明：如果你是比如@Configuration或者别的注解的话  他们就是null了
			Object mode = candidate.get("mode");
			Object proxyTargetClass = candidate.get("proxyTargetClass");
			// 找到@Configuration类上的注解带有model和proxyTargetClass属性的注解对象

			// 如果存在mode且存在proxyTargetClass 属性
			// 且 mode 是AdviceMode枚举类型，proxyTargetClass是boolean类型
			// 并且两个属性的class类型也是对的，才会进来此处（因此其余注解相当于都挡外面了~）
			if (mode != null && proxyTargetClass != null && AdviceMode.class == mode.getClass() && Boolean.class == proxyTargetClass.getClass()) {
				candidateFound = true; // 标记候选者被找到
				if (mode == AdviceMode.PROXY) { // 动态代理
					AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
					if ((Boolean) proxyTargetClass) {
							// 强制使用Cglib代理
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
