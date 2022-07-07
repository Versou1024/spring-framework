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

package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.Collection;

import org.springframework.lang.Nullable;

/**
 * Interface used by {@link CacheInterceptor}. Implementations know how to source
 * cache operation attributes, whether from configuration, metadata attributes at
 * source level, or elsewhere.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
public interface CacheOperationSource {
	// CacheOperation 指的指定缓存操作的属性
	// 缓存操作包括: 缓存set\缓存get\清除缓存
	// 对应的注解的缓存操作就是: @CachePut\@Cacheable\@CacheEvict
	
	// CacheOperationSource 用来从targetClass或者method上获取指定的缓存操作CacheOperation
	
	// CacheOperationSource 有三个实现类:
	//		NameMatchCacheOperationSource -- 根据指定beanName判断是否可以作为缓存操作CacheOperation
	//		AnnotationCacheOperationSource -- ❗️99%的使用❗️根据类上或方法上的注解@CachePut\@Cacheable\@CacheEvict\@Caching确定缓存操作CacheOperation
	//		CompositeCacheOperationSource -- 组合模式

	/**
	 * Determine whether the given class is a candidate for cache operations
	 * in the metadata format of this {@code CacheOperationSource}.
	 * <p>If this method returns {@code false}, the methods on the given class
	 * will not get traversed for {@link #getCacheOperations} introspection.
	 * Returning {@code false} is therefore an optimization for non-affected
	 * classes, whereas {@code true} simply means that the class needs to get
	 * fully introspected for each method on the given class individually.
	 * @param targetClass the class to introspect
	 * @return {@code false} if the class is known to have no cache operation
	 * metadata at class or method level; {@code true} otherwise. The default
	 * implementation returns {@code true}, leading to regular introspection.
	 * @since 5.2
	 */
	default boolean isCandidateClass(Class<?> targetClass) {
		return true;
	}
	// 确定给定类是否是此CacheOperationSource元数据格式的缓存操作的候选对象
	// 一般而言: 就是看是否有 @CachePut\@Cacheable\@CacheEvict\@Caching 注解

	/**
	 * Return the collection of cache operations for this method,
	 * or {@code null} if the method contains no <em>cacheable</em> annotations.
	 * @param method the method to introspect
	 * @param targetClass the target class (may be {@code null}, in which case
	 * the declaring class of the method must be used)
	 * @return all cache operations for this method, or {@code null} if none found
	 */
	@Nullable
	Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass);
	// 返回此方法的缓存操作集合，如果该方法不包含可缓存的注释，则返回null
	// 就是将方法上的 @CachePut\@Cacheable\@CacheEvict\@Caching 注解转换为对应的 CacheOperation
	// 所以可以把  CacheOperationSource 当做一个解析器吧

}
