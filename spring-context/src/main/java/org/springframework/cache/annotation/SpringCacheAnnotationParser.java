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

package org.springframework.cache.annotation;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Strategy implementation for parsing Spring's {@link Caching}, {@link Cacheable},
 * {@link CacheEvict}, and {@link CachePut} annotations.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 3.1
 */
@SuppressWarnings("serial")
public class SpringCacheAnnotationParser implements CacheAnnotationParser, Serializable {
	// ❗️❗️❗️❗️❗️❗️
	// 核心从method或class上解析处缓存操作

	private static final Set<Class<? extends Annotation>> CACHE_OPERATION_ANNOTATIONS = new LinkedHashSet<>(8);

	static {
		// 主要是关注以下四个注解
		CACHE_OPERATION_ANNOTATIONS.add(Cacheable.class);
		CACHE_OPERATION_ANNOTATIONS.add(CacheEvict.class);
		CACHE_OPERATION_ANNOTATIONS.add(CachePut.class);
		CACHE_OPERATION_ANNOTATIONS.add(Caching.class);
	}


	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		// 是否为候选类
		return AnnotationUtils.isCandidateClass(targetClass, CACHE_OPERATION_ANNOTATIONS);
	}

	@Override
	@Nullable
	public Collection<CacheOperation> parseCacheAnnotations(Class<?> type) {
		// 类上的缓存操作

		// 使用DefaultCacheConfig，把它传给parseCacheAnnotations()  来给注解属性搞定默认值
		// 至于为何自己要新new一个呢？？？其实是因为@CacheConfig它只能放在类上呀~~~（传Method也能获取到类）
		// 返回值都可以为null（没有标注此注解方法、类  那肯定返回null啊）
		DefaultCacheConfig defaultConfig = new DefaultCacheConfig(type);
		return parseCacheAnnotations(defaultConfig, type);
	}

	@Override
	@Nullable
	public Collection<CacheOperation> parseCacheAnnotations(Method method) {
		// 方法上的缓存操作
		DefaultCacheConfig defaultConfig = new DefaultCacheConfig(method.getDeclaringClass());
		return parseCacheAnnotations(defaultConfig, method);
	}

	@Nullable
	private Collection<CacheOperation> parseCacheAnnotations(DefaultCacheConfig cachingConfig, AnnotatedElement ae) {
		// 第三个参数传的false：表示接口的注解它也会看一下~~~
		Collection<CacheOperation> ops = parseCacheAnnotations(cachingConfig, ae, false);
		// 若出现接口方法里也标了，实例方法里也标了，那就继续处理。让以实例方法里标注的为准~~~
		if (ops != null && ops.size() > 1) {
			// More than one operation found -> local declarations override interface-declared ones...
			// 发现多个操作 -> 本地声明覆盖接口声明的..
			Collection<CacheOperation> localOps = parseCacheAnnotations(cachingConfig, ae, true);
			if (localOps != null) {
				return localOps;
			}
		}
		return ops;
	}

	@Nullable
	private Collection<CacheOperation> parseCacheAnnotations(
			DefaultCacheConfig cachingConfig, AnnotatedElement ae, boolean localOnly) {
		// localOnly=true，只看自己的不看接口的。false表示接口的也得看~

		// 1. 获取方法或类上的所有相关注解
		// @Cacheable\@CacheEvict\@CachePut\@Caching
		Collection<? extends Annotation> anns = (localOnly ?
				AnnotatedElementUtils.getAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS) :
				AnnotatedElementUtils.findAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS));
		// 2. 为空,立即返回
		if (anns.isEmpty()) {
			return null;
		}

		// 3. 对注解过滤 @Cacheable\@CacheEvict\@CachePut\@Caching 四个注解值 -> 转换为对应的CacheOperation
		// 这里为和写个1？？？因为绝大多数情况下，我们都只会标注一个注解~~
		// 这里的方法parseCacheableAnnotation/parsePutAnnotation等 说白了  就是把注解属性，转换封装成为`CacheOperation`对象~~
		// 注意parseCachingAnnotation方法，相当于~把注解属性转换成了CacheOperation对象  下面以它为例介绍
		final Collection<CacheOperation> ops = new ArrayList<>(1);
		anns.stream().filter(ann -> ann instanceof Cacheable).forEach(
				ann -> ops.add(parseCacheableAnnotation(ae, cachingConfig, (Cacheable) ann)));
		anns.stream().filter(ann -> ann instanceof CacheEvict).forEach(
				ann -> ops.add(parseEvictAnnotation(ae, cachingConfig, (CacheEvict) ann)));
		anns.stream().filter(ann -> ann instanceof CachePut).forEach(
				ann -> ops.add(parsePutAnnotation(ae, cachingConfig, (CachePut) ann)));
		anns.stream().filter(ann -> ann instanceof Caching).forEach(
				ann -> parseCachingAnnotation(ae, cachingConfig, (Caching) ann, ops));
		return ops;
	}

	private CacheableOperation parseCacheableAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, Cacheable cacheable) {

		// 1. 方法上的@Cacheable或类上的@Cacheable注解
		// 将注解中的各种属性转换为Builder中的配置
		// 然后对于 @Cacheable 中没有设置的配置项,可以通过类上的 @CacheConfig 即默认配置去完成加载
		// 即 defaultConfig.applyDefault(builder)
		CacheableOperation.Builder builder = new CacheableOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cacheable.cacheNames());
		builder.setCondition(cacheable.condition());
		builder.setUnless(cacheable.unless());
		builder.setKey(cacheable.key());
		builder.setKeyGenerator(cacheable.keyGenerator());
		builder.setCacheManager(cacheable.cacheManager());
		builder.setCacheResolver(cacheable.cacheResolver());
		builder.setSync(cacheable.sync());

		// 2. 接受类上的 @CacheConfig 上默认的的配置属性 -- 前提是方法上或类上的 @Cacheable 注解没有对应的配置
		defaultConfig.applyDefault(builder);
		// 3. build()
		CacheableOperation op = builder.build();
		// 4.
		validateCacheOperation(ae, op);

		return op;
	}

	private CacheEvictOperation parseEvictAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, CacheEvict cacheEvict) {

		CacheEvictOperation.Builder builder = new CacheEvictOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cacheEvict.cacheNames());
		builder.setCondition(cacheEvict.condition());
		builder.setKey(cacheEvict.key());
		builder.setKeyGenerator(cacheEvict.keyGenerator());
		builder.setCacheManager(cacheEvict.cacheManager());
		builder.setCacheResolver(cacheEvict.cacheResolver());
		builder.setCacheWide(cacheEvict.allEntries()); // CacheWide - 对应的就是 @CacheEvict 的 allEntries 属性
		builder.setBeforeInvocation(cacheEvict.beforeInvocation()); // beforeInvocation -- 对应的就是 immediate

		defaultConfig.applyDefault(builder);
		CacheEvictOperation op = builder.build();
		validateCacheOperation(ae, op);

		return op;
	}

	private CacheOperation parsePutAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, CachePut cachePut) {

		CachePutOperation.Builder builder = new CachePutOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cachePut.cacheNames());
		builder.setCondition(cachePut.condition());
		builder.setUnless(cachePut.unless());
		builder.setKey(cachePut.key());
		builder.setKeyGenerator(cachePut.keyGenerator());
		builder.setCacheManager(cachePut.cacheManager());
		builder.setCacheResolver(cachePut.cacheResolver());

		defaultConfig.applyDefault(builder);
		CachePutOperation op = builder.build();
		validateCacheOperation(ae, op);

		return op;
	}

	private void parseCachingAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, Caching caching, Collection<CacheOperation> ops) {

		Cacheable[] cacheables = caching.cacheable();
		for (Cacheable cacheable : cacheables) {
			ops.add(parseCacheableAnnotation(ae, defaultConfig, cacheable));
		}
		CacheEvict[] cacheEvicts = caching.evict();
		for (CacheEvict cacheEvict : cacheEvicts) {
			ops.add(parseEvictAnnotation(ae, defaultConfig, cacheEvict));
		}
		CachePut[] cachePuts = caching.put();
		for (CachePut cachePut : cachePuts) {
			ops.add(parsePutAnnotation(ae, defaultConfig, cachePut));
		}
	}


	/**
	 * Validates the specified {@link CacheOperation}.
	 * <p>Throws an {@link IllegalStateException} if the state of the operation is
	 * invalid. As there might be multiple sources for default values, this ensure
	 * that the operation is in a proper state before being returned.
	 * @param ae the annotated element of the cache operation
	 * @param operation the {@link CacheOperation} to validate
	 */
	private void validateCacheOperation(AnnotatedElement ae, CacheOperation operation) {
		// 验证指定的CacheOperation 。
		// 如果操作的状态无效，则引发IllegalStateException 。由于默认值可能有多个来源，因此可以确保操作在返回之前处于正确状态。

		// 1. key 和 keyGenerator 只能有一个
		if (StringUtils.hasText(operation.getKey()) && StringUtils.hasText(operation.getKeyGenerator())) {
			throw new IllegalStateException("Invalid cache annotation configuration on '" +
					ae.toString() + "'. Both 'key' and 'keyGenerator' attributes have been set. " +
					"These attributes are mutually exclusive: either set the SpEL expression used to" +
					"compute the key at runtime or set the name of the KeyGenerator bean to use.");
		}
		// 2. cacheManager 和 cacheResolver 只能有一个
		if (StringUtils.hasText(operation.getCacheManager()) && StringUtils.hasText(operation.getCacheResolver())) {
			throw new IllegalStateException("Invalid cache annotation configuration on '" +
					ae.toString() + "'. Both 'cacheManager' and 'cacheResolver' attributes have been set. " +
					"These attributes are mutually exclusive: the cache manager is used to configure a" +
					"default cache resolver if none is set. If a cache resolver is set, the cache manager" +
					"won't be used.");
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || other instanceof SpringCacheAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringCacheAnnotationParser.class.hashCode();
	}


	/**
	 * Provides default settings for a given set of cache operations.
	 */
	private static class DefaultCacheConfig {
		// 默认缓存的config -- 对应每个注解的缓存配置

		private final Class<?> target;

		@Nullable
		private String[] cacheNames;

		@Nullable
		private String keyGenerator;

		@Nullable
		private String cacheManager;

		@Nullable
		private String cacheResolver;

		private boolean initialized = false;

		public DefaultCacheConfig(Class<?> target) {
			this.target = target;
		}

		/**
		 * Apply the defaults to the specified {@link CacheOperation.Builder}.
		 * @param builder the operation builder to update
		 */
		public void applyDefault(CacheOperation.Builder builder) {
			// 1. initialized 初始化为 false -- 仅仅第一次使用有效
			// 如果还没初始化过，就进行初始化（毕竟一个类上有多个方法，这种默认通用设置只需要来一次即可）
			if (!this.initialized) {
				// 2. 查找类上是否有 @CacheConfig 注解
				// 找到类上、接口上的@CacheConfig注解  它可以指定本类使用的cacheNames和keyGenerator和cacheManager和cacheResolver
				CacheConfig annotation = AnnotatedElementUtils.findMergedAnnotation(this.target, CacheConfig.class);
				if (annotation != null) {
					this.cacheNames = annotation.cacheNames();
					this.keyGenerator = annotation.keyGenerator();
					this.cacheManager = annotation.cacheManager();
					this.cacheResolver = annotation.cacheResolver();
				}
				this.initialized = true;
			}

			// 3. 没有@CacheConfig,但@CacheConfig上有设置cacheNames
			// 下面方法一句话总结：方法上没有指定的话，就用类上面的CacheConfig配置
			if (builder.getCacheNames().isEmpty() && this.cacheNames != null) {
				// 3.1 @CacheConfig上有设置cacheNames,就将其作为cacheNames
				builder.setCacheNames(this.cacheNames);
			}
			// 4. builder 中没有 key,且builder中乜有 KeyGenerator,但@CacheConfig有配置keyGenerator
			if (!StringUtils.hasText(builder.getKey()) && !StringUtils.hasText(builder.getKeyGenerator()) &&
					StringUtils.hasText(this.keyGenerator)) {
				// 4.1 使用 @CacheConfig的配置keyGenerator
				builder.setKeyGenerator(this.keyGenerator);
			}

			// 5. builder中没有cacheManager或者cacheResolver
			if (StringUtils.hasText(builder.getCacheManager()) || StringUtils.hasText(builder.getCacheResolver())) {
				// One of these is set so we should not inherit anything
			}
			// 5.1 类上的@CacheConfig中的cacheResolver属性值非空
			else if (StringUtils.hasText(this.cacheResolver)) {
				builder.setCacheResolver(this.cacheResolver);
			}
			// 5.2 类上的@CacheConfig中的cacheManager属性值非空
			else if (StringUtils.hasText(this.cacheManager)) {
				builder.setCacheManager(this.cacheManager);
			}
		}
	}

	// 经过这一番解析后，三大缓存注解，最终都被收集到CacheOperation里来了，这也就和CacheOperationSource缓存属性源接口的功能照应了起来。

}
