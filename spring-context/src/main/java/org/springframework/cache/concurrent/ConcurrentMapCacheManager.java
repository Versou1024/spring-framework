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

package org.springframework.cache.concurrent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.serializer.support.SerializationDelegate;
import org.springframework.lang.Nullable;

/**
 * {@link CacheManager} implementation that lazily builds {@link ConcurrentMapCache}
 * instances for each {@link #getCache} request. Also supports a 'static' mode where
 * the set of cache names is pre-defined through {@link #setCacheNames}, with no
 * dynamic creation of further cache regions at runtime.
 *
 * <p>Note: This is by no means a sophisticated CacheManager; it comes with no
 * cache configuration options. However, it may be useful for testing or simple
 * caching scenarios. For advanced local caching needs, consider
 * {@link org.springframework.cache.jcache.JCacheCacheManager},
 * {@link org.springframework.cache.ehcache.EhCacheCacheManager},
 * {@link org.springframework.cache.caffeine.CaffeineCacheManager}.
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see ConcurrentMapCache
 */
public class ConcurrentMapCacheManager implements CacheManager, BeanClassLoaderAware {
	// 管理 ConcurrentMapCache 的Manager
	// 它的缓存存储是基于内存的，所以它的生命周期是与应用关联的，对于生产级别的大型企业级应用程序，这可能并不是理想的选择，但它用于本地自己测试是个很好的选择。

	// 每个CacheName对应的ConcurrentMapCache
	private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

	// 静态还是动态的ConcurrentMapCacheManager
	// 静态的 -- ConcurrentMapCacheManager 管理的 cacheNames 是固定的
	// 动态的 -- ConcurrentMapCacheManager 管理的 cacheNames 是动态增加的
	private boolean dynamic = true;

	// 是否允许value为null
	// 是的话,虽然ConcurrentHashMap不允许有NULL值,但是可以通过 NULL_OBJ = new Object(); // 表示空的对象
	private boolean allowNullValues = true;

	// 指定此缓存管理器是存储每个value的副本，还是存储其所有缓存的引用
	// 影响到ConcurrentMapCache对value值的存储~~~
	// false表示：存储它自己（引用）
	// true表示：存储一个副本（对序列化有要求~~~） 很少这么使用~~~
	private boolean storeByValue = false;

	@Nullable
	private SerializationDelegate serialization;


	/**
	 * Construct a dynamic ConcurrentMapCacheManager,
	 * lazily creating cache instances as they are being requested.
	 */
	public ConcurrentMapCacheManager() {
		// 构造一个动态的 ConcurrentMapCacheManager，在请求缓存实例时懒惰地创建它们。
	}

	/**
	 * Construct a static ConcurrentMapCacheManager,
	 * managing caches for the specified cache names only.
	 */
	public ConcurrentMapCacheManager(String... cacheNames) {
		// 构造一个静态 ConcurrentMapCacheManager，仅管理指定缓存名称的缓存
		setCacheNames(Arrays.asList(cacheNames));
	}


	/**
	 * Specify the set of cache names for this CacheManager's 'static' mode.
	 * <p>The number of caches and their names will be fixed after a call to this method,
	 * with no creation of further cache regions at runtime.
	 * <p>Calling this with a {@code null} collection argument resets the
	 * mode to 'dynamic', allowing for further creation of caches again.
	 */
	public void setCacheNames(@Nullable Collection<String> cacheNames) {
		if (cacheNames != null) {
			// 静态
			for (String name : cacheNames) {
				// ❗️❗️❗️
				// 会为每个 cacheNames 创建属于它的 缓存空间
				this.cacheMap.put(name, createConcurrentMapCache(name));
			}
			// 手动设置了，就不允许在动态创建了
			this.dynamic = false;
		}
		else {
			// 动态
			this.dynamic = true;
		}
	}

	/**
	 * Specify whether to accept and convert {@code null} values for all caches
	 * in this cache manager.
	 * <p>Default is "true", despite ConcurrentHashMap itself not supporting {@code null}
	 * values. An internal holder object will be used to store user-level {@code null}s.
	 * <p>Note: A change of the null-value setting will reset all existing caches,
	 * if any, to reconfigure them with the new null-value requirement.
	 */
	public void setAllowNullValues(boolean allowNullValues) {
		// 指定是否接受和转换此缓存管理器中所有缓存的null值。
		// 默认值为“true”，尽管 ConcurrentHashMap 本身不支持null值。内部持有者对象将用于存储用户级null
		if (allowNullValues != this.allowNullValues) {
			this.allowNullValues = allowNullValues;
			// Need to recreate all Cache instances with the new null-value configuration...
			recreateCaches();
		}
	}

	/**
	 * Return whether this cache manager accepts and converts {@code null} values
	 * for all of its caches.
	 */
	public boolean isAllowNullValues() {
		return this.allowNullValues;
	}

	/**
	 * Specify whether this cache manager stores a copy of each entry ({@code true}
	 * or the reference ({@code false} for all of its caches.
	 * <p>Default is "false" so that the value itself is stored and no serializable
	 * contract is required on cached values.
	 * <p>Note: A change of the store-by-value setting will reset all existing caches,
	 * if any, to reconfigure them with the new store-by-value requirement.
	 * @since 4.3
	 */
	public void setStoreByValue(boolean storeByValue) {
		if (storeByValue != this.storeByValue) {
			this.storeByValue = storeByValue;
			// Need to recreate all Cache instances with the new store-by-value configuration...
			recreateCaches();
		}
	}

	/**
	 * Return whether this cache manager stores a copy of each entry or
	 * a reference for all its caches. If store by value is enabled, any
	 * cache entry must be serializable.
	 * @since 4.3
	 */
	public boolean isStoreByValue() {
		// 返回此缓存管理器cacheManger是否存储每个条目的副本或所有缓存的引用。
		// 如果启用了按值存储storeByValue，则任何缓存条目cacheEntry都必须是可序列化的。
		// 因此需要将
		return this.storeByValue;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.serialization = new SerializationDelegate(classLoader);
		// Need to recreate all Cache instances with new ClassLoader in store-by-value mode...
		if (isStoreByValue()) {
			recreateCaches();
		}
	}


	@Override
	public Collection<String> getCacheNames() {
		return Collections.unmodifiableSet(this.cacheMap.keySet());
	}

	@Override
	@Nullable
	public Cache getCache(String name) {
		// 根据 cacheName 查找 cache
		// 如果是dynamic动态的,就会去创建一个新的ConcurrentMapCache

		Cache cache = this.cacheMap.get(name);
		if (cache == null && this.dynamic) {
			synchronized (this.cacheMap) {
				cache = this.cacheMap.get(name);
				if (cache == null) {
					cache = createConcurrentMapCache(name);
					this.cacheMap.put(name, cache);
				}
			}
		}
		return cache;
	}

	private void recreateCaches() {
		for (Map.Entry<String, Cache> entry : this.cacheMap.entrySet()) {
			entry.setValue(createConcurrentMapCache(entry.getKey()));
		}
	}

	/**
	 * Create a new ConcurrentMapCache instance for the specified cache name.
	 * @param name the name of the cache
	 * @return the ConcurrentMapCache (or a decorator thereof)
	 */
	protected Cache createConcurrentMapCache(String name) {
		// 根据cacheName创建ConcurrentMapCache
		SerializationDelegate actualSerialization = (isStoreByValue() ? this.serialization : null);
		return new ConcurrentMapCache(name, new ConcurrentHashMap<>(256), isAllowNullValues(), actualSerialization);
	}

}
