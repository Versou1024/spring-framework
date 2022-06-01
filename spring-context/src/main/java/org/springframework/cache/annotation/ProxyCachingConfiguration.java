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

package org.springframework.cache.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.config.CacheManagementConfigUtils;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable proxy-based annotation-driven cache management.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableCaching
 * @see CachingConfigurationSelector
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyCachingConfiguration extends AbstractCachingConfiguration {
	// 超类 AbstractCachingConfiguration
	// 1. 将获取@EnableCaching注解的相关信息
	// 2. 并注入用户实现的CachingConfigurer中的各项配置

	// 用于注册启用基于代理的注释驱动的缓存管理所需的 Spring 基础设施 bean
	// ProxyCachingConfiguration 在超类 AbstractCachingConfiguration 的 基础上
	// 完成 Caching 需要的拦截器\目标源\切面等配置 -- 和 aop 相关的
	// 职责分离

	@Bean(name = CacheManagementConfigUtils.CACHE_ADVISOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public BeanFactoryCacheOperationSourceAdvisor cacheAdvisor() {
		// 核心 -- 配值增强器

		BeanFactoryCacheOperationSourceAdvisor advisor = new BeanFactoryCacheOperationSourceAdvisor();
		// 设置缓存的操作属性源头
		advisor.setCacheOperationSource(cacheOperationSource());
		// 设置增强方法 -- CacheInterceptor
		advisor.setAdvice(cacheInterceptor());
		if (this.enableCaching != null) {
			// 设置当前Advisor的优先级
			advisor.setOrder(this.enableCaching.<Integer>getNumber("order"));
		}
		return advisor;
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public CacheOperationSource cacheOperationSource() {
		// 默认会注入的 -- 基于Cache系列注解获取缓存操作CacheOperation的属性源
		return new AnnotationCacheOperationSource();
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public CacheInterceptor cacheInterceptor() {
		// 缓存拦截器 -- 即在方法前后增强的处理
		CacheInterceptor interceptor = new CacheInterceptor();
		// 父类 -- 检查到用户定义的配置 -- 注入到CacheInterceptor中
		interceptor.configure(this.errorHandler, this.keyGenerator, this.cacheResolver, this.cacheManager);
		// 设置操作源
		interceptor.setCacheOperationSource(cacheOperationSource());
		return interceptor;
	}

}
