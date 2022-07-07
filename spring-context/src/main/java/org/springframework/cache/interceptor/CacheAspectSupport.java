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
public abstract class CacheAspectSupport extends AbstractCacheInvoker implements BeanFactoryAware, InitializingBean, SmartInitializingSingleton {
	// CacheAspectSupport = Cache Aspect Support 缓存切面支持
	// 提供一些对于缓存切面的支持接口

	protected final Log logger = LogFactory.getLog(getClass());

	private final Map<CacheOperationCacheKey, CacheOperationMetadata> metadataCache = new ConcurrentHashMap<>(1024);

	// 针对Cache注解的表达式#{}解析上下文 
	private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator();

	// 使用策略设计模式。
	// 		CacheOperationSource 用于确定缓存操作，
	// 		KeyGenerator		 将构建缓存映射的键，
	// 		CacheResolver		 将解析要使用的实际缓存。

	// spring注解环境一般使用 AnnotationCacheOperationSource
	@Nullable
	private CacheOperationSource cacheOperationSource; 

	// 默认: SimpleKeyGenerator
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
		// 调用处: ProxyCachingConfiguration 创建一个CacheInterceptor拦截器时,会调用该构造器
		// 其中形参中的四个配置是来自用户对CacheConfigurer实现类注入的哦 -> 即CacheConfigurer接口的cacheManage() cacheResolver() keyGenerator() errorHandler()
		
		// 用户的errorHandler为空时 -- 默认将使用 SimpleCacheErrorHandler
		this.errorHandler = new SingletonSupplier<>(errorHandler, SimpleCacheErrorHandler::new);
		
		// 用户的keyGenerator为空时 -- 默认将使用 SimpleKeyGenerator
		this.keyGenerator = new SingletonSupplier<>(keyGenerator, SimpleKeyGenerator::new);
		
		// ❗️❗️❗️
		// 用户的cacheResolver为空时 -- 默认将使用 SimpleCacheResolver --
		// 当CacheManger为空时 -> SimpleCacheResolver.of(null)
		// 当CacheManger非空时 -> SimpleCacheResolver.of(cacheManager)
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

