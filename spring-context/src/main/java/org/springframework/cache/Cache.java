/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.cache;

import java.util.concurrent.Callable;

import org.springframework.lang.Nullable;

/**
 * Interface that defines common cache operations.
 *
 * <b>Note:</b> Due to the generic use of caching, it is recommended that
 * implementations allow storage of <tt>null</tt> values (for example to
 * cache methods that return {@code null}).
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.1
 */
public interface Cache {
	// 定义通用缓存操作的接口。注意：由于缓存的一般用途，建议实现允许存储空值（例如缓存返回null的方法）
	// 其实现类:
	//  	NoOpCache
	//		EhCacheCache
	//		TransactionalAwareCacheDecorator
	// 		AbstractValueAdaptingCache
	//			ConcurrentMapCache
	//			JCacheCache
	//			CaffeineCache

	/**
	 * Return the cache name.
	 */
	String getName();
	// 缓存名 -> note:注意这个是Cache的name,而不是缓冲中映射的key篇

	/**
	 * Return the underlying native cache provider.
	 */
	Object getNativeCache();
	// 没被包装的Cache
	// 返回本地存储的那个。/比如
	// ConcurrentMapCache就是用ConcurrentHashMap来缓存
	// EhCacheCache就是用Ehcache来缓存数据

	/**
	 * Return the value to which this cache maps the specified key.
	 * <p>Returns {@code null} if the cache contains no mapping for this key;
	 * otherwise, the cached value (which may be {@code null} itself) will
	 * be returned in a {@link ValueWrapper}.
	 * @param key the key whose associated value is to be returned
	 * @return the value to which this cache maps the specified key,
	 * contained within a {@link ValueWrapper} which may also hold
	 * a cached {@code null} value. A straight {@code null} being
	 * returned means that the cache contains no mapping for this key.
	 * @see #get(Object, Class)
	 * @see #get(Object, Callable)
	 */
	@Nullable
	ValueWrapper get(Object key);
	// 根据key获取指定缓存的值

	/**
	 * Return the value to which this cache maps the specified key,
	 * generically specifying a type that return value will be cast to.
	 * <p>Note: This variant of {@code get} does not allow for differentiating
	 * between a cached {@code null} value and no cache entry found at all.
	 * Use the standard {@link #get(Object)} variant for that purpose instead.
	 * @param key the key whose associated value is to be returned
	 * @param type the required type of the returned value (may be
	 * {@code null} to bypass a type check; in case of a {@code null}
	 * value found in the cache, the specified type is irrelevant)
	 * @return the value to which this cache maps the specified key
	 * (which may be {@code null} itself), or also {@code null} if
	 * the cache contains no mapping for this key
	 * @throws IllegalStateException if a cache entry has been found
	 * but failed to match the specified type
	 * @since 4.0
	 * @see #get(Object)
	 */
	@Nullable
	<T> T get(Object key, @Nullable Class<T> type);
	// 根据key获取指定缓存的值并转换为目标的type类型

	/**
	 * Return the value to which this cache maps the specified key, obtaining
	 * that value from {@code valueLoader} if necessary. This method provides
	 * a simple substitute for the conventional "if cached, return; otherwise
	 * create, cache and return" pattern.
	 * <p>If possible, implementations should ensure that the loading operation
	 * is synchronized so that the specified {@code valueLoader} is only called
	 * once in case of concurrent access on the same key.
	 * <p>If the {@code valueLoader} throws an exception, it is wrapped in
	 * a {@link ValueRetrievalException}
	 * @param key the key whose associated value is to be returned
	 * @return the value to which this cache maps the specified key
	 * @throws ValueRetrievalException if the {@code valueLoader} throws an exception
	 * @since 4.3
	 * @see #get(Object)
	 */
	@Nullable
	<T> T get(Object key, Callable<T> valueLoader);
	// 根据key获取指定缓存的值,如果没有缓存则从valueLoader中获取该值
	// note: 说实话不应该使用Callable来表示这个valueLoader

	/**
	 * Associate the specified value with the specified key in this cache.
	 * <p>If the cache previously contained a mapping for this key, the old
	 * value is replaced by the specified value.
	 * <p>Actual registration may be performed in an asynchronous or deferred
	 * fashion, with subsequent lookups possibly not seeing the entry yet.
	 * This may for example be the case with transactional cache decorators.
	 * Use {@link #putIfAbsent} for guaranteed immediate registration.
	 * @param key the key with which the specified value is to be associated
	 * @param value the value to be associated with the specified key
	 * @see #putIfAbsent(Object, Object)
	 */
	void put(Object key, @Nullable Object value);
	// 将指定的键值对存入当前Cache中

