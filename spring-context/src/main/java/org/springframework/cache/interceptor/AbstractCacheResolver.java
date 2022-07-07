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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A base {@link CacheResolver} implementation that requires the concrete
 * implementation to provide the collection of cache name(s) based on the
 * invocation context.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 */
public abstract class AbstractCacheResolver implements CacheResolver, InitializingBean {

	// CacheManger 用来存放Cache
	// 注意 -- 可以为空哦 
	// 当CacheManager不为空的时候,解析CacheOperation的cacheName时就可以直接去CacheManager中查找
	@Nullable
	private CacheManager cacheManager; 


	/**
	 * Construct a new {@code AbstractCacheResolver}.
	 * @see #setCacheManager
	 */
	protected AbstractCacheResolver() {
	}

	/**
	 * Construct a new {@code AbstractCacheResolver} for the given {@link CacheManager}.
	 * @param cacheManager the CacheManager to use
	 */
	protected AbstractCacheResolver(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}


	/**
	 * Set the {@link CacheManager} that this instance should use.
	 */
	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	/**
	 * Return the {@link CacheManager} that this instance uses.
	 */
	public CacheManager getCacheManager() {
		Assert.state(this.cacheManager != null, "No CacheManager set");
		return this.cacheManager;
	}

	// 做了一步校验而已 ~~~ CacheManager 必须存在
	// 这是一个使用技巧哦   自己的在设计框架的框架的时候可以使用~
	@Override
	public void afterPropertiesSet()  {
		// 如果是注入到IOC容器中 -- 就需要检查 CacheManager 是否存在
		Assert.notNull(this.cacheManager, "CacheManager is required");
	}


	@Override
	public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
		// 1. 抽象方法 getCacheNames() -> 返回 List<String> cacheNames
		// 去 CacheOperationInvocationContext 找到所有的 CacheNames
		// CacheOperationInvocationContext 是用户标注有缓存注解的方法执行时,就会为每一个对应的CacheOperation
		// 创建一个CacheOperationInvocationContext,而context中就有对应的CacheOperation
		// 这里就是应该从CacheOperation获取对应的CacheName -> 比如 @CachePut的cacheNames的数组属性
		// 具体如何解析: 需要子类实现 getCacheNames(context);
		Collection<String> cacheNames = getCacheNames(context);
		if (cacheNames == null) {
			return Collections.emptyList();
		}
		// 2. 使用CacheManager根据CacheName查找Cache
		// 将所有需要指定的Cache加入到result中返回
		// 根据cacheNames  去CacheManager里面拿到Cache对象， 作为最终的返回
		Collection<Cache> result = new ArrayList<>(cacheNames.size());
		for (String cacheName : cacheNames) {
			Cache cache = getCacheManager().getCache(cacheName);
			if (cache == null) {
				throw new IllegalArgumentException("Cannot find cache named '" +
						cacheName + "' for " + context.getOperation());
			}
			result.add(cache);
		}
		return result;
	}

	/**
	 * Provide the name of the cache(s) to resolve against the current cache manager.
	 * <p>It is acceptable to return {@code null} to indicate that no cache could
	 * be resolved for this invocation.
	 * @param context the context of the particular invocation
	 * @return the cache name(s) to resolve, or {@code null} if no cache should be resolved
	 */
	@Nullable
	protected abstract Collection<String> getCacheNames(CacheOperationInvocationContext<?> context);
	// 子类需要实现此抽象方法

}
