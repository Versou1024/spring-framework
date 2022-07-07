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

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.core.serializer.support.SerializationDelegate;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Simple {@link org.springframework.cache.Cache} implementation based on the
 * core JDK {@code java.util.concurrent} package.
 *
 * <p>Useful for testing or simple caching scenarios, typically in combination
 * with {@link org.springframework.cache.support.SimpleCacheManager} or
 * dynamically through {@link ConcurrentMapCacheManager}.
 *
 * <p><b>Note:</b> As {@link ConcurrentHashMap} (the default implementation used)
 * does not allow for {@code null} values to be stored, this class will replace
 * them with a predefined internal object. This behavior can be changed through the
 * {@link #ConcurrentMapCache(String, ConcurrentMap, boolean)} constructor.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.1
 * @see ConcurrentMapCacheManager
 */
public class ConcurrentMapCache extends AbstractValueAdaptingCache {
	// 基于核心 JDK java.util.concurrent包的简单org.springframework.cache.Cache实现。
	// 对于测试或简单的缓存场景很有用，通常与org.springframework.cache.support.SimpleCacheManager或通过ConcurrentMapCacheManager动态结合。

	private final String name;

	// 缓存
	private final ConcurrentMap<Object, Object> store;

	@Nullable
	private final SerializationDelegate serialization;


	/**
	 * Create a new ConcurrentMapCache with the specified name.
	 * @param name the name of the cache
	 */
	public ConcurrentMapCache(String name) {
		// note: 需要自动缓存cache的名字name,并且默认是支持存储null值的
		this(name, new ConcurrentHashMap<>(256), true);
	}

	/**
	 * Create a new ConcurrentMapCache with the specified name.
	 * @param name the name of the cache
	 * @param allowNullValues whether to accept and convert {@code null}
	 * values for this cache
	 */
	public ConcurrentMapCache(String name, boolean allowNullValues) {
		this(name, new ConcurrentHashMap<>(256), allowNullValues);
	}

	/**
	 * Create a new ConcurrentMapCache with the specified name and the
	 * given internal {@link ConcurrentMap} to use.
	 * @param name the name of the cache
	 * @param store the ConcurrentMap to use as an internal store
	 * @param allowNullValues whether to allow {@code null} values
	 * (adapting them to an internal null holder value)
	 */
	public ConcurrentMapCache(String name, ConcurrentMap<Object, Object> store, boolean allowNullValues) {
		this(name, store, allowNullValues, null);
	}

	/**
	 * Create a new ConcurrentMapCache with the specified name and the
	 * given internal {@link ConcurrentMap} to use. If the
	 * {@link SerializationDelegate} is specified,
	 * {@link #isStoreByValue() store-by-value} is enabled
	 * @param name the name of the cache
	 * @param store the ConcurrentMap to use as an internal store
	 * @param allowNullValues whether to allow {@code null} values
	 * (adapting them to an internal null holder value)
	 * @param serialization the {@link SerializationDelegate} to use
	 * to serialize cache entry or {@code null} to store the reference
	 * @since 4.3
	 */
	protected ConcurrentMapCache(String name, ConcurrentMap<Object, Object> store,
			boolean allowNullValues, @Nullable SerializationDelegate serialization) {

		super(allowNullValues);
		Assert.notNull(name, "Name must not be null");
		Assert.notNull(store, "Store must not be null");
		this.name = name;
		this.store = store;
		this.serialization = serialization;
	}


	/**
	 * Return whether this cache stores a copy of each entry ({@code true}) or
	 * a reference ({@code false}, default). If store by value is enabled, each
	 * entry in the cache must be serializable.
	 * @since 4.3
	 */
	public final boolean isStoreByValue() {
		return (this.serialization != null);
	}

	@Override
	public final String getName() {
		// 缓存空间名
		return this.name;
	}

	@Override
	public final ConcurrentMap<Object, Object> getNativeCache() {
		// 缓存空间
		return this.store;
	}

	@Override
	@Nullable
	protected Object lookup(Object key) {
		// 该缓存空间中对应key的value值
		return this.store.get(key);
	}

	@SuppressWarnings("unchecked")
	@Override
	@Nullable
	public <T> T get(Object key, Callable<T> valueLoader) {
		// 获取缓存空间中指定的key的value值

		// 当缓存空间中不存在指定的key,将调用valueLoader.call()方法
		// 然后存储起来,然后再获取出来并返回出去
		return (T) fromStoreValue(this.store.computeIfAbsent(key, k -> {
			try {
				return toStoreValue(valueLoader.call());
			}
			catch (Throwable ex) {
				throw new ValueRetrievalException(key, valueLoader, ex);
			}
		}));
	}

	@Override
	public void put(Object key, @Nullable Object value) {
		// 向缓存空间设置键值对
		this.store.put(key, toStoreValue(value));
	}

	@Override
	@Nullable
	public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
		// 当缓存空间不存在指定key时,就设置进去
		Object existing = this.store.putIfAbsent(key, toStoreValue(value));
		return toValueWrapper(existing);
	}

	// ConcurrentMapCache的evict()和evictIfPresent()都是立即清除指定映射
	// evict()不存在延迟或异步清除的情况

	@Override
	public void evict(Object key) {
		this.store.remove(key);
	}

	@Override
	public boolean evictIfPresent(Object key) {
		return (this.store.remove(key) != null);
	}

	// ConcurrentMapCache的clear()和invalidate()都是立即清空缓存
	// clear()不存在延迟或异步清除的情况
	
	@Override
	public void clear() {
		// 清空缓存空间
		this.store.clear();
	}

	@Override
	public boolean invalidate() {
		boolean notEmpty = !this.store.isEmpty();
		this.store.clear();
		return notEmpty;
	}

	@Override
	protected Object toStoreValue(@Nullable Object userValue) {
		// 在有序列化器时, 将 userValue 直接序列化为byteArray再存储起来
		// 没有序列化器, 就直接存储 userValue
		
	    // 1. super.toStoreValue() 主要是当开启allowNullValues时,需要注意userValue为null时,存储的值替换为NullValue.INSTANCE
		Object storeValue = super.toStoreValue(userValue);
		// 2.1 序列化器存在,序列化为字节数组存储起来
		if (this.serialization != null) {
			try {
				return this.serialization.serializeToByteArray(storeValue);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Failed to serialize cache value '" + userValue +
						"'. Does it implement Serializable?", ex);
			}
		}
		// 2. 2 没有序列化器,直接存储即可
		else {
			return storeValue;
		}
	}

	@Override
	protected Object fromStoreValue(@Nullable Object storeValue) {
		if (storeValue != null && this.serialization != null) {
			try {
				return super.fromStoreValue(this.serialization.deserializeFromByteArray((byte[]) storeValue));
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Failed to deserialize cache value '" + storeValue + "'", ex);
			}
		}
		else {
			return super.fromStoreValue(storeValue);
		}
	}

}
