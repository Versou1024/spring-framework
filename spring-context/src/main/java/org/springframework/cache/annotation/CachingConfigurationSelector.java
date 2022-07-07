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

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Selects which implementation of {@link AbstractCachingConfiguration} should
 * be used based on the value of {@link EnableCaching#mode} on the importing
 * {@code @Configuration} class.
 *
 * <p>Detects the presence of JSR-107 and enables JCache support accordingly.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 3.1
 * @see EnableCaching
 * @see ProxyCachingConfiguration
 */
public class CachingConfigurationSelector extends AdviceModeImportSelector<EnableCaching> {
	// 继承了熟悉的AdviceModeImportSelector<EnableCaching>
	
	private static final String PROXY_JCACHE_CONFIGURATION_CLASS =
			"org.springframework.cache.jcache.config.ProxyJCacheConfiguration";

	private static final String CACHE_ASPECT_CONFIGURATION_CLASS_NAME =
			"org.springframework.cache.aspectj.AspectJCachingConfiguration";

	private static final String JCACHE_ASPECT_CONFIGURATION_CLASS_NAME =
			"org.springframework.cache.aspectj.AspectJJCacheConfiguration";


	private static final boolean jsr107Present;

	private static final boolean jcacheImplPresent;

	static {
		ClassLoader classLoader = CachingConfigurationSelector.class.getClassLoader();
		jsr107Present = ClassUtils.isPresent("javax.cache.Cache", classLoader);
		jcacheImplPresent = ClassUtils.isPresent(PROXY_JCACHE_CONFIGURATION_CLASS, classLoader);
	}


	/**
	 * Returns {@link ProxyCachingConfiguration} or {@code AspectJCachingConfiguration}
	 * for {@code PROXY} and {@code ASPECTJ} values of {@link EnableCaching#mode()},
	 * respectively. Potentially includes corresponding JCache configuration as well.
	 */
	@Override
	public String[] selectImports(AdviceMode adviceMode) {
		switch (adviceMode) {
			case PROXY:
				// 大部分时间走这里
				return getProxyImports();
			case ASPECTJ:
				return getAspectJImports();
			default:
				return null;
		}
	}

	/**
	 * Return the imports to use if the {@link AdviceMode} is set to {@link AdviceMode#PROXY}.
	 * <p>Take care of adding the necessary JSR-107 import if it is available.
	 */
	private String[] getProxyImports() {
		// 将 AutoProxyRegistrar\ProxyCachingConfiguration 加入到ioc容器中

		List<String> result = new ArrayList<>(3);
		// 将自动代理创建器AnnotationAwareAspectJAutoProxyCreator注册到BeanDefinition --> 
		// ❗️❗️❗️ 目的是: 防止后面在ProxyCachingConfiguration创建的Advisor无效
		// 分析:
		//  	AutoProxyRegistrar会将将自动代理创建器AutoProxyCreator加载到BeanDefinitionRegistry中去
		// 		一般而言可以注册的自动代理创建器都是 AnnotationAwareAspectJAutoProxyCreator 这一个
		// 		简单说一下: 它可以做到两点
		// 			a: 扫描ioc容器中的Advisor类型
		// 			b: 将@Aspect注解标注的切面类中的所有@Before\@Pointcut\@After\@Around\@AfterThrowing\@AfterReturning标注的通知增强方法转换为对应的Advisor
		// 		然后对所有的bean初始化之后进行拦截
		// 			将a和b中Advisor汇总 [当然期间是可以通过一些方法判断Advisor是否合格,比如Advisor的beanName是否满足正则表达式等等,忽略~~]
		// 			对拦截到的bean判断[第一步bean是否为AOP内部框架或者BeanDefinition自己标注过自己不需要代理,第二步是否有Advisor的ClassFilter或者MethodMatcher匹配到]
		// 			如果允许代理,且有对应的Advisor,就是用ProxyFactory开始为其创建代理对象吧 [Advisor可以进行排序哦]
		
		// 下面的 ProxyCachingConfiguration 中会创建一个 BeanFactoryCacheOperationSourceAdvisor
		// 这个Advisor是否生效就需要依靠上面的  AnnotationAwareAspectJAutoProxyCreator 的第a步 -> 去扫描ioc容器中的advisor
		
		
		result.add(AutoProxyRegistrar.class.getName());
		// 缓存配置类
		// 主要是向ioc容器注册一个 BeanFactoryCacheOperationSourceAdvisor 的 Advisor 来拦截增强
		result.add(ProxyCachingConfiguration.class.getName()); 
		if (jsr107Present && jcacheImplPresent) {
			result.add(PROXY_JCACHE_CONFIGURATION_CLASS);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Return the imports to use if the {@link AdviceMode} is set to {@link AdviceMode#ASPECTJ}.
	 * <p>Take care of adding the necessary JSR-107 import if it is available.
	 */
	private String[] getAspectJImports() {
		List<String> result = new ArrayList<>(2);
		result.add(CACHE_ASPECT_CONFIGURATION_CLASS_NAME);
		if (jsr107Present && jcacheImplPresent) {
			result.add(JCACHE_ASPECT_CONFIGURATION_CLASS_NAME);
		}
		return StringUtils.toStringArray(result);
	}

}
