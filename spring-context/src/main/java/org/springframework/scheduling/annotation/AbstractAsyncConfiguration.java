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

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base {@code Configuration} class providing common structure for enabling
 * Spring's asynchronous method execution capability.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.1
 * @see EnableAsync
 */
@Configuration
public abstract class AbstractAsyncConfiguration implements ImportAware {
	// AbstractAsyncConfiguration = Abstract Async Configuration
	// 抽象的异步配置 -- 包括两个部分的配置感知
	// a: 感知获取到使用的@EnableAsync的注解属性 -> AnnotationAttributes
	// b: 感知用户设置到ioc容器的AsyncConfigurer -> 从中获取出executor执行器和异常处理器exceptionHandler

	// @EnableAsync的注解属性信息
	@Nullable
	protected AnnotationAttributes enableAsync;

	// 异步执行的执行器 -- 可以为null，主要存储用户配置的AsyncConfigurer的执行器
	@Nullable
	protected Supplier<Executor> executor;

	// 错误处理器 -- 可以为null，主要存储用户配置的AsyncConfigurer的错误处理器
	@Nullable
	protected Supplier<AsyncUncaughtExceptionHandler> exceptionHandler;


	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		// AnnotationMetadata 是间接使用@Import注解的配置类的元数据信息
		// 从其中获取@EnableAsync信息
		this.enableAsync = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(EnableAsync.class.getName(), false));
		if (this.enableAsync == null) {
			throw new IllegalArgumentException("@EnableAsync is not present on importing class " + importMetadata.getClassName());
		}
	}

	/**
	 * Collect any {@link AsyncConfigurer} beans through autowiring.
	 */
	@Autowired(required = false)
	void setConfigurers(Collection<AsyncConfigurer> configurers) {
		// 这个方法上有一个@Autowrited注解，因此Ioc容器中AsyncConfigurer类型的bean都会被注册进来 -> 当然这并非是必须的哦

		// 1. 用户没有定义任何AsyncConfigurer,直接return
		if (CollectionUtils.isEmpty(configurers)) {
			return;
		}
		// 2. AsyncConfigurer用来配置线程池配置以及异常处理器，而且在Spring环境中最多只能有一个
		// 在这里我们知道了，如果想要自己去配置线程池，只需要实现AsyncConfigurer接口，并且不可以在Spring环境中有多个实现AsyncConfigurer的类。
		if (configurers.size() > 1) {
			throw new IllegalStateException("Only one AsyncConfigurer may exist");
		}
		// 3. 唯一的 AsyncConfigurer ,取出其中的executor\exceptionHandler -> 配置到全局中心
		AsyncConfigurer configurer = configurers.iterator().next();
		this.executor = configurer::getAsyncExecutor;
		this.exceptionHandler = configurer::getAsyncUncaughtExceptionHandler;
	}

}
