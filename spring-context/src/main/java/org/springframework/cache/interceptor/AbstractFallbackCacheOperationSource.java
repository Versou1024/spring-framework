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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Abstract implementation of {@link CacheOperation} that caches attributes
 * for methods and implements a fallback policy: 1. specific target method;
 * 2. target class; 3. declaring method; 4. declaring class/interface.
 *
 * <p>Defaults to using the target class's caching attribute if none is
 * associated with the target method. Any caching attribute associated with
 * the target method completely overrides a class caching attribute.
 * If none found on the target class, the interface that the invoked method
 * has been called through (in case of a JDK proxy) will be checked.
 *
 * <p>This implementation caches attributes by method after they are first
 * used. If it is ever desirable to allow dynamic changing of cacheable
 * attributes (which is very unlikely), caching could be made configurable.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractFallbackCacheOperationSource implements CacheOperationSource {
	// CacheOperation的抽象实现，它为方法缓存属性并实现回退策略：
	// 	1. 特定目标方法；
	// 	2. 目标类；
	// 	3. 声明方法；
	// 	4. 声明类/接口

	/**
	 * Canonical value held in cache to indicate no caching attribute was
	 * found for this method and we don't need to look again.
	 */
	private static final Collection<CacheOperation> NULL_CACHING_ATTRIBUTE = Collections.emptyList();


	/**
	 * Logger available to subclasses.
	 * <p>As this base class is not marked Serializable, the logger will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Cache of CacheOperations, keyed by method on a specific target class.
	 * <p>As this base class is not marked Serializable, the cache will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	private final Map<Object, Collection<CacheOperation>> attributeCache = new ConcurrentHashMap<>(1024);
	// CacheOperations 的缓存，由特定目标类上的方法作为键设入。
	// 由于这个基类没有标记为 Serializable，缓存将在序列化后重新创建——前提是具体的子类是 Serializable


	/**
	 * Determine the caching attribute for this method invocation.
	 * <p>Defaults to the class's caching attribute if no method attribute is found.
	 * @param method the method for the current invocation (never {@code null})
	 * @param targetClass the target class for this invocation (may be {@code null})
	 * @return {@link CacheOperation} for this method, or {@code null} if the method
	 * is not cacheable
	 */
	@Override
	@Nullable
	public Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass) {
		// 确定目标类上的目标方法需要使用的缓存操作
		// AbstractFallbackCacheOperationSource 没有做根据method和targetClass实际构建 CacheOperation
		// 主要是子类通过 computeCacheOperations(method, targetClass) 完成

		// 1. Object下的方法不需要缓存
		if (method.getDeclaringClass() == Object.class) {
			return null;
		}

		// 2. 在当前类中的缓存key
		Object cacheKey = getCacheKey(method, targetClass);
		Collection<CacheOperation> cached = this.attributeCache.get(cacheKey);

		// 3. 缓存命中
		if (cached != null) {
			return (cached != NULL_CACHING_ATTRIBUTE ? cached : null);
		}
		else {
			// 4. 模板 -- 子类完成对method和targetClass计算CacheOperation
			Collection<CacheOperation> cacheOps = computeCacheOperations(method, targetClass);
			if (cacheOps != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Adding cacheable method '" + method.getName() + "' with attribute: " + cacheOps);
				}
				// 5. 存入缓存中
				this.attributeCache.put(cacheKey, cacheOps);
			}
			else {
				this.attributeCache.put(cacheKey, NULL_CACHING_ATTRIBUTE);
			}
			return cacheOps;
		}
	}

	/**
	 * Determine a cache key for the given method and target class.
	 * <p>Must not produce same key for overloaded methods.
	 * Must produce same key for different instances of the same method.
	 * @param method the method (never {@code null})
	 * @param targetClass the target class (may be {@code null})
	 * @return the cache key (never {@code null})
	 */
	protected Object getCacheKey(Method method, @Nullable Class<?> targetClass) {
		return new MethodClassKey(method, targetClass);
	}

	@Nullable
	private Collection<CacheOperation> computeCacheOperations(Method method, @Nullable Class<?> targetClass) {
		// Don't allow no-public methods as required.
		// 1. allowPublicMethodsOnly() 是否默认仅仅让public方法有缓存语义,如果是的话,method非public方法就发那会null
		if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		// The method may be on an interface, but we need attributes from the target class.
		// If the target class is null, the method will be unchanged.
		// 2. 该方法可能在接口上，但我们需要来自目标类的属性。如果目标类为空，则方法将保持不变。
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

		// First try is the method in the target class.
		// 3. 首先尝试的是目标方法。
		Collection<CacheOperation> opDef = findCacheOperations(specificMethod);
		if (opDef != null) {
			return opDef;
		}

		// Second try is the caching operation on the target class.
		// 4. 第二次尝试是对目标类上是否缓存操作。
		opDef = findCacheOperations(specificMethod.getDeclaringClass());
		if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
			return opDef;
		}

		// 5. 最佳的specificMethod,和实际的method不是一样的
		if (specificMethod != method) {
			// Fallback is to look at the original method.
			// 6. 回滚到查找接口上声明的接口method上的缓存操作
			opDef = findCacheOperations(method);
			if (opDef != null) {
				return opDef;
			}
			// Last fallback is the class of the original method.
			// 7. 最后一次回滚到 method 的声明的接口上查看缓存操作
			opDef = findCacheOperations(method.getDeclaringClass());
			if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
				return opDef;
			}
		}

		//  8. 否则就是没有缓存
		return null;
	}


	/**
	 * Subclasses need to implement this to return the caching attribute for the
	 * given class, if any.
	 * @param clazz the class to retrieve the attribute for
	 * @return all caching attribute associated with this class, or {@code null} if none
	 */
	@Nullable
	protected abstract Collection<CacheOperation> findCacheOperations(Class<?> clazz);

	/**
	 * Subclasses need to implement this to return the caching attribute for the
	 * given method, if any.
	 * @param method the method to retrieve the attribute for
	 * @return all caching attribute associated with this method, or {@code null} if none
	 */
	@Nullable
	protected abstract Collection<CacheOperation> findCacheOperations(Method method);

	/**
	 * Should only public methods be allowed to have caching semantics?
	 * <p>The default implementation returns {@code false}.
	 */
	protected boolean allowPublicMethodsOnly() {
		// 是否应该只允许公共方法具有缓存语义？
		return false;
	}

}
