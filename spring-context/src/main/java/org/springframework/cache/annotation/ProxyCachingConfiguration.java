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
	// 2. 注入用户实现的CachingConfigurer中的各项配置 ~~ 老套路

	
	// BeanFactoryCacheOperationSourceAdvisor 生效的原因
	// 分析:
	//		1. 在CachingConfigurationSelect首先注入了AutoProxyRegistrar
	//  	而AutoProxyRegistrar会将将自动代理创建器AutoProxyCreator加载到BeanDefinitionRegistry中去
	// 		一般而言可以注册的自动代理创建器都是 AnnotationAwareAspectJAutoProxyCreator 这一个
	// 		简单说一下: 它可以做到两点
	// 			a: 扫描ioc容器中的Advisor类型
	// 			b: 将@Aspect注解标注的切面类中的所有@Before\@Pointcut\@After\@Around\@AfterThrowing\@AfterReturning标注的通知增强方法转换为对应的Advisor
	// 		然后对所有的bean初始化之后进行拦截
	// 			将a和b中Advisor汇总 [当然期间是可以通过一些方法判断Advisor是否合格,比如Advisor的beanName是否满足正则表达式等等,忽略~~]
	// 			对拦截到的bean判断[第一步bean是否为AOP内部框架或者BeanDefinition自己标注过自己不需要代理,第二步是否有Advisor的ClassFilter或者MethodMatcher匹配到]
	// 			如果允许代理,且有对应的Advisor,就是用ProxyFactory开始为其创建代理对象吧 [Advisor可以进行排序哦]
	// 		2. 注入类当前配置类即 ProxyCachingConfiguration ,然后这里@Bean 中会创建一个 BeanFactoryCacheOperationSourceAdvisor
	// 		   BeanFactoryCacheOperationSourceAdvisor是否生效就需要依靠上面的 AnnotationAwareAspectJAutoProxyCreator 的第a步 -> 去扫描ioc容器中的advisor

	@Bean(name = CacheManagementConfigUtils.CACHE_ADVISOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public BeanFactoryCacheOperationSourceAdvisor cacheAdvisor() {
		// 核心 -- 配值增强器

		BeanFactoryCacheOperationSourceAdvisor advisor = new BeanFactoryCacheOperationSourceAdvisor();
		// 设置缓存的操作属性源头
		advisor.setCacheOperationSource(cacheOperationSource());
		// 设置增强方法 -- CacheInterceptor 
		// note: 这里是Full配置类,因此cacheInterceptor()返回的就是在ioc容器中注册那个bean哦,而不是新创建一个哦
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
		// 父类 -- 检查到用户定义的配置 -- 注入到CacheInterceptor中  -> 下面四个配置是来自用户的CacheConfigurer实现类注入的哦
		interceptor.configure(this.errorHandler, this.keyGenerator, this.cacheResolver, this.cacheManager);
		// 设置操作源
		interceptor.setCacheOperationSource(cacheOperationSource());
		return interceptor;
	}

}