		// ~~ 现在来说: 实际上在调用AbstractCachingConfiguration创建CacheInterceptor时,调用了当前类的configure()操作
		// 就一定保证啦getCacheResolver()是最少就有默认的SimpleCacheResolver
		if (getCacheResolver() == null) {
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

	protected Collection<? extends Cache> getCaches(CacheOperationInvocationContext<CacheOperation> context, CacheResolver cacheResolver) {

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

		// 1. 为每一个operation创建一个相应元数据CacheOperationMetadata
		// CacheOperationMetadata持有为这个CacheOperationKeyGenerator\CacheResolver -- 具体见下面的方法
		CacheOperationMetadata metadata = getCacheOperationMetadata(operation, method, targetClass);
		// 2. 转换为 CacheOperationContext
		// CacheOperationContext 中会去调用 CacheResolver.resolverCache() 解析这个operation对应需要的Cache集合哦
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
	protected CacheOperationMetadata getCacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass) {

		// 1. CacheOperationMetadata 元数据缓存是否命中
		CacheOperationCacheKey cacheKey = new CacheOperationCacheKey(operation, method, targetClass);
		CacheOperationMetadata metadata = this.metadataCache.get(cacheKey);
		if (metadata == null) {
			// 2. 缓存未命中 -- 创建 CacheOperationMetadata
			// ❗❗️❗️❗️❗️
			// ️注意各种注解的配置写入到CacheOperation,都是String值,没有做实际的Spel运算和从ioc容器获取
			//  在这个创建过程中去完成

			// 2.1 创建 KeyGenerator
			// ❗️❗️❗️ 简单描述 -- keyGenerator的决定过程 a > b > c > d 优先级上
			// a:方法上的@CachePut/@CacheEvict/@Cacheable有设置keyGenerator的beanName
			// b:类上的@CacheConfig有设置keyGenerator
			// c:用户通过CacheConfigurer配置keyGenerator
			// d:默认使用的SimpleKeyGenerator
			KeyGenerator operationKeyGenerator;
			if (StringUtils.hasText(operation.getKeyGenerator())) {
				// a和b步骤在装配CacheOperation时完成的
				operationKeyGenerator = getBean(operation.getKeyGenerator(), KeyGenerator.class);
			}
			else {
				// c和d步骤在当前类的configurer()方法中完成
				operationKeyGenerator = getKeyGenerator();
			}
			// 2.2 创建 CacheResolver
			// CacheResolver的决定逻辑是 -> 如下: 
			// note: 1. 如果有同等级有CacheManger和CacheResolver,那就使用CacheResolver; 2. 如果高优先级只有CacheManger,那么也需要通过new SimpleCacheResolver(cacheManager) 转为Resovler
			// a:方法上的@CachePut/@CacheEvict/@Cacheable有设置CacheResolver
			// b:方法上的@CachePut/@CacheEvict/@Cacheable有设置CacheManager,当然需要调用 SimpleCacheResolver(cacheManager)
			// c:当a和b都失败时，类上的@CacheConfig有设置CacheResolver和CacheManager，会优先考虑设置CacheResolver，如果没有就设置CacheManger
			// d:用户通过CacheConfigurer有配置CacheResolver，那就直接用这个CacheResolver
			// e:用户通过CacheConfigurer没有配置CacheResolver，但配置了CacheResolver，就是用SimpleCacheResolver.of(CacheManger)
			// f:用户没有通过CacheConfigurer配置CacheResolver和CacheManger，那就用SimpleCacheResolver.of(null)
			
			CacheResolver operationCacheResolver;
			if (StringUtils.hasText(operation.getCacheResolver())) {
				operationCacheResolver = getBean(operation.getCacheResolver(), CacheResolver.class);
			}
			else if (StringUtils.hasText(operation.getCacheManager())) {
				CacheManager cacheManager = getBean(operation.getCacheManager(), CacheManager.class);
				operationCacheResolver = new SimpleCacheResolver(cacheManager);
			}
			else {
				operationCacheResolver = getCacheResolver();
				Assert.state(operationCacheResolver != null, "No CacheResolver/CacheManager set");
			}
			// 3. 创建 CacheOperationMetadata
			metadata = new CacheOperationMetadata(operation, method, targetClass, operationKeyGenerator, operationCacheResolver);
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
		// 1. 检查是否已经初始化完毕 ~~ 100%
		if (this.initialized) {
			// 1.1 防止代理 -> 取出最终的target的class
			Class<?> targetClass = getTargetClass(target);
			// 1.2 取出CacheOperationSource[一般都是AnnotationCacheOperationSource],用来取出CacheOperation集合
			CacheOperationSource cacheOperationSource = getCacheOperationSource(); 
			if (cacheOperationSource != null) {
				// 1.3 ❗️❗️❗️
				// 核心 -- 从当前执行的方法method上获取CacheOperation缓存操作集合
				// 实际的解析工作委托给 -- SpringCacheAnnotationParser 处理啦 [逻辑比较简单]
				Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
				if (!CollectionUtils.isEmpty(operations)) {
					// 1.4 ❗️❗️❗️
					// 核心 -- 对当前执行方法method上每个缓存操作动作进行判断和执行哦
					// 当前期间会创建一个 CacheOperationContexts 操作上下文
					// CacheOperationContexts是非常重要的一个私有内部类
					// 注意它是复数哦~不是CacheOperationContext单数  所以它就像持有多个注解上下文一样  一个个执行吧
					// 所以我建议先看看此类的描述，再继续往下看~~~
					return execute(invoker, method, new CacheOperationContexts(operations, method, args, target, targetClass));
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
		// 构造完CacheOperationContexts -> 就要开始真正的执行操作啦
		
		// 1. 同步调用的特殊处理 -- 有同步,表明只有一个@Cacheable注解
		// 因此下面的cache只有一个,而且处理完就返回啦 -- 全都因为只有一个注解
		if (contexts.isSynchronized()) {
			// 2. 获取 CacheableOperation 操作的对应的上下文
			CacheOperationContext context = contexts.get(CacheableOperation.class).iterator().next();
			// 3.1 检查是否可以通过Condition属性
			if (isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
				// 3.1.1 有key属性,就是使用spel表达式解析出来
				// 没有key属性,有KeyGenerator就调用其来生成一个key
				Object key = generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
				// 3.1.2 获取当前CacheableOperation对应待执行的cache
				Cache cache = context.getCaches().iterator().next();
				try {
					// 3.1.3  下面这行代码的逻辑是: 
					// 	a: 检查cache中是否有指定key的映射,有的话,就直接返回							-- 缓存命中
					//  b: 没有指定key的映射,直接调用目标方法,获取结果,并且结果为Optional时进行unwrap	-- 缓存未命中
					//		b.a: 缓存未命中执行了目标方法之后会将unwrap解包出来的value存入到cache中	-- 加入缓存
					// 	d 返回值如果是null就包装为Optional类型返回,或目标方法的返回类型时Optional的
					
					// why -> 为什么里面unwrap解包之后这里又wrap包装起来?
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
				// 3.2.1 没有通过Condition,就不需要去Cache中查看是否有对应的key的映射
				// 直接调用目标方法返回值即可哦
				return invokeOperation(invoker);
			}
		}

		// ------
		// 注意: 如果有 @Cacheable(sync=true) [就只能有且只有一个注解,否则创建的过程就已经报错啦]
		// 而其余情况是,允 Cache系列注解合并使用的,例如 @Cacheable + @CacheEvict + @CachePut 都放在同一个方法上的哦


		// 1. 处理任何早期驱逐 -- @CacheEvict的beforeInvocation属性为true -- 简述如下:
		// 	a: 从contexts中获取@CacheEvict类型的CacheEvictOperation的操作上下文集合 - 方法上允许有多个@CacheEvict注解，每个CacheOperation对应一个CacheOperationContext
		//  b: 遍历CacheOperationContext，对于CacheEvictOperation中beforeInvocation为true即早期驱逐，且同时满足codition的spel表达式 -> 允许执行这个CacheEvictOperation操作
		//  c: 执行
		//      c.1: 若CacheEvictOperation的cacheWide为true，对应就是@CacheEvict的allEntries属性为true，表示clear整个Cache，否则表示invalidate无效某个key的映射
		//      c.2: 若CacheEvictOperation的beforeInvocation为true，对应就是@CacheEvict的beforeInvocation属性为true，表示早期驱除，即在方法执行之前清空或驱除缓存时
		//           就必须是立即清空或立即驱除，否则等会儿方法执行的时候，会从Cache中拿到没有立即删除的值，不符合语义
		processCacheEvicts(contexts.get(CacheEvictOperation.class), true, CacheOperationExpressionEvaluator.NO_RESULT);

		// 2. 如果有@Cacheable,就可以去看看缓存空间caches中是否有符合key的缓存项
		Cache.ValueWrapper cacheHit = findCachedItem(contexts.get(CacheableOperation.class));

		// 3. ❗️❗️
		// 当没有@Cachebale注解,或者即使上面有@Cacheable但缓存空间中都未命中,就不得不调用方法获取结果并加入到缓存中
		List<CachePutRequest> cachePutRequests = new LinkedList<>();
		if (cacheHit == null) {
			// 3.1 获取CacheableOperation所有的操作上下文集合,然后满足其Condition就创建为CachePutRequest
			// 注意 -- 仅仅收集@Cacheable的CachePutRequest,还不会立即去执行方法的
			collectPutRequests(contexts.get(CacheableOperation.class), CacheOperationExpressionEvaluator.NO_RESULT, cachePutRequests);
		}
		
		// @Cacheable pk @CachePut
		// @Cacheable在缓存命中时,就不回去触发方法的执行
		// @CachePut时不管缓存命中与否,都会执行方法,将其返回值放入到缓存空间
		// @Cacheable和@CachePut混用,就需要注意上面的逻辑
		
		Object cacheValue; // 缓存的值
		Object returnValue; // 返回的值

		// 4.1 缓存命中且contexts中不含有CachePutOperation的上下文,即不含有@CachePut注解在目标方法上 -- 不需要执行目标方法
		if (cacheHit != null && !hasCachePut(contexts)) {
			// 4.1.1 获取缓存命中的结果cacheValue,并将其包装后作为returnValue
			cacheValue = cacheHit.get();
			returnValue = wrapCacheValue(method, cacheValue);
		}
		// 4.2 @Cacheable的缓存没有命中且contexts中不含有CachePutOperation的上下文,即不含有@CachePut注解在目标方法上 -- 不需要执行目标方法
		else {
			// ❗️❗️❗️
			// a. 缓存未命中 -- 执行方法拿到返回值 ✅
			// b. 就算缓存命中,如果有@Cacheput -- 还是会执行方法,并且缓存值使用返回值,此时缓存命中的cacheHit是无效的 ✅
			// 因此: 结论是@Cacheable和@Cacheput混用时,最终拿到的值都是执行方法获取返回值 returnValue
			returnValue = invokeOperation(invoker); // -- 执行目标方法
			cacheValue = unwrapReturnValue(returnValue);
		}

		// 上面收集的是: @Cacheable缓存未命中后,通过Condition的spel判断,那就收集到CachePutRequest中
		// 5. 收集 @CachePut -> 相比于@Cacheable,不管缓存命中与否,只要通过condition的spel判断,都会收集到CachePutRequest中去
		collectPutRequests(contexts.get(CachePutOperation.class), cacheValue, cachePutRequests);

		// 6. 处理来自 @CachePut 或 @Cacheable 缓存未命中时将任何结果存入缓存空间的放置请求
		for (CachePutRequest cachePutRequest : cachePutRequests) {
			// 6.1 注意：此处unless啥的生效~~~~
			// 最终执行cache.put(key, result)方法
			cachePutRequest.apply(cacheValue);
		}

		// 7. 处理任何在方法执行完之后的后期驱逐
		processCacheEvicts(contexts.get(CacheEvictOperation.class), false, cacheValue);

		return returnValue;
	}

	@Nullable
	private Object handleSynchronizedGet(CacheOperationInvoker invoker, Object key, Cache cache) {
		// 简单描述:
		// 1. 检查cache中是否有指定key的映射,有的话,就直接返回							-- 缓存命中
		// 2. 没有指定key的映射,直接调用目标方法,获取结果,并且结果为Optional时进行unwrap	-- 缓存未命中
		//		2.1 缓存未命中执行了目标方法之后会将unwrap解包出来的value存入到cache中		-- 加入缓存
		// 最终都可以获取到返回值Object
		
		
		InvocationAwareResult invocationResult = new InvocationAwareResult();
		// 1. 调用 cache.get() 方法查询是否存在指定key的映射
		// a: 如果key对应的映射存在时,直接返回对应value
		// b: 如果key对应的映射不存在时,就触发ValueLoader#call()即下面这个lambda表达式,并且触发之后会将这个映射添加到cache中哦
		// 触发 invoker 即原始方法的执行,获取其结果
		Object result = cache.get(key, () -> {
			invocationResult.invoked = true;
			if (logger.isTraceEnabled()) {
				logger.trace("No cache entry for key '" + key + "' in cache " + cache.getName());
			}
			// 2. invokeOperation(invoker) -> 源码 : return invoker.invoke() -> 就是触发@Cache标注的方法开始执行,并获取返回结果
			// unwrapReturnValue -> 源码: return ObjectUtils.unwrapOptional(returnValue) 获取返回值是Optional时的真实值 -> 即返回类型为Optional时将unwrapper
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
		// 	a: 从contexts中获取@CacheEvict类型的CacheEvictOperation的操作上下文集合 - 方法上允许有多个@CacheEvict注解，每个CacheOperation对应一个CacheOperationContext
		//  b: 遍历CacheOperationContext，对于CacheEvictOperation中beforeInvocation为true即早期驱逐，且同时满足codition的spel表达式 -> 允许执行这个CacheEvictOperation操作
		//  c: 执行
		//      c.1: 若CacheEvictOperation的cacheWide为true，对应就是@CacheEvict的allEntries属性为true，表示clear整个Cache，否则表示invalidate无效某个key的映射
		//      c.2: 若CacheEvictOperation的beforeInvocation为true，对应就是@CacheEvict的beforeInvocation属性为true，表示早期驱除，即在方法执行之前清空或驱除缓存时
		//           就必须是立即清空或立即驱除，否则等会儿方法执行的时候，会从Cahche中拿到没有立即删除的值，不符合语义

		// 1. 遍历 CacheOperationContext -- 
		// 只要CacheEvictOperation的beforeInvocation=true表示早期驱逐,且满足Condition属性表示的条件
		// 就可以调用performCacheEvict() 处理驱逐
		for (CacheOperationContext context : contexts) {
			CacheEvictOperation operation = (CacheEvictOperation) context.metadata.operation;
			if (beforeInvocation == operation.isBeforeInvocation() && isConditionPassing(context, result)) {
				performCacheEvict(context, operation, result);
			}
		}
	}

	private void performCacheEvict(CacheOperationContext context, CacheEvictOperation operation, @Nullable Object result) {
		// 和 @CacheEvict 有关 -> 完成驱除操作

		// 1. 获取@CacheEvict解析出来的所有的Cache
		Object key = null;
		for (Cache cache : context.getCaches()) {
			// 1.1 cacheWide -- 对应的就是 @CacheEvict 的 allEntries 属性的true/false
			if (operation.isCacheWide()) {
				logInvalidating(context, operation, null); // 日志
				// 1.1.1  @CacheEvict的allEntries属性为true -> 表示清空Cache
				// 而 operation.isBeforeInvocation() 为true,就表示早期删除即在方法运行之前删除,
				// 那么就对应立即删除,否则后面方法执行会从Cache中找到哦
				doClear(cache, operation.isBeforeInvocation()); 
			}
			// 1.2 非全部删除 -> 就表示删除指定的key
			else {
				if (key == null) {
					key = generateKey(context, result);
				}
				logInvalidating(context, operation, key); // 日志
				// 1.2.1  @CacheEvict的allEntries属性为false -> 表示从Cache删除指定key的映射键值对
				// 而 operation.isBeforeInvocation() 为true,就表示早期删除即在方法运行之前删除,
				// 那么就对应立即删除,否则后面方法执行会从Cache中找到哦
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
		// 一个方法上可以使用多个@Cacheable注解
		
		// 1. 遍历 @Cacheable 的使用
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
		// 去Caches中查看指定key的value值
		// note: 比如一个@Cacheable的cacheNames就可以指定多个
		
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
		// 每次带有@CachePut\@Cacheable\@CacheEvict\@Caching的方法执行时,都会被CacheInterceptor拦截到
		// 然后分析出其中对应的CacheOperation集合,同时创建一个CacheOperationContexts来联系上下文
		// 所以: CacheOperationContexts 是每次带有缓存注解的方法执行时都会创建一个新的哦

		// key -> CacheOperation实现类的class
		// value -> 对应CacheOperation实现类的集合CacheOperationContext
		private final MultiValueMap<Class<? extends CacheOperation>, CacheOperationContext> contexts;

		// 多个线程执行时,是否需要同步 -- 取决于是否有@Cacheable(sync=true) -- 不过需要满足下面三点
		// 1.@Cacheable(sync=true) 是无法与多个缓存操作联合 -- 比如 @CacheEvict\@CachePut等等
		// 人话解释：sync=true时候，不能还有其它的缓存操作 也就是说@Cacheable(sync=true)的时候只能单独使用这一个注解哦
		// 2. 开启缓存方法同步后时, @Cacheable(sync=true) 也只能有一个,不能和其他 @Cacheable(sync=false|true) 联合
		// 人话解释：@Cacheable(sync=true)时，多个@Cacheable也是不允许的
		// 3. @Cacheable(sync=true) 不支持同时使用 Unless 属性哦
		// 人话解释：sync=true时，unless属性是不支持的~~~并且是不能写的
		private final boolean sync;

		public CacheOperationContexts(Collection<? extends CacheOperation> operations, Method method, Object[] args, Object target, Class<?> targetClass) {

			this.contexts = new LinkedMultiValueMap<>(operations.size());
			// 1.遍历 operations -> 将他们规划为实际所属的某个操作 [简单易理解哦]
			for (CacheOperation op : operations) {
				// ❗️❗️❗️ 注意: getOperationContext(op, method, args, target, targetClass) 这是一个复杂的过程哦
				this.contexts.add(op.getClass(), getOperationContext(op, method, args, target, targetClass));
			}
			// 2. 多个线程执行时,是否需要同步
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
			// 1. 拿到所有的CacheableOperation对应的List<CacheOperationContext> -> @Cacheable 
			// 因为只有@Cacheable有这个同步标志属性sync
			List<CacheOperationContext> cacheOperationContexts = this.contexts.get(CacheableOperation.class);
			// 2. cacheOperationContexts为空的
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
		// 内部列 -- 不需要get方法直接使用即可

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
			this.targetMethod = (!Proxy.isProxyClass(targetClass) ? AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
			this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
			this.keyGenerator = keyGenerator;
			// 指定了最最终使用的cacheResolver
			this.cacheResolver = cacheResolver;
		}
	}


	/**
	 * A {@link CacheOperationInvocationContext} context for a {@link CacheOperation}.
	 */
	protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {
		// 缓存操作的上下文

		// CacheOperation的元数据 -> 持有最终分析出来的keyGenerator和CacheResolver
		private final CacheOperationMetadata metadata;

		// 执行方法的参数
		private final Object[] args;

		// 执行方法的目标对象
		private final Object target;

		// 通过元数据CacheOperationMetadata持有的CacheResolver分析出最终缓存的地方 -- Cache
		private final Collection<? extends Cache> caches;

		// 从上面的caches中解析处所有的对应的CacheName
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
			// ❗️❗️❗️ 判断Condition条件是否有效哦
			
			// 1. 如果配置了并且还没被解析过，此处就解析condition条件~~~
			if (this.conditionPassing == null) {
				// 2. 获取原生的注解上的 Condition 值
				if (StringUtils.hasText(this.metadata.operation.getCondition())) {
					// 2.1 ❗️❗️要求先创建 EvaluationContext -- 利用 CacheOperationExpressionEvaluator.createEvaluationContext
					// 可以在 EvaluationContext 中设置根对象 CacheExpressionRootObject 其中包括 args\method\target\targetClass 等等
					// 注意: 每次方法的执行都需要重新一个EvaluationContext
					// 而Expression是可以缓存的额篇
					EvaluationContext evaluationContext = createEvaluationContext(result);
					// 2.2 调用 condition -- 并将前面生成的 evaluationContext 放入其中
					this.conditionPassing = evaluator.condition(this.metadata.operation.getCondition(), this.metadata.methodKey, evaluationContext);
				}
				// 3. 没有Condition属性值,意味着true
				else {
					this.conditionPassing = true;
				}
			}
			return this.conditionPassing;
		}

		// 解析 CacheableOperation 和 CachePutOperation 的unles的spels是否满足
		// 如果满足就允许将获取到的结果put到Cache空间去
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
			// 1. 注解中指定的缓存的key
			if (StringUtils.hasText(this.metadata.operation.getKey())) {
				// 1.1 
				// 每次都需要创建新的 EvaluationContext
				// 且调用 CacheOperationExpressionEvaluator.key() 方法去解析key属性的字符串
				// 将注解上原生的key做spel解析后返回
				EvaluationContext evaluationContext = createEvaluationContext(result);
				return evaluator.key(this.metadata.operation.getKey(), this.metadata.methodKey, evaluationContext);
			}
			// 2. 没有指定缓存的key,就是用keyGenerator来创建key
			return this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
		}

		private EvaluationContext createEvaluationContext(@Nullable Object result) {
			// 创建spel执行的上下文哦
			
			// ❗️
			// 传入 当前CacheOperation对应的Cache集合\当前执行的方法\传递进来的方法参数\执行方法的目标对象target\目标对象的class\目标方法\执行的结果\ioc
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
		// CachePutRequest = cache put request
		// 表示需要将key和对应的reulst存入即put到Cache空间去

		// CacheOperation对应的Context
		private final CacheOperationContext context;

		// CacheOperation对应的key
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
		// 记录调用缓存方法的内部持有者类。

		boolean invoked;

	}

}
