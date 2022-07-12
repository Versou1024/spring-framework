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

package org.springframework.transaction.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Abstract implementation of {@link TransactionAttributeSource} that caches
 * attributes for methods and implements a fallback policy: 1. specific target
 * method; 2. target class; 3. declaring method; 4. declaring class/interface.
 *
 * <p>Defaults to using the target class's transaction attribute if none is
 * associated with the target method. Any transaction attribute associated with
 * the target method completely overrides a class transaction attribute.
 * If none found on the target class, the interface that the invoked method
 * has been called through (in case of a JDK proxy) will be checked.
 *
 * <p>This implementation caches attributes by method after they are first used.
 * If it is ever desirable to allow dynamic changing of transaction attributes
 * (which is very unlikely), caching could be made configurable. Caching is
 * desirable because of the cost of evaluating rollback rules.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public abstract class AbstractFallbackTransactionAttributeSource implements TransactionAttributeSource {
	// 命名:
	// AbstractFallbackTransactionAttributeSource = Abstract Fallback TransactionAttribute Source
	
	// 含义:
	// 抽象的回调的TransactionAttribute的源头 -> 
	

	// 用于标记方法没有事务注解属性 -> 主要是用在缓存中,避免null值,而是使用一个标记对象NULL_TRANSACTION_ATTRIBUTE
	// 比如一个类A属于事务代理类，拥有3个方法，其中有个方法1被@Transactional修饰，其余两个方法就属于这里没有事务注解属性的说法
	private static final TransactionAttribute NULL_TRANSACTION_ATTRIBUTE = new DefaultTransactionAttribute() {
		@Override
		public String toString() {
			return "null";
		}
	};


	/**
	 * Logger available to subclasses.
	 * <p>As this base class is not marked Serializable, the logger will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	protected final Log logger = LogFactory.getLog(getClass());


	// 方法上的事务注解属性缓存，key使用目标类上的方法，使用类型MethodClassKey来表示
	// 这个Map会比较大，因为与事务相关的方法都会被Advisor匹配拦截下来，处理过一次后缓存下来
	// 因为会有很多，所以我们才需要一个NULL_TRANSACTION_ATTRIBUTE常量来提高查找的效率~~~
	private final Map<Object, TransactionAttribute> attributeCache = new ConcurrentHashMap<>(1024);



	/**
	 * Determine the transaction attribute for this method invocation.
	 * <p>Defaults to the class's transaction attribute if no method attribute is found.
	 * @param method the method for the current invocation (never {@code null})
	 * @param targetClass the target class for this invocation (may be {@code null})
	 * @return a TransactionAttribute for this method, or {@code null} if the method
	 * is not transactional
	 */
	@Override
	@Nullable
	public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// 获取指定方法上的注解事务属性   如果方法上没有注解事务属性，则使用目标方法所属类上的注解事务属性

		// 1. Object下的方法不应该使用事务
		if (method.getDeclaringClass() == Object.class) {
			return null;
		}

		// 2. cache key
		Object cacheKey = getCacheKey(method, targetClass);
		TransactionAttribute cached = this.attributeCache.get(cacheKey);
		// 2.1 缓存命中
		if (cached != null) {
			// 2.1.1 执行的目标方法已经被解析过且发现没有事务注解属性,将存入NULL_TRANSACTION_ATTRIBUTE
			if (cached == NULL_TRANSACTION_ATTRIBUTE) {
				return null;
			}
			// 2.1.2 执行的目标方法有事务注解属性
			else {
				return cached;
			}
		}
		// 2.2 缓存未命中
		else {
			// 2.2.1 通过method\targetClass计算TransactionAttribute
			TransactionAttribute txAttr = computeTransactionAttribute(method, targetClass); // 核心处理 --- 计算事务属性
			// 2.2.2 如果目标方法上并没有使用注解事务属性，也缓存该信息，只不过使用的值是一个特殊值NULL_TRANSACTION_ATTRIBUTE
			if (txAttr == null) {
				this.attributeCache.put(cacheKey, NULL_TRANSACTION_ATTRIBUTE);
			}
			// 2.2.3 获取method的id值 -> 一般就是当前method的全限定类名
			else {
				String methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
				if (txAttr instanceof DefaultTransactionAttribute) {
					// 
					((DefaultTransactionAttribute) txAttr).setDescriptor(methodIdentification);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Adding transactional method '" + methodIdentification + "' with attribute: " + txAttr);
				}
				this.attributeCache.put(cacheKey, txAttr);
			}
			return txAttr;
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

	/**
	 * Same signature as {@link #getTransactionAttribute}, but doesn't cache the result.
	 * {@link #getTransactionAttribute} is effectively a caching decorator for this method.
	 * <p>As of 4.1.8, this method can be overridden.
	 * @since 4.1.8
	 * @see #getTransactionAttribute
	 */
	@Nullable
	protected TransactionAttribute computeTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// 从method\targetClass上计算TransactionAttribute

		// 1. 默认情况：不允许非公有方法被事务增强
		// 如果事务注解属性分析仅仅针对public方法，而当前方法不是public，则直接返回null
		// 如果是private，AOP是能切入，代理对象也会生成的  但就是事务不回生效的~~~~
		if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		// 该方法可能在接口上，但我们需要来自目标类的属性。如果目标类为空，则方法将保持不变。
		// 上面说了，因为Method并不一样属于目标类。所以这个方法就是获取targetClass上的那个和method对应的方法  也就是最终要执行的方法
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

		// 2.1 首先尝试的是目标类中的方法。
		// 第一步：去找直接标记在方法上的事务属性~~~ 如果方法上有就直接返回（不用再看类上的了）
		// findTransactionAttribute这个方法其实就是子类去实现的
		TransactionAttribute txAttr = findTransactionAttribute(specificMethod);
		if (txAttr != null) {
			return txAttr;
		}

		// 2.2 尝试检查事务注解属性是否标记在目标方法 specificMethod（注意此处用不是Method） 所属类上
		txAttr = findTransactionAttribute(specificMethod.getDeclaringClass());
		if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
			return txAttr;
		}

		// 程序走到这里说明目标方法specificMethod，也就是实现类上的目标方法上没有标记事务注解属性（否则直接返回了嘛）

		// 如果 specificMethod 和 method 引用不同，则说明 specificMethod 是具体实现类的方法；method 是实现类所实现接口的方法
		// 因此再次尝试从 method 上获取事务注解属性
		// 这也就是为何我们的@Transaction标注在接口上或者接口的方法上都是好使的原因~~~~~~~
		if (specificMethod != method) {
			// Fallback is to look at the original method.
			txAttr = findTransactionAttribute(method);
			if (txAttr != null) {
				return txAttr;
			}
			// Last fallback is the class of the original method.
			txAttr = findTransactionAttribute(method.getDeclaringClass());
			if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
				return txAttr;
			}
		}

		return null;
	}

	// 子类 AnnotationTransactionAttributeSource 需要实现这两个注解 -> 分别是从类上\方法上获取TransactionAttribute

	/**
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given class, if any.
	 * @param clazz the class to retrieve the attribute for
	 * @return all transaction attribute associated with this class, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Class<?> clazz);

	/**
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given method, if any.
	 * @param method the method to retrieve the attribute for
	 * @return all transaction attribute associated with this method, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Method method);

	/**
	 * Should only public methods be allowed to have transactional semantics?
	 * <p>The default implementation returns {@code false}.
	 */
	protected boolean allowPublicMethodsOnly() {
		// 可议看到默认值是false  表示private的也是ok的
		// 但是`AnnotationTransactionAttributeSource`复写了它  可以由开发者指定（默认是true了）
		return false;
	}

}
