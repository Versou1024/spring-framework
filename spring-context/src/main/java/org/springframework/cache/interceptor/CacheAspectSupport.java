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

package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.util.function.SupplierUtils;

/**
 * Base class for caching aspects, such as the {@link CacheInterceptor} or an
 * AspectJ aspect.
 *
 * <p>This enables the underlying Spring caching infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling relevant methods in the correct order.
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link CacheOperationSource} is
 * used for determining caching operations, a {@link KeyGenerator} will build the
 * cache keys, and a {@link CacheResolver} will resolve the actual cache(s) to use.
 *
 * <p>Note: A cache aspect is serializable but does not perform any actual caching
 * after deserialization.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheAspectSupport extends AbstractCacheInvoker
		implements BeanFactoryAware, InitializingBean, SmartInitializingSingleton {

	protected final Log logger = LogFactory.getLog(getClass());

	private final Map<CacheOperationCacheKey, CacheOperationMetadata> metadataCache = new ConcurrentHashMap<>(1024);

	private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator();

	// 使用策略设计模式。
	// 		CacheOperationSource用于确定缓存操作，
	// 		KeyGenerator将构建缓存键，
	// 		CacheResolver将解析要使用的实际缓存。

	@Nullable
	private CacheOperationSource cacheOperationSource; // spring注解环境一般使用 AnnotationCacheOperationSource()

	private SingletonSupplier<KeyGenerator> keyGenerator = SingletonSupplier.of(SimpleKeyGenerator::new);

	@Nullable
	private SingletonSupplier<CacheResolver> cacheResolver;

	@Nullable
	private BeanFactory beanFactory;

	private boolean initialized = false;

	// 无构造器

	/**
	 * Configure this aspect with the given error handler, key generator and cache resolver/manager
	 * suppliers, applying the corresponding default if a supplier is not resolvable.
	 * @since 5.1
	 */
	public void configure(
			@Nullable Supplier<CacheErrorHandler> errorHandler, @Nullable Supplier<KeyGenerator> keyGenerator,
			@Nullable Supplier<CacheResolver> cacheResolver, @Nullable Supplier<CacheManager> cacheManager) {
		// 用户没有扩展 -- 默认使用 SimpleCacheErrorHandler
		this.errorHandler = new SingletonSupplier<>(errorHandler, SimpleCacheErrorHandler::new);
		// 用户没有扩展 -- 默认使用 SimpleKeyGenerator
		this.keyGenerator = new SingletonSupplier<>(keyGenerator, SimpleKeyGenerator::new);
		// 用户没有扩展 -- 默认使用 SimpleCacheResolver --
		// 默认是 SimpleCacheResolver.of(null)
		// 或者 -- SimpleCacheResolver.of(cacheManager)
		// ❗️❗️❗️
		// 一般情况我们都是简单的加入一个CacheManger到ioc容器中,会加入到SimpleCacheResolver中
		this.cacheResolver = new SingletonSupplier<>(cacheResolver,
				() -> SimpleCacheResolver.of(SupplierUtils.resolve(cacheManager)));
	}


	/**
	 * Set one or more cache operation sources which are used to find the cache
	 * attributes. If more than one source is provided, they will be aggregated
	 * using a {@link CompositeCacheOperationSource}.
	 * @see #setCacheOperationSource
	 */
	public void setCacheOperationSources(CacheOperationSource... cacheOperationSources) {
		Assert.notEmpty(cacheOperationSources, "At least 1 CacheOperationSource needs to be specified");
		this.cacheOperationSource = (cacheOperationSources.length > 1 ?
				new CompositeCacheOperationSource(cacheOperationSources) : cacheOperationSources[0]);
	}

	/**
	 * Set the CacheOperationSource for this cache aspect.
	 * @since 5.1
	 * @see #setCacheOperationSources
	 */
	public void setCacheOperationSource(@Nullable CacheOperationSource cacheOperationSource) {
		this.cacheOperationSource = cacheOperationSource;
	}

	/**
	 * Return the CacheOperationSource for this cache aspect.
	 */
	@Nullable
	public CacheOperationSource getCacheOperationSource() {
		return this.cacheOperationSource;
	}

	/**
	 * Set the default {@link KeyGenerator} that this cache aspect should delegate to
	 * if no specific key generator has been set for the operation.
	 * <p>The default is a {@link SimpleKeyGenerator}.
	 */
	public void setKeyGenerator(KeyGenerator keyGenerator) {
		this.keyGenerator = SingletonSupplier.of(keyGenerator);
	}

	/**
	 * Return the default {@link KeyGenerator} that this cache aspect delegates to.
	 */
	public KeyGenerator getKeyGenerator() {
		return this.keyGenerator.obtain();
	}

	/**
	 * Set the default {@link CacheResolver} that this cache aspect should delegate
	 * to if no specific cache resolver has been set for the operation.
	 * <p>The default resolver resolves the caches against their names and the
	 * default cache manager.
	 * @see #setCacheManager
	 * @see SimpleCacheResolver
	 */
	public void setCacheResolver(@Nullable CacheResolver cacheResolver) {
		this.cacheResolver = SingletonSupplier.ofNullable(cacheResolver);
	}

	/**
	 * Return the default {@link CacheResolver} that this cache aspect delegates to.
	 */
	@Nullable
	public CacheResolver getCacheResolver() {
		return SupplierUtils.resolve(this.cacheResolver);
	}

	/**
	 * Set the {@link CacheManager} to use to create a default {@link CacheResolver}.
	 * Replace the current {@link CacheResolver}, if any.
	 * @see #setCacheResolver
	 * @see SimpleCacheResolver
	 */
	public void setCacheManager(CacheManager cacheManager) {
		this.cacheResolver = SingletonSupplier.of(new SimpleCacheResolver(cacheManager));
	}

	/**
	 * Set the containing {@link BeanFactory} for {@link CacheManager} and other
	 * service lookups.
	 * @since 4.3
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		Assert.state(getCacheOperationSource() != null, "The 'cacheOperationSources' property is required: " +
				"If there are no cacheable methods, then don't use a cache aspect.");
	}

	@Override
	public void afterSingletonsInstantiated() {
		// 在所有单例Bean实例化之后调用

		if (getCacheResolver() == null) {
			// Lazily initialize cache resolver via default cache manager...
			// 若没有给这个切面手动设置cacheResolver  那就去拿CacheManager吧
			// 这就是为何我们只需要把CacheManager配进容器里即可  就自动会设置在切面里了
			Assert.state(this.beanFactory != null, "CacheResolver or BeanFactory must be set on cache aspect");
			try {
				setCacheManager(this.beanFactory.getBean(CacheManager.class));
			}
			catch (NoUniqueBeanDefinitionException ex) {
				throw new IllegalStateException("No CacheResolver specified, and no unique bean of type " +
						"CacheManager found. Mark one as primary or declare a specific CacheManager to use.");
			}
			catch (NoSuchBeanDefinitionException ex) {
				throw new IllegalStateException("No CacheResolver specified, and no bean of type CacheManager found. " +
						"Register a CacheManager bean or remove the @EnableCaching annotation from your configuration.");
			}
		}
		// 已经初始化标记设置为true
		this.initialized = true;
	}


	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * @param method the method we're interested in
	 * @param targetClass class the method is on
	 * @return log message identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	protected String methodIdentification(Method method, Class<?> targetClass) {
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		return ClassUtils.getQualifiedMethodName(specificMethod);
	}

	protected Collection<? extends Cache> getCaches(
			CacheOperationInvocationContext<CacheOperation> context, CacheResolver cacheResolver) {

		Collection<? extends Cache> caches = cacheResolver.resolveCaches(context);
		if (caches.isEmpty()) {
			throw new IllegalStateException("No cache could be resolved for '" +
					context.getOperation() + "' using resolver '" + cacheResolver +
					"'. At least one cache should be provided per cache operation.");
		}
		return caches;
	}

	protected CacheOperationContext getOperationContext(
			CacheOperation operation, Method method, Object[] args, Object target, Class<?> targetClass) {
		// 获取 CacheOperationContext

		// 1. 获取元数据
		CacheOperationMetadata metadata = getCacheOperationMetadata(operation, method, targetClass);
		// 2. 转换为 CacheOperationContext
		return new CacheOperationContext(metadata, args, target);
	}

	/**
	 * Return the {@link CacheOperationMetadata} for the specified operation.
	 * <p>Resolve the {@link CacheResolver} and the {@link KeyGenerator} to be
	 * used for the operation.
	 * @param operation the operation
	 * @param method the method on which the operation is invoked
	 * @param targetClass the target type
	 * @return the resolved metadata for the operation
	 */
	protected CacheOperationMetadata getCacheOperationMetadata(
			CacheOperation operation, Method method, Class<?> targetClass) {

		// 1. CacheOperationMetadata 元数据缓存是否命中
		CacheOperationCacheKey cacheKey = new CacheOperationCacheKey(operation, method, targetClass);
		CacheOperationMetadata metadata = this.metadataCache.get(cacheKey);
		if (metadata == null) {
			// 2. 缓存未命中 -- 创建 CacheOperationMetadata
			// ❗❗️❗️❗️❗️
			// ️注意各种注解的配置写入到CacheOperation,都是String值,没有做实际的Spel运算和从ioc容器获取
			// 在这个创建过程中去完成

			// 2.1 创建 KeyGenerator
			// 从 ioc容器 加载 KeyGenerator
			KeyGenerator operationKeyGenerator;
			if (StringUtils.hasText(operation.getKeyGenerator())) {
				operationKeyGenerator = getBean(operation.getKeyGenerator(), KeyGenerator.class);
			}
			else {
				operationKeyGenerator = getKeyGenerator();
			}
			// 2.2 创建 CacheResolver
			// 从 ioc 容器 加载 CacheResolver,不行就退而求其次, 加载 CacheManager 放入并创建 SimpleCacheResolver也可以
			// ❗️❗️❗️❗️
			// 如果你的 @Cacheable @CacheEvict @CachePut 等注解没有指定CacheManger或者CacheResolver
			// 去 getCacheResolver() 获取本切面摩尔恩的 CacheResolver;
			// 在 afterSingleInstantiated() 方法中若用户没有设置CacheResolver,就会去ioc容器加载CacheManger,封装为一个SimpleCacheResovler来使用
			CacheResolver operationCacheResolver;
			if (StringUtils.hasText(operation.getCacheResolver())) {
				operationCacheResolver = getBean(operation.getCacheResolver(), CacheResolver.class);
			}
			else if (StringUtils.hasText(operation.getCacheManager())) {
				CacheManager cacheManager = getBean(operation.getCacheManager(), CacheManager.class);
				operationCacheResolver = new SimpleCacheResolver(cacheManager);
			}
			else {
				//最终都没配置的话，取本切面默认的
				operationCacheResolver = getCacheResolver();
				Assert.state(operationCacheResolver != null, "No CacheResolver/CacheManager set");
			}
			// 3. 创建
			metadata = new CacheOperationMetadata(operation, method, targetClass,
					operationKeyGenerator, operationCacheResolver);
			// 4. 缓存
			this.metadataCache.put(cacheKey, metadata);
		}
		return metadata;
	}

	/**
	 * Return a bean with the specified name and type. Used to resolve services that
	 * are referenced by name in a {@link CacheOperation}.
	 * @param beanName the name of the bean, as defined by the operation
	 * @param expectedType type for the bean
	 * @return the bean matching that name
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if such bean does not exist
	 * @see CacheOperation#getKeyGenerator()
	 * @see CacheOperation#getCacheManager()
	 * @see CacheOperation#getCacheResolver()
	 */
	protected <T> T getBean(String beanName, Class<T> expectedType) {
		if (this.beanFactory == null) {
			throw new IllegalStateException(
					"BeanFactory must be set on cache aspect for " + expectedType.getSimpleName() + " retrieval");
		}
		return BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, expectedType, beanName);
	}

	/**
	 * Clear the cached metadata.
	 */
	protected void clearMetadataCache() {
		this.metadataCache.clear();
		this.evaluator.clear();
	}

	@Nullable
	protected Object execute(CacheOperationInvoker invoker, Object target, Method method, Object[] args) {
		// Check whether aspect is enabled (to cope with cases where the AJ is pulled in automatically)
		// 检查aspect是否开启（应对自动拉入AJ的情况）
		if (this.initialized) {
			Class<?> targetClass = getTargetClass(target);
			CacheOperationSource cacheOperationSource = getCacheOperationSource(); // 现在都是 AnnotationCacheOperationSource
			if (cacheOperationSource != null) {
				// 核心 -- 从method获取CacheOperation缓存操作集合
				// 最终实际委托给 -- SpringCacheAnnotationParser 处理啦
				Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
				if (!CollectionUtils.isEmpty(operations)) {
					// 核心 -- 对每个缓存操作动作进行执行
					// ❗️❗️❗️ -- 会创建一个 CacheOperationContexts 操作上下文

					// CacheOperationContexts是非常重要的一个私有内部类
					// 注意它是复数哦~不是CacheOperationContext单数  所以它就像持有多个注解上下文一样  一个个执行吧
					// 所以我建议先看看此类的描述，再继续往下看~~~
					return execute(invoker, method,
							new CacheOperationContexts(operations, method, args, target, targetClass));
				}
			}
		}

		return invoker.invoke();
	}

	/**
	 * Execute the underlying operation (typically in case of cache miss) and return
	 * the result of the invocation. If an exception occurs it will be wrapped in a
	 * {@link CacheOperationInvoker.ThrowableWrapper}: the exception can be handled
	 * or modified but it <em>must</em> be wrapped in a
	 * {@link CacheOperationInvoker.ThrowableWrapper} as well.
	 * @param invoker the invoker handling the operation being cached
	 * @return the result of the invocation
	 * @see CacheOperationInvoker#invoke()
	 */
	protected Object invokeOperation(CacheOperationInvoker invoker) {
		return invoker.invoke();
	}

	private Class<?> getTargetClass(Object target) {
		// 防止代理
		return AopProxyUtils.ultimateTargetClass(target);
	}

	@Nullable
	private Object execute(final CacheOperationInvoker invoker, Method method, CacheOperationContexts contexts) {
		// Special handling of synchronized invocation
		// 1. 同步调用的特殊处理 -- 有同步,给表名只有一个@Cacheable注解
		// 因此下面的cache只有一个,而且处理完就返回啦 -- 全都因为只有一个注解
		if (contexts.isSynchronized()) {
			// 2. 获取 CacheableOperation 操作的上下文
			CacheOperationContext context = contexts.get(CacheableOperation.class).iterator().next();
			// 3.1 检查是否可以通过Condition
			if (isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
				// 3.1.1 创建key
				Object key = generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
				// 3.1.2 获取待执行的 cache
				Cache cache = context.getCaches().iterator().next();
				try {
					// 3.1.3 同步处理Get
					return wrapCacheValue(method, handleSynchronizedGet(invoker, key, cache));
				}
				catch (Cache.ValueRetrievalException ex) {
					// Directly propagate ThrowableWrapper from the invoker,
					// or potentially also an IllegalArgumentException etc.
					ReflectionUtils.rethrowRuntimeException(ex.getCause());
				}
			}
			// 3.2 检查没有通过Condition
			else {
				// No caching required, only call the underlying method
				// 3.2.1 仅仅执行目标方法返回对应的值,和上面不同,上面get到方法返回值后加入到缓存空间cache中
				return invokeOperation(invoker);
			}
		}

		// ------
		// 注意: 如果有 @Cacheable(sync=true) [且只有一个,否则创建的过程就已经报错啦], 在上面就已经执行完啦
		// 而其余情况是,允许 Cache系列注解合并使用的,例如 @Cacheable + @CacheEvict + @CachePut 等


		// Process any early evictions
		// 1. 处理任何早期驱逐 -- @CacheEvict 早期驱逐要求 -- BeforeInvocation为true
		// 首先从contexts中获取@CacheEvict对应的CacheEvictOperation的操作上下文
		processCacheEvicts(contexts.get(CacheEvictOperation.class), true,
				CacheOperationExpressionEvaluator.NO_RESULT);

		// Check if we have a cached item matching the conditions
		// 2. 如果有@Cacheable,就可以去看看缓存空间中是否有符合条件的缓存项
		Cache.ValueWrapper cacheHit = findCachedItem(contexts.get(CacheableOperation.class));

		// Collect puts from any @Cacheable miss, if no cached item is found
		// 3. 没有@Cachebale注解或者缓存空间中未命中,就不得不调用方法获取结果
		// 并加入到缓存中
		List<CachePutRequest> cachePutRequests = new LinkedList<>();
		if (cacheHit == null) {
			// 3.1 获取所有 @Cacheable 的操作上下文 -- 结果存入 cachePutRequests
			// result 为 NO_RESULT
			// 注意 -- 仅仅收集@Cacheable的CachePutRequest,不会做cache的相关操作
			collectPutRequests(contexts.get(CacheableOperation.class), CacheOperationExpressionEvaluator.NO_RESULT, cachePutRequests);
		}
		// 如果缓存命中 -- 是不会收集@Cacheable的放置请求
		// @Cacheable在缓存命中时,就不回去触发方法的执行
		// 当有@Cacheput时不管缓存命中与否,都会执行方法,将其返回值放入到缓存空间
		// @Cacheable和@CachePut混用,就需要注意上面的逻辑

		Object cacheValue; // 缓存的值
		Object returnValue; // 返回的值

		// 4. 缓存命中,contexts中不含有@CachePut的上下文
		if (cacheHit != null && !hasCachePut(contexts)) {
			// If there are no put requests, just use the cache hit
			// 4.1 如果没有put请求，就使用缓存命中的结果,如果是返回类型OPTIONAL还需要包装一下再返回
			// 因为OPTIONAL类型的值加入缓存空间时,会进行unWrapper
			cacheValue = cacheHit.get();
			returnValue = wrapCacheValue(method, cacheValue);
		}
		else {
			// Invoke the method if we don't have a cache hit
			// 缓存未命中 -- 执行方法拿到返回值 ✅
			// 或者,就算你缓存命中,但有@Cacheput -- 还是会执行方法,并且缓存值使用返回值,此时缓存命中的cacheHit是无效的
			// 执行方法获取返回值 returnValue
			returnValue = invokeOperation(invoker);
			cacheValue = unwrapReturnValue(returnValue);
		}

		// Collect any explicit @CachePuts
		// 5. 收集 @CachePut
		collectPutRequests(contexts.get(CachePutOperation.class), cacheValue, cachePutRequests);

		// Process any collected put requests, either from @CachePut or a @Cacheable miss
		// 6. 处理来自 @CachePut 或 @Cacheable 缓存未命中时将任何结果存入缓存空间的放置请求
		for (CachePutRequest cachePutRequest : cachePutRequests) {
			// 注意：此处unless啥的生效~~~~
			// 最终执行cache.put(key, result);方法
			cachePutRequest.apply(cacheValue);
		}

		// Process any late evictions
		// 7. 处理任何在方法执行完之后的驱逐
		processCacheEvicts(contexts.get(CacheEvictOperation.class), false, cacheValue);

		return returnValue;
	}

	@Nullable
	private Object handleSynchronizedGet(CacheOperationInvoker invoker, Object key, Cache cache) {
		InvocationAwareResult invocationResult = new InvocationAwareResult();
		// 1. 调用 cache.get() 方法查询key
		// 如果key存在直接返回对应value,如果不存在就触发 ValueLoader#call() 即下面这个lambda表达式
		// 触发 invoker 即原始方法的执行,获取其结果
		Object result = cache.get(key, () -> {
			invocationResult.invoked = true;
			if (logger.isTraceEnabled()) {
				logger.trace("No cache entry for key '" + key + "' in cache " + cache.getName());
			}
			// invokeOperation(invoker) -> 源码 : return invoker.invoke()
			// unwrapReturnValue -> 源码: return ObjectUtils.unwrapOptional(returnValue) 获取返回值是Optional时的真实值
			// ❗️❗️❗️ 因此注意 -- 实际Optional存储的不是Optional而是包装的真实value
			return unwrapReturnValue(invokeOperation(invoker));
		});
		if (!invocationResult.invoked && logger.isTraceEnabled()) {
			logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
		}
		return result;
	}

	@Nullable
	private Object wrapCacheValue(Method method, @Nullable Object cacheValue) {
		if (method.getReturnType() == Optional.class &&
				(cacheValue == null || cacheValue.getClass() != Optional.class)) {
			return Optional.ofNullable(cacheValue);
		}
		return cacheValue;
	}

	@Nullable
	private Object unwrapReturnValue(Object returnValue) {
		return ObjectUtils.unwrapOptional(returnValue);
	}

	private boolean hasCachePut(CacheOperationContexts contexts) {
		// Evaluate the conditions *without* the result object because we don't have it yet...
		Collection<CacheOperationContext> cachePutContexts = contexts.get(CachePutOperation.class);
		Collection<CacheOperationContext> excluded = new ArrayList<>();
		for (CacheOperationContext context : cachePutContexts) {
			try {
				if (!context.isConditionPassing(CacheOperationExpressionEvaluator.RESULT_UNAVAILABLE)) {
					excluded.add(context);
				}
			}
			catch (VariableNotAvailableException ex) {
				// Ignoring failure due to missing result, consider the cache put has to proceed
			}
		}
		// Check if all puts have been excluded by condition
		return (cachePutContexts.size() != excluded.size());
	}

	private void processCacheEvicts(Collection<CacheOperationContext> contexts, boolean beforeInvocation, @Nullable Object result) {
		// 遍历 -- @CacheEvict 的 CacheEvictOperation 的 CacheOperationContext
		// ❗️❗️❗️CacheEvictOperation PK CacheOperationContext
		// CacheEvictOperation 中有 @CacheEvict 各种配置的String值
		// CacheOperationContext 中将 CacheEvictOperation 全限定名转为对应的类,已经对应的Cache

		// 1. 遍历 CacheOperationContext -- 只有是符合beforeInvocation=true早期驱逐或beforeInvocation=false晚期驱逐
		// 且通过 @CacheEvict 上的Condition注解,就可以调用performCacheEvict() 处理驱逐
		for (CacheOperationContext context : contexts) {
			CacheEvictOperation operation = (CacheEvictOperation) context.metadata.operation;
			if (beforeInvocation == operation.isBeforeInvocation() && isConditionPassing(context, result)) {
				performCacheEvict(context, operation, result);
			}
		}
	}

	private void performCacheEvict(
			CacheOperationContext context, CacheEvictOperation operation, @Nullable Object result) {

		// 1. 获取所有的cache
		// 经历下面的操作
		Object key = null;
		for (Cache cache : context.getCaches()) {
			// 1. 通配 -- 对应的就是 @CacheEvict 的 allEntries 属性的true/false
			if (operation.isCacheWide()) {
				logInvalidating(context, operation, null); // 日志
				doClear(cache, operation.isBeforeInvocation()); // doClear 或 doEvict
			}
			// 2. 指定key
			else {
				if (key == null) {
					key = generateKey(context, result);
				}
				logInvalidating(context, operation, key);
				doEvict(cache, key, operation.isBeforeInvocation());
			}
		}
	}

	private void logInvalidating(CacheOperationContext context, CacheEvictOperation operation, @Nullable Object key) {
		if (logger.isTraceEnabled()) {
			logger.trace("Invalidating " + (key != null ? "cache key [" + key + "]" : "entire cache") +
					" for operation " + operation + " on method " + context.metadata.method);
		}
	}

	/**
	 * Find a cached item only for {@link CacheableOperation} that passes the condition.
	 * @param contexts the cacheable operations
	 * @return a {@link Cache.ValueWrapper} holding the cached item,
	 * or {@code null} if none is found
	 */
	@Nullable
	private Cache.ValueWrapper findCachedItem(Collection<CacheOperationContext> contexts) {
		// 遍历 @Cacheable 的使用
		Object result = CacheOperationExpressionEvaluator.NO_RESULT;
		for (CacheOperationContext context : contexts) {
			// 1. 是否通过 condition
			if (isConditionPassing(context, result)) {
				// 2. 生成 key
				Object key = generateKey(context, result);
				// 3. 通过 cache 查找 key
				Cache.ValueWrapper cached = findInCaches(context, key);
				// 4. 缓存命中,直接返回
				if (cached != null) {
					return cached;
				}
				// 5. 缓存未命中,提示
				else {
					if (logger.isTraceEnabled()) {
						logger.trace("No cache entry for key '" + key + "' in cache(s) " + context.getCacheNames());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Collect the {@link CachePutRequest} for all {@link CacheOperation} using
	 * the specified result item.
	 * @param contexts the contexts to handle
	 * @param result the result item (never {@code null})
	 * @param putRequests the collection to update
	 */
	private void collectPutRequests(Collection<CacheOperationContext> contexts,
			@Nullable Object result, Collection<CachePutRequest> putRequests) {

		// 1. 遍历所有的CacheOperationContext,检查是否通过Condition
		// 然后生成key,创建CachePutRequest加入putRequests中
		for (CacheOperationContext context : contexts) {
			if (isConditionPassing(context, result)) {
				Object key = generateKey(context, result);
				putRequests.add(new CachePutRequest(context, key));
			}
		}
	}

	@Nullable
	private Cache.ValueWrapper findInCaches(CacheOperationContext context, Object key) {
		for (Cache cache : context.getCaches()) {
			Cache.ValueWrapper wrapper = doGet(cache, key);
			if (wrapper != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
				}
				return wrapper;
			}
		}
		return null;
	}

	private boolean isConditionPassing(CacheOperationContext context, @Nullable Object result) {
		// 是否可以通过 Condition 属性
		boolean passing = context.isConditionPassing(result);
		if (!passing && logger.isTraceEnabled()) {
			logger.trace("Cache condition failed on method " + context.metadata.method +
					" for operation " + context.metadata.operation);
		}
		return passing;
	}

	private Object generateKey(CacheOperationContext context, @Nullable Object result) {
		Object key = context.generateKey(result);
		if (key == null) {
			throw new IllegalArgumentException("Null key returned for cache operation (maybe you are " +
					"using named params on classes without debug info?) " + context.metadata.operation);
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Computed cache key '" + key + "' for operation " + context.metadata.operation);
		}
		return key;
	}


	private class CacheOperationContexts {
		// 内部类 -- 高聚合

		// key就是CacheOperation实现类的class,value就是这个实现类对应的注解的属性值
		private final MultiValueMap<Class<? extends CacheOperation>, CacheOperationContext> contexts;

		// 多个线程执行时,是否需要同步
		private final boolean sync;

		public CacheOperationContexts(Collection<? extends CacheOperation> operations, Method method,
				Object[] args, Object target, Class<?> targetClass) {

			this.contexts = new LinkedMultiValueMap<>(operations.size());
			// 遍历 operations -> 将他们规划为实际所属的某个操作
			// 比如 CacheableOperation\CacheEvictOperation\CacheputOperation
			// 然后将每个 operation 包装到 OperationContext 中
			// 再存入到 contexts 对应 op类型上 的 多值List中
			for (CacheOperation op : operations) {
				// ❗️❗️❗️
				// 注意 getOperationContext
				this.contexts.add(op.getClass(), getOperationContext(op, method, args, target, targetClass));
			}
			//
			this.sync = determineSyncFlag(method);
		}

		public Collection<CacheOperationContext> get(Class<? extends CacheOperation> operationClass) {
			Collection<CacheOperationContext> result = this.contexts.get(operationClass);
			return (result != null ? result : Collections.emptyList());
		}

		public boolean isSynchronized() {
			return this.sync;
		}

		// 因为只有@Cacheable有sync属性，所以只需要看CacheableOperation即可
		private boolean determineSyncFlag(Method method) {
			// 1. 拿到 @CacheableOperation 所有对应的 CacheOperation 的 CacheOperationContext
			List<CacheOperationContext> cacheOperationContexts = this.contexts.get(CacheableOperation.class);
			// 2. 没有 @Cacheable -- 那么就不同步
			if (cacheOperationContexts == null) {  // no @Cacheable operation at all
				return false;
			}
			boolean syncEnabled = false;
			// 3. 只要有一个注解 @Cacheable 标注属性 isSync 为 true, syncEnabled就设置为true
			for (CacheOperationContext cacheOperationContext : cacheOperationContexts) {
				if (((CacheableOperation) cacheOperationContext.getOperation()).isSync()) {
					syncEnabled = true;
					break;
				}
			}
			if (syncEnabled) {
				// 4.1 @Cacheable(sync=true) 是无法与多个缓存操作联合 -- 比如 @CacheEvict\@CachePut等等
				// 人话解释：sync=true时候，不能还有其它的缓存操作 也就是说@Cacheable(sync=true)的时候只能单独使用
				if (this.contexts.size() > 1) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) cannot be combined with other cache operations on '" + method + "'");
				}
				// 4.2 开启缓存方法同步后时, @Cacheable(sync=true) 也只能有一个,不能和其他 @Cacheable(sync=false|true) 联合
				// 人话解释：@Cacheable(sync=true)时，多个@Cacheable也是不允许的
				if (cacheOperationContexts.size() > 1) {
					throw new IllegalStateException(
							"Only one @Cacheable(sync=true) entry is allowed on '" + method + "'");
				}
				/// 4.3 拿到 @Cacheable(sync=true) 对应的 CacheableOperation
				// 拿到唯一的一个@Cacheable
				CacheOperationContext cacheOperationContext = cacheOperationContexts.iterator().next();
				CacheableOperation operation = (CacheableOperation) cacheOperationContext.getOperation();
				// 4.4 @Cacheable(sync=true) 中 caches 超过躲过
				// 人话解释：@Cacheable(sync=true)时，cacheName只能使用一个
				if (cacheOperationContext.getCaches().size() > 1) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) only allows a single cache on '" + operation + "'");
				}
				// 4.5 @Cacheable(sync=true) 不支持同时使用 Unless 属性哦
				// 人话解释：sync=true时，unless属性是不支持的~~~并且是不能写的
				if (StringUtils.hasText(operation.getUnless())) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) does not support unless attribute on '" + operation + "'");
				}
				// 只有校验都通过后，才返回true
				return true;
			}
			return false;
		}
	}


	/**
	 * Metadata of a cache operation that does not depend on a particular invocation
	 * which makes it a good candidate for caching.
	 */
	protected static class CacheOperationMetadata {
		// 元数据
		// 持有 CacheOperation\Method\targetClass\targetMethod
		// 并且将 CacheOperation String类型的 keyGenerator/ cacheResolver 转换为 真实类型后加载进来

		private final CacheOperation operation;

		private final Method method;

		private final Class<?> targetClass;

		private final Method targetMethod;

		private final AnnotatedElementKey methodKey;

		private final KeyGenerator keyGenerator;

		private final CacheResolver cacheResolver;

		public CacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass,
				KeyGenerator keyGenerator, CacheResolver cacheResolver) {

			this.operation = operation;
			this.method = BridgeMethodResolver.findBridgedMethod(method);
			this.targetClass = targetClass;
			this.targetMethod = (!Proxy.isProxyClass(targetClass) ?
					AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
			this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
			this.keyGenerator = keyGenerator;
			this.cacheResolver = cacheResolver;
		}
	}


	/**
	 * A {@link CacheOperationInvocationContext} context for a {@link CacheOperation}.
	 */
	protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {
		// 缓存操作的上下文
		// 包括 args\target\caches\cacheNames\
		// 它里面包含了CacheOperation、Method、Class、Method targetMethod;(注意有两个Method)、AnnotatedElementKey、KeyGenerator、CacheResolver等属性
		// this.method = BridgeMethodResolver.findBridgedMethod(method);
		// this.targetMethod = (!Proxy.isProxyClass(targetClass) ? AopUtils.getMostSpecificMethod(method, targetClass)  : this.method);
		// this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);

		private final CacheOperationMetadata metadata;

		private final Object[] args;

		private final Object target;

		private final Collection<? extends Cache> caches;

		private final Collection<String> cacheNames;

		// 标志CacheOperation.conditon是否是true：表示通过  false表示未通过
		@Nullable
		private Boolean conditionPassing;

		public CacheOperationContext(CacheOperationMetadata metadata, Object[] args, Object target) {
			this.metadata = metadata;
			this.args = extractArgs(metadata.method, args);
			this.target = target;
			// ❗️ ❗️ ❗️ ❗️ ❗️ ❗️
			// 实际执行缓存的操作 -- 交给 caches -- 注意
			// 这里方法里调用了cacheResolver.resolveCaches(context)方法来得到缓存们~~~~  CacheResolver
			// SimpleCacheResolver 又是通过 CacheManger 来根据cacheNames来做查找的
			this.caches = CacheAspectSupport.this.getCaches(this, metadata.cacheResolver);
			// 从 caches 遍历获取到 Cache#getName()的值
			this.cacheNames = createCacheNames(this.caches);
		}

		@Override
		public CacheOperation getOperation() {
			return this.metadata.operation;
		}

		@Override
		public Object getTarget() {
			return this.target;
		}

		@Override
		public Method getMethod() {
			return this.metadata.method;
		}

		@Override
		public Object[] getArgs() {
			return this.args;
		}

		private Object[] extractArgs(Method method, Object[] args) {
			if (!method.isVarArgs()) {
				return args;
			}
			Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
			Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
			System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
			System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
			return combinedArgs;
		}

		protected boolean isConditionPassing(@Nullable Object result) {
			// 1. 如果配置了并且还没被解析过，此处就解析condition条件~~~
			if (this.conditionPassing == null) {
				// 2. 获取原生的注解上的 Condition 值
				if (StringUtils.hasText(this.metadata.operation.getCondition())) {
					// 3. 创建 EvaluationContext -- 利用 CacheOperationExpressionEvaluator.createEvaluationContext
					EvaluationContext evaluationContext = createEvaluationContext(result);
					// 4. 调用 condition -- 并将前面生成的 evaluationContext 放入其中
					this.conditionPassing = evaluator.condition(this.metadata.operation.getCondition(),
							this.metadata.methodKey, evaluationContext);
				}
				else {
					this.conditionPassing = true;
				}
			}
			return this.conditionPassing;
		}

		// 解析CacheableOperation和CachePutOperation的unless
		protected boolean canPutToCache(@Nullable Object value) {
			String unless = "";
			if (this.metadata.operation instanceof CacheableOperation) {
				unless = ((CacheableOperation) this.metadata.operation).getUnless();
			}
			else if (this.metadata.operation instanceof CachePutOperation) {
				unless = ((CachePutOperation) this.metadata.operation).getUnless();
			}
			if (StringUtils.hasText(unless)) {
				EvaluationContext evaluationContext = createEvaluationContext(value);
				return !evaluator.unless(unless, this.metadata.methodKey, evaluationContext);
			}
			return true;
		}

		// 这里注意：生成key  需要注意步骤。
		// 若配置了key（非空串）：那就作为SpEL解析出来
		// 否则走keyGenerator去生成~~~（所以你会发现，即使咱们没有配置key和keyGenerator，程序依旧能正常work,只是生成的key很长而已~~~）
		// （keyGenerator你可以能没配置？？？？）
		// 若你自己没有手动指定过KeyGenerator，那会使用默认的SimpleKeyGenerator 它的实现比较简单
		// 其实若我们自定义KeyGenerator，我觉得可以继承自`SimpleKeyGenerator `，而不是直接实现接口~~~
		/**
		 * Compute the key for the given caching operation.
		 */
		@Nullable
		protected Object generateKey(@Nullable Object result) {
			// 需要自动key和keyGenerator只能指定一个,同时指定将抛出异常
			// 调用栈
			// org.springframework.cache.interceptor.CacheInterceptor.invoke() -- 拦截器调用
			// org.springframework.cache.interceptor.CacheAspectSupport.execute() -- 执行方法
			// org.springframework.cache.interceptor.AbstractFallbackCacheOperationSource.getCacheOperations() -- 查找方法或类上的cacheOperations
			// org.springframework.cache.annotation.AnnotationCacheOperationSource.findCacheOperations(java.lang.reflect.Method)
			// ...
			// org.springframework.cache.annotation.SpringCacheAnnotationParser.validateCacheOperation -- 验证CacheOperation是否正确

			// 1. 注解中指定的缓存的key
			if (StringUtils.hasText(this.metadata.operation.getKey())) {
				// 1.1 创建 EvaluationContext,并且调用 org.springframework.cache.interceptor.CacheOperationExpressionEvaluator.key() 方法
				// 将注解上原生的key做spel解析后发那会
				EvaluationContext evaluationContext = createEvaluationContext(result);
				return evaluator.key(this.metadata.operation.getKey(), this.metadata.methodKey, evaluationContext);
			}
			// 2. 没有指定缓存的key,就是用keyGenerator来创建key
			return this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
		}

		private EvaluationContext createEvaluationContext(@Nullable Object result) {
			// org.springframework.cache.interceptor.CacheOperationExpressionEvaluator.createEvaluationContext() 方法
			return evaluator.createEvaluationContext(this.caches, this.metadata.method, this.args,
					this.target, this.metadata.targetClass, this.metadata.targetMethod, result, beanFactory);
		}

		protected Collection<? extends Cache> getCaches() {
			return this.caches;
		}

		protected Collection<String> getCacheNames() {
			return this.cacheNames;
		}

		private Collection<String> createCacheNames(Collection<? extends Cache> caches) {
			Collection<String> names = new ArrayList<>();
			for (Cache cache : caches) {
				names.add(cache.getName());
			}
			return names;
		}
	}


	private class CachePutRequest {
		// 持有 CacheOperationContext 以及对应的 key

		private final CacheOperationContext context;

		private final Object key;

		public CachePutRequest(CacheOperationContext context, Object key) {
			this.context = context;
			this.key = key;
		}

		public void apply(@Nullable Object result) {
			if (this.context.canPutToCache(result)) {
				for (Cache cache : this.context.getCaches()) {
					doPut(cache, this.key, result);
				}
			}
		}
	}


	private static final class CacheOperationCacheKey implements Comparable<CacheOperationCacheKey> {
		// CacheOperationCacheKey = CacheOperation + methodCacheKey
		// AnnotatedElementKey = Method + targetClass

		private final CacheOperation cacheOperation;

		private final AnnotatedElementKey methodCacheKey;

		private CacheOperationCacheKey(CacheOperation cacheOperation, Method method, Class<?> targetClass) {
			this.cacheOperation = cacheOperation;
			this.methodCacheKey = new AnnotatedElementKey(method, targetClass);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof CacheOperationCacheKey)) {
				return false;
			}
			CacheOperationCacheKey otherKey = (CacheOperationCacheKey) other;
			return (this.cacheOperation.equals(otherKey.cacheOperation) &&
					this.methodCacheKey.equals(otherKey.methodCacheKey));
		}

		@Override
		public int hashCode() {
			return (this.cacheOperation.hashCode() * 31 + this.methodCacheKey.hashCode());
		}

		@Override
		public String toString() {
			return this.cacheOperation + " on " + this.methodCacheKey;
		}

		@Override
		public int compareTo(CacheOperationCacheKey other) {
			int result = this.cacheOperation.getName().compareTo(other.cacheOperation.getName());
			if (result == 0) {
				result = this.methodCacheKey.compareTo(other.methodCacheKey);
			}
			return result;
		}
	}

	/**
	 * Internal holder class for recording that a cache method was invoked.
	 */
	private static class InvocationAwareResult {

		boolean invoked;

	}

}
