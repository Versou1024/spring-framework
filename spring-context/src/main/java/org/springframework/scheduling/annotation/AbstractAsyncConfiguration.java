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
	// 抽象的异步配置 -- 主要提供给@Async使用

	@Nullable
	protected AnnotationAttributes enableAsync; // @EnableAsync的注解属性信息

	@Nullable
	protected Supplier<Executor> executor; // 异步执行的执行器 -- 可以为null，主要存储用户配置的AsyncConfigurer的执行器

	@Nullable
	protected Supplier<AsyncUncaughtExceptionHandler> exceptionHandler; // 错误处理器 -- 可以为null，主要存储用户配置的AsyncConfigurer的错误处理器


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
		// 这个方法上有一个@Autowrited注解，因此Ioc容器中AsyncConfigurer类型的bean都会被注册进来

		if (CollectionUtils.isEmpty(configurers)) {
			return;
		}
		// AsyncConfigurer用来配置线程池配置以及异常处理器，而且在Spring环境中最多只能有一个
		// 在这里我们知道了，如果想要自己去配置线程池，只需要实现AsyncConfigurer接口，并且不可以在Spring环境中有多个实现AsyncConfigurer的类。
		if (configurers.size() > 1) {
			throw new IllegalStateException("Only one AsyncConfigurer may exist");
		}
		// 拿到唯一的 AsyncConfigurer ，然后赋值~~~~   默认的请参照这个类：AsyncConfigurerSupport（它并不会被加入进Spring容器里）
		AsyncConfigurer configurer = configurers.iterator().next();
		this.executor = configurer::getAsyncExecutor;
		this.exceptionHandler = configurer::getAsyncUncaughtExceptionHandler;
	}

}
