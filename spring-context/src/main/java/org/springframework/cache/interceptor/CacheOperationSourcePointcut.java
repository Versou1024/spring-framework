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

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * A Pointcut that matches if the underlying {@link CacheOperationSource}
 * has an attribute for a given method.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
@SuppressWarnings("serial")
abstract class CacheOperationSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {
	
	// @EnableCaching开启将向ioc容器注入一个Advisor,其连接器pointcut就是这里的 CacheOperationSourcePointcut

	protected CacheOperationSourcePointcut() {
		// 构造器 -- 默认设置 CacheOperationSourceClassFilter 类过滤器
		setClassFilter(new CacheOperationSourceClassFilter());
	}


	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		// 实现 -- 静态的方法过滤器
		
		// 99%的情况都是 AnnotationCacheOperationSource 
		CacheOperationSource cas = getCacheOperationSource();
		// 只要使用 AnnotationCacheOperationSource 能够从targetClass的method上找到CacheOperation,就认为是需要拦截的哦
		return (cas != null && !CollectionUtils.isEmpty(cas.getCacheOperations(method, targetClass)));
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof CacheOperationSourcePointcut)) {
			return false;
		}
		CacheOperationSourcePointcut otherPc = (CacheOperationSourcePointcut) other;
		return ObjectUtils.nullSafeEquals(getCacheOperationSource(), otherPc.getCacheOperationSource());
	}

	@Override
	public int hashCode() {
		return CacheOperationSourcePointcut.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + getCacheOperationSource();
	}


	/**
	 * Obtain the underlying {@link CacheOperationSource} (may be {@code null}).
	 * To be implemented by subclasses.
	 */
	@Nullable
	protected abstract CacheOperationSource getCacheOperationSource();


	/**
	 * {@link ClassFilter} that delegates to {@link CacheOperationSource#isCandidateClass}
	 * for filtering classes whose methods are not worth searching to begin with.
	 */
	private class CacheOperationSourceClassFilter implements ClassFilter {

		// clazz不属于CacheManager
		// 且通过 CacheOperationSource 判断 clazz 是否为候选类
		// [类\方法\字段上只要标注有@Cacheable\@CacheEvict\@CachePut\@Caching]
		@Override
		public boolean matches(Class<?> clazz) {
			// 如果你这个类就是一个CacheManager，不切入
			if (CacheManager.class.isAssignableFrom(clazz)) {
				return false;
			}
			// 获取到当前的缓存属性源~~~getCacheOperationSource()是个抽象方法
			CacheOperationSource cas = getCacheOperationSource();
			// 下面一句话解释为：如果方法/类上标注有缓存相关的注解，就切入进取~~
			// 具体逻辑请参见方法：AnnotationCacheOperationSource.isCandidateClass()
			return (cas == null || cas.isCandidateClass(clazz));
		}
	}

}