	/**
	 * Atomically associate the specified value with the specified key in this cache
	 * if it is not set already.
	 * <p>This is equivalent to:
	 * <pre><code>
	 * ValueWrapper existingValue = cache.get(key);
	 * if (existingValue == null) {
	 *     cache.put(key, value);
	 * }
	 * return existingValue;
	 * </code></pre>
	 * except that the action is performed atomically. While all out-of-the-box
	 * {@link CacheManager} implementations are able to perform the put atomically,
	 * the operation may also be implemented in two steps, e.g. with a check for
	 * presence and a subsequent put, in a non-atomic way. Check the documentation
	 * of the native cache implementation that you are using for more details.
	 * <p>The default implementation delegates to {@link #get(Object)} and
	 * {@link #put(Object, Object)} along the lines of the code snippet above.
	 * @param key the key with which the specified value is to be associated
	 * @param value the value to be associated with the specified key
	 * @return the value to which this cache maps the specified key (which may be
	 * {@code null} itself), or also {@code null} if the cache did not contain any
	 * mapping for that key prior to this call. Returning {@code null} is therefore
	 * an indicator that the given {@code value} has been associated with the key.
	 * @since 4.1
	 * @see #put(Object, Object)
	 */
	@Nullable
	default ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
		// 当前cache中不存在这个key时,就添加key-value
		// 不存在旧值直接put就先去了返回null，否则返回旧值（并且不会把新值put进去）
		ValueWrapper existingValue = get(key);
		if (existingValue == null) {
			put(key, value);
		}
		return existingValue;
	}

	/**
	 * Evict the mapping for this key from this cache if it is present.
	 * <p>Actual eviction may be performed in an asynchronous or deferred
	 * fashion, with subsequent lookups possibly still seeing the entry.
	 * This may for example be the case with transactional cache decorators.
	 * Use {@link #evictIfPresent} for guaranteed immediate removal.
	 * @param key the key whose mapping is to be removed from the cache
	 * @see #evictIfPresent(Object)
	 */
	void evict(Object key);
	// 如果此缓存存在，则从此缓存中逐出此键的映射。
	// 实际驱逐可能以异步或延迟方式执行，随后的查找可能仍会看到该条目。
	// 例如，事务缓存装饰器可能就是这种情况。使用evictIfPresent保证立即删除。

	/**
	 * Evict the mapping for this key from this cache if it is present,
	 * expecting the key to be immediately invisible for subsequent lookups.
	 * <p>The default implementation delegates to {@link #evict(Object)},
	 * returning {@code false} for not-determined prior presence of the key.
	 * Cache providers and in particular cache decorators are encouraged
	 * to perform immediate eviction if possible (e.g. in case of generally
	 * deferred cache operations within a transaction) and to reliably
	 * determine prior presence of the given key.
	 * @param key the key whose mapping is to be removed from the cache
	 * @return {@code true} if the cache was known to have a mapping for
	 * this key before, {@code false} if it did not (or if prior presence
	 * could not be determined)
	 * @since 5.2
	 * @see #evict(Object)
	 */
	default boolean evictIfPresent(Object key) {
		// 如果存在此缓存，则从此缓存中逐出此键的映射，期望该键对于后续查找立即不可见。
		evict(key);
		return false;
	}
	
	// 方法的具体实现我们可以不理解:
	// 但是 要认识到 clear()是有可能异步或延迟删除,而invalidate()需要做到立即使得缓存无效
	// 所以Cache的子类应该能够做到上述的描述 
	// 所以在部分cache实现类中,可能clear()=invalidate()都是立即删除
	// 而对于部分cache实现类中,可能clear()是异步或延迟删除的,invalidate()才是立即删除的

	/**
	 * Clear the cache through removing all mappings.
	 * <p>Actual clearing may be performed in an asynchronous or deferred
	 * fashion, with subsequent lookups possibly still seeing the entries.
	 * This may for example be the case with transactional cache decorators.
	 * Use {@link #invalidate()} for guaranteed immediate removal of entries.
	 * @see #invalidate()
	 */
	void clear();
	// 通过删除所有映射来清除缓存。
	// 实际清除可能以异步或延迟方式执行，后续查找可能仍会看到条目。
	// 例如，事务缓存装饰器可能就是这种情况。使用invalidate()保证立即删除条目

	/**
	 * Invalidate the cache through removing all mappings, expecting all
	 * entries to be immediately invisible for subsequent lookups.
	 * @return {@code true} if the cache was known to have mappings before,
	 * {@code false} if it did not (or if prior presence of entries could
	 * not be determined)
	 * @since 5.2
	 * @see #clear()
	 */
	default boolean invalidate() {
		clear();
		return false;
	}
	// 意义: 通过删除所有映射使缓存无效，期望所有条目对于后续查找立即不可见


	/**
	 * A (wrapper) object representing a cache value.
	 */
	@FunctionalInterface
	interface ValueWrapper {
		// 函数式接口 -- 仅仅get真实值

		/**
		 * Return the actual value in the cache.
		 */
		@Nullable
		Object get();
	}


	/**
	 * Wrapper exception to be thrown from {@link #get(Object, Callable)}
	 * in case of the value loader callback failing with an exception.
	 * @since 4.3
	 */
	@SuppressWarnings("serial")
	class ValueRetrievalException extends RuntimeException {
		// 值检索失败的异常

		@Nullable
		private final Object key;

		public ValueRetrievalException(@Nullable Object key, Callable<?> loader, Throwable ex) {
			super(String.format("Value for key '%s' could not be loaded using '%s'", key, loader), ex);
			this.key = key;
		}

		@Nullable
		public Object getKey() {
			return this.key;
		}
	}

}
