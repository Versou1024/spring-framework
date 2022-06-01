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

import java.util.Collection;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base {@code @Configuration} class providing common structure
 * for enabling Spring's annotation-driven cache management capability.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableCaching
 */
@Configuration
public abstract class AbstractCachingConfiguration implements ImportAware {

	@Nullable
	protected AnnotationAttributes enableCaching; // @EnableCaching的注解属性

	@Nullable
	protected Supplier<CacheManager> cacheManager;

	@Nullable
	protected Supplier<CacheResolver> cacheResolver;

	@Nullable
	protected Supplier<KeyGenerator> keyGenerator;

	@Nullable
	protected Supplier<CacheErrorHandler> errorHandler;


	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		// 感知被谁所导入IOC容器的

		// 继承此抽象类前提条件：必须标注@EnableCaching注解~
		this.enableCaching = AnnotationAttributes.fromMap(
				importMetadata.getAnnotationAttributes(EnableCaching.class.getName(), false));
		if (this.enableCaching == null) {
			throw new IllegalArgumentException(
					"@EnableCaching is not present on importing class " + importMetadata.getClassName());
		}
	}

	@Autowired(required = false)
	void setConfigurers(Collection<CachingConfigurer> configurers) {
		// 监测用户配置的CachingConfigurer
		// 注意: 只能配置一个

		if (CollectionUtils.isEmpty(configurers)) {
			return;
		}
		if (configurers.size() > 1) {
			throw new IllegalStateException(configurers.size() + " implementations of " +
					"CachingConfigurer were found when only 1 was expected. " +
					"Refactor the configuration such that CachingConfigurer is " +
					"implemented only once or not at all.");
		}
		CachingConfigurer configurer = configurers.iterator().next();
		// 将 configurer 中的配置 CacheManage\CacheResolver\KeyGenerator\ErrorHandler 提取出来
		useCachingConfigurer(configurer);
	}

	/**
	 * Extract the configuration from the nominated {@link CachingConfigurer}.
	 */
	protected void useCachingConfigurer(CachingConfigurer config) {
		this.cacheManager = config::cacheManager;
		this.cacheResolver = config::cacheResolver;
		this.keyGenerator = config::keyGenerator;
		this.errorHandler = config::errorHandler;
	}

}
