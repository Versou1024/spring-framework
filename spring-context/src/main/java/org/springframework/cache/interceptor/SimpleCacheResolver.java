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

package org.springframework.cache.interceptor;

import java.util.Collection;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;

/**
 * A simple {@link CacheResolver} that resolves the {@link Cache} instance(s)
 * based on a configurable {@link CacheManager} and the name of the
 * cache(s) as provided by {@link BasicOperation#getCacheNames() getCacheNames()}.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 * @see BasicOperation#getCacheNames()
 */
public class SimpleCacheResolver extends AbstractCacheResolver {
	// SimpleCacheResolver 默认被使用的CacheResolver对象
	// SimpleCacheResolver 中CacheManager必须存在,否则调用resolveCache()会抛出异常哦
	
	// getCacheNames(CacheOperationInvocationContext<?> context)
	// 是从 直接去拿CacheOperation中的cacheNames 属性 

	/**
	 * Construct a new {@code SimpleCacheResolver}.
	 * @see #setCacheManager
	 */
	public SimpleCacheResolver() {
	}

	/**
	 * Construct a new {@code SimpleCacheResolver} for the given {@link CacheManager}.
	 * @param cacheManager the CacheManager to use
	 */
	public SimpleCacheResolver(CacheManager cacheManager) {
		super(cacheManager);
	}


	@Override
	protected Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
		// 1. 直接去拿CacheOperation中的cacheNames
		// 对应的就是 @CachePut/@CacheEvict/@Cacheable 中的 cacheNames 属性哦
		return context.getOperation().getCacheNames();
	}


	/**
	 * Return a {@code SimpleCacheResolver} for the given {@link CacheManager}.
	 * @param cacheManager the CacheManager (potentially {@code null})
	 * @return the SimpleCacheResolver ({@code null} if the CacheManager was {@code null})
	 * @since 5.1
	 */
	@Nullable
	static SimpleCacheResolver of(@Nullable CacheManager cacheManager) {
		return (cacheManager != null ? new SimpleCacheResolver(cacheManager) : null);
	}

}
