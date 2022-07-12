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

package org.springframework.transaction.interceptor;

import java.lang.reflect.Method;

import org.springframework.lang.Nullable;

/**
 * Strategy interface used by {@link TransactionInterceptor} for metadata retrieval.
 *
 * <p>Implementations know how to source transaction attributes, whether from configuration,
 * metadata attributes at source level (such as Java 5 annotations), or anywhere else.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 15.04.2003
 * @see TransactionInterceptor#setTransactionAttributeSource
 * @see TransactionProxyFactoryBean#setTransactionAttributeSource
 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
 */
public interface TransactionAttributeSource {
	// 位于: org.springframework.transaction.interceptor
	
	// 命名:
	// TransactionAttributeSource = TransactionAttribute Source
	// XxxSource一般都是表示用来查找Xxx
	// TransactionAttributeSource就是用来查找TransactionAttribute
	
	// 定义:
	// getTransactionAttribute()  -- 从method上获取事务属性TransactionAttribute
	// isCandidateClass() -- 判断targetClass是否为合格TransactionAttribute的来源
	
	// 实现类:
	//		NamedTransactionAttributeSource			-- 通过提前注入注入Map<String, TransactionAttribute>,以方法名为key,以对应TransactionAttribute为value
	//		AnnotationTransactionAttributeSource	-- 100%的情况都是使用这个,切记切记哦
	//		MethodMapTransactionAttributeSource		-- 存入了Method->TransactionAttribute,Method->name的各种关系
	//		CompositeTransactionAttributeSource		-- 组合模式,不bb
	//		MatchAlwaysTransactionAttributeSource	-- 永远都能匹配到同一个TransactionAttribute
	
	// 参考:
	// 这tm和CacheOperationSource几乎一摸一样

	/**
	 * Determine whether the given class is a candidate for transaction attributes
	 * in the metadata format of this {@code TransactionAttributeSource}.
	 * <p>If this method returns {@code false}, the methods on the given class
	 * will not get traversed for {@link #getTransactionAttribute} introspection.
	 * Returning {@code false} is therefore an optimization for non-affected
	 * classes, whereas {@code true} simply means that the class needs to get
	 * fully introspected for each method on the given class individually.
	 * @param targetClass the class to introspect
	 * @return {@code false} if the class is known to have no transaction
	 * attributes at class or method level; {@code true} otherwise. The default
	 * implementation returns {@code true}, leading to regular introspection.
	 * @since 5.2
	 */
	default boolean isCandidateClass(Class<?> targetClass) { // 默认方法 - 直接返回true
		return true;
	}

	/**
	 * Return the transaction attribute for the given method,
	 * or {@code null} if the method is non-transactional.
	 * @param method the method to introspect
	 * @param targetClass the target class (may be {@code null},
	 * in which case the declaring class of the method must be used)
	 * @return the matching transaction attribute, or {@code null} if none found
	 */
	@Nullable
	TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass);
	/**
	 * 这里有很多人不明白了，为何都给了Method，为啥还要传入Class呢？难道Method还不知道它所属的类？？？
	 * 这里做如下解释：
	 *
	 * method – 目前正在进行的方法调用
	 * targetClass – 真正要调用的方法所在的类
	 * 这里是有细微差别的：
	 *
	 * method的所属类不一样是targetClass。比如：method是代理对象的方法，它的所属类是代理出来的类
	 * 但是：targetClass一定会有一个方法和method的方法签名一样
	 *
	 * -- 通常情况下，method的所属类会是targetClass的某个祖先类或者实现的某个接口。(动态代理)
	 */

}
