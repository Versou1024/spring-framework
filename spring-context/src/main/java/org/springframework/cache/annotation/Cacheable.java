/*
 * Copyright 2002-2016 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation indicating that the result of invoking a method (or all methods
 * in a class) can be cached.
 *
 * <p>Each time an advised method is invoked, caching behavior will be applied,
 * checking whether the method has been already invoked for the given arguments.
 * A sensible default simply uses the method parameters to compute the key, but
 * a SpEL expression can be provided via the {@link #key} attribute, or a custom
 * {@link org.springframework.cache.interceptor.KeyGenerator} implementation can
 * replace the default one (see {@link #keyGenerator}).
 *
 * <p>If no value is found in the cache for the computed key, the target method
 * will be invoked and the returned value stored in the associated cache. Note
 * that Java8's {@code Optional} return types are automatically handled and its
 * content is stored in the cache if present.
 *
 * <p>This annotation may be used as a <em>meta-annotation</em> to create custom
 * <em>composed annotations</em> with attribute overrides.
 *
 * @author Costin Leau
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 3.1
 * @see CacheConfig
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Cacheable {
	// @Cacheable pk @CachePut
	// @Cacheable在缓存命中时,就不会再去触发目标方法的执行
	// @CachePut不管缓存命中与否,都要去重新执行方法,将其返回值放入到缓存空间
	// @Cacheable和@CachePut混用,就需要注意上面的逻辑

	// 其次@Cacheable能够从Cache中去get,get不到,再去触发put
	// 而@CachePut只能够往Cache中put

	// 二者混用时:
	// a. @Cacheable缓存未命中 -- 执行方法拿到返回值 ✅
	// b. @Cacheable就算缓存命中,如果有@Cacheput -- 还是会执行方法,并且缓存值使用返回值,此时缓存命中的cacheHit是无效的 ✅
	// 因此: 结论是@Cacheable和@Cacheput混用时,最终拿到的值都是执行方法获取返回值 returnValue

	/**
	 * Alias for {@link #cacheNames}.
	 */
	@AliasFor("cacheNames")
	String[] value() default {};

	/**
	 * Names of the caches in which method invocation results are stored.
	 * <p>Names may be used to determine the target cache (or caches), matching
	 * the qualifier value or bean name of a specific bean definition.
	 * @since 4.2
	 * @see #value
	 * @see CacheConfig#cacheNames
	 */
	@AliasFor("value")
	String[] cacheNames() default {};
	// 存入 方法调用结果的缓存 的名称
	// 这个名称持有相关的配置哦

	/**
	 * Spring Expression Language (SpEL) expression for computing the key dynamically.
	 * <p>Default is {@code ""}, meaning all method parameters are considered as a key,
	 * unless a custom {@link #keyGenerator} has been configured.
	 * <p>The SpEL expression evaluates against a dedicated context that provides the
	 * following meta-data:
	 * <ul>
	 * <li>{@code #root.method}, {@code #root.target}, and {@code #root.caches} for
	 * references to the {@link java.lang.reflect.Method method}, target object, and
	 * affected cache(s) respectively.</li>
	 * <li>Shortcuts for the method name ({@code #root.methodName}) and target class
	 * ({@code #root.targetClass}) are also available.
	 * <li>Method arguments can be accessed by index. For instance the second argument
	 * can be accessed via {@code #root.args[1]}, {@code #p1} or {@code #a1}. Arguments
	 * can also be accessed by name if that information is available.</li>
	 * </ul>
	 */
	String key() default "";
	//  用于动态计算缓存key的 Spring 表达式语言 (SpEL) 表达式。
	//	默认为"" ，这意味着所有方法参数都被视为一个键，除非配置了自定义keyGenerator 。
	// 为什么可以使用 #root.method 还可以使用哪些变量
	// 请看 org.springframework.cache.interceptor.CacheOperationExpressionEvaluator.createEvaluationContext() 中如何创建的上下文

	/**
	 * The bean name of the custom {@link org.springframework.cache.interceptor.KeyGenerator}
	 * to use.
	 * <p>Mutually exclusive with the {@link #key} attribute.
	 * @see CacheConfig#keyGenerator
	 */
	String keyGenerator() default "";
	// 要使用实现了 org.springframework.cache.interceptor.KeyGenerator 的 bean 名称
	// 注意将KeyGenerator的实现类其注入到ioc容器中
	// 注意与key属性互斥

	/**
	 * The bean name of the custom {@link org.springframework.cache.CacheManager} to use to
	 * create a default {@link org.springframework.cache.interceptor.CacheResolver} if none
	 * is set already.
	 * <p>Mutually exclusive with the {@link #cacheResolver}  attribute.
	 * @see org.springframework.cache.interceptor.SimpleCacheResolver
	 * @see CacheConfig#cacheManager
	 */
	String cacheManager() default "";
	// 自定义org.springframework.cache.CacheManager的 bean 名称

	/**
	 * The bean name of the custom {@link org.springframework.cache.interceptor.CacheResolver}
	 * to use.
	 * @see CacheConfig#cacheResolver
	 */
	String cacheResolver() default "";
	// 要使用的自定义org.springframework.cache.interceptor.CacheResolver的 bean 名称。

	/**
	 * Spring Expression Language (SpEL) expression used for making the method
	 * caching conditional.
	 * <p>Default is {@code ""}, meaning the method result is always cached.
	 * <p>The SpEL expression evaluates against a dedicated context that provides the
	 * following meta-data:
	 * <ul>
	 * <li>{@code #root.method}, {@code #root.target}, and {@code #root.caches} for
	 * references to the {@link java.lang.reflect.Method method}, target object, and
	 * affected cache(s) respectively.</li>
	 * <li>Shortcuts for the method name ({@code #root.methodName}) and target class
	 * ({@code #root.targetClass}) are also available.
	 * <li>Method arguments can be accessed by index. For instance the second argument
	 * can be accessed via {@code #root.args[1]}, {@code #p1} or {@code #a1}. Arguments
	 * can also be accessed by name if that information is available.</li>
	 * </ul>
	 */
	String condition() default "";
	// condition 用于表示是否将此方法的结果缓存起来的判断条件
	//		默认为"" ，表示方法结果始终被缓存。
	//		SpEL 表达式根据提供以下元数据的专用上下文进行评估：
	//		#root.method 、 #root.target和#root.caches分别用于对method 、目标对象和受影响缓存的引用。
	//		方法名称 ( #root.methodName ) 和目标类 ( #root.targetClass ) 的快捷方式也可用。
	//		方法参数可以通过索引访问。例如，可以通过#root.args[1] 、 #p1或#a1访问第二个参数。如果该信息可用，也可以按名称访问参数。

	/**
	 * Spring Expression Language (SpEL) expression used to veto method caching.
	 * <p>Unlike {@link #condition}, this expression is evaluated after the method
	 * has been called and can therefore refer to the {@code result}.
	 * <p>Default is {@code ""}, meaning that caching is never vetoed.
	 * <p>The SpEL expression evaluates against a dedicated context that provides the
	 * following meta-data:
	 * <ul>
	 * <li>{@code #result} for a reference to the result of the method invocation. For
	 * supported wrappers such as {@code Optional}, {@code #result} refers to the actual
	 * object, not the wrapper</li>
	 * <li>{@code #root.method}, {@code #root.target}, and {@code #root.caches} for
	 * references to the {@link java.lang.reflect.Method method}, target object, and
	 * affected cache(s) respectively.</li>
	 * <li>Shortcuts for the method name ({@code #root.methodName}) and target class
	 * ({@code #root.targetClass}) are also available.
	 * <li>Method arguments can be accessed by index. For instance the second argument
	 * can be accessed via {@code #root.args[1]}, {@code #p1} or {@code #a1}. Arguments
	 * can also be accessed by name if that information is available.</li>
	 * </ul>
	 * @since 3.2
	 */
	String unless() default "";
	// 用于否决方法缓存的 Spring 表达式语言 (SpEL) 表达式。
	//	与condition不同，此表达式在方法被调用后计算，因此可以引用result 。
	//	默认为"" ，这意味着缓存永远不会被否决。
	//	SpEL 表达式根据提供以下元数据的专用上下文进行评估：
	//	#result 用于引用方法调用的结果。对于支持的包装器，例如Optional ， #result指的是实际对象，而不是包装器
	//	#root.method 、 #root.target和#root.caches分别用于对method 、目标对象和受影响缓存的引用。
	//	方法名称 ( #root.methodName ) 和目标类 ( #root.targetClass ) 的快捷方式也可用。
	//	方法参数可以通过索引访问。例如，可以通过
	//		#root.args[1] \ #p1 \ #a1 访问第二个参数。如果该信息可用，也可以按名称访问参数。

	/**
	 * Synchronize the invocation of the underlying method if several threads are
	 * attempting to load a value for the same key. The synchronization leads to
	 * a couple of limitations:
	 * <ol>
	 * <li>{@link #unless()} is not supported</li>
	 * <li>Only one cache may be specified</li>
	 * <li>No other cache-related operation can be combined</li>
	 * </ol>
	 * This is effectively a hint and the actual cache provider that you are
	 * using may not support it in a synchronized fashion. Check your provider
	 * documentation for more details on the actual semantics.
	 * @since 4.3
	 * @see org.springframework.cache.Cache#get(Object, Callable)
	 */
	boolean sync() default false;
	// 如果多个线程试图为同一个键加载一个值，则同步底层方法的调用。同步导致了一些限制：
	//		不支持unless()
	//		只能指定一个缓存
	//		没有其他缓存相关的操作可以组合
	// 这实际上是一个提示，您使用的实际缓存提供程序可能不以同步方式支持它。检查您的提供者文档以获取有关实际语义的更多详细信息。


	// @Cacheable(sync=true) 是无法与多个缓存操作联合 -- 比如 @CacheEvict\@CachePut等等
	// 人话解释：sync=true时候，不能还有其它的缓存操作 也就是说@Cacheable(sync=true)的时候只能单独使用这一个注解哦

	// 开启缓存方法同步后时, @Cacheable(sync=true) 也只能有一个,不能和其他 @Cacheable(sync=false|true) 联合
	// 人话解释：@Cacheable(sync=true)时，多个@Cacheable也是不允许的
	
	// @Cacheable(sync=true) 不支持同时使用 Unless 属性哦
	// 人话解释：sync=true时，unless属性是不支持的~~~并且是不能写的

}
