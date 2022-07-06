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

package org.springframework.scheduling.annotation;

import java.lang.annotation.Annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.util.Assert;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable proxy-based asynchronous method execution.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableAsync
 * @see AsyncConfigurationSelector
 */
@Configuration // 它是一个配置类，角色为ROLE_INFRASTRUCTURE  框架自用的Bean类型
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyAsyncConfiguration extends AbstractAsyncConfiguration {
	
	// AbstractAsyncConfiguration 抽象的异步处理配置中心 -> 
	// 抽象的异步配置 -- 包括两个部分的配置感知
	// a: 感知获取到使用的@EnableAsync的注解属性 -> AnnotationAttributes
	// b: 感知用户设置到ioc容器的AsyncConfigurer -> 从中获取出executor执行器和异常处理器exceptionHandler
	
	// ProxyAsyncConfiguration = Proxy Async Configuration
	// 是在抽象异步配置的基础上,提供一个AsyncAnnotationBeanPostProcessor自动来处理需要异步的方法哦

	// 它的作用就是注册了一个AsyncAnnotationBeanPostProcessor，它是个BeanPostProcessor
	@Bean(name = TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public AsyncAnnotationBeanPostProcessor asyncAdvisor() {
		Assert.notNull(this.enableAsync, "@EnableAsync annotation metadata was not injected");
		// 1. 关注@Async的BeanPostProcessor处理器 --- 核心之一
		AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
		bpp.configure(this.executor, this.exceptionHandler);
		// 2. customAsyncAnnotation：自定义的注解类型
		Class<? extends Annotation> customAsyncAnnotation = this.enableAsync.getClass("annotation");
		// 用户没有自定义annotation属性，就无须设置
		if (customAsyncAnnotation != AnnotationUtils.getDefaultValue(EnableAsync.class, "annotation")) {
			bpp.setAsyncAnnotationType(customAsyncAnnotation);
		}
		// 3. order属性值，最终决定的是BeanProcessor的执行顺序的
		bpp.setProxyTargetClass(this.enableAsync.getBoolean("proxyTargetClass"));
		bpp.setOrder(this.enableAsync.<Integer>getNumber("order"));
		return bpp;
	}

}
