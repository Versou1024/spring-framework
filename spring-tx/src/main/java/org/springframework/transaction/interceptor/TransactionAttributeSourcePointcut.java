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

package org.springframework.transaction.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionManager;
import org.springframework.util.ObjectUtils;

/**
 * Abstract class that implements a Pointcut that matches if the underlying
 * {@link TransactionAttributeSource} has an attribute for a given method.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 */
@SuppressWarnings("serial")
abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {
	// 扩展：StaticMethodMatcherPointcut -> 表名是一个PointCut，因此需要提供方法匹配、类过滤的方法；StaticMethodMatcher表示静态方法匹配，即isRuntime()返回false
	// 扩展点在于：PointCut的类、方法过滤都是通过TransactionAttributeSource借助判断的

	protected TransactionAttributeSourcePointcut() {
		// 重新设置ClassFilter，支持@Transactional在类上
		setClassFilter(new TransactionAttributeSourceClassFilter());
	}


	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		// 方法匹配 -> 检查方法上是否有@Transaction注解
		TransactionAttributeSource tas = getTransactionAttributeSource();
		return (tas == null || tas.getTransactionAttribute(method, targetClass) != null);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TransactionAttributeSourcePointcut)) {
			return false;
		}
		TransactionAttributeSourcePointcut otherPc = (TransactionAttributeSourcePointcut) other;
		return ObjectUtils.nullSafeEquals(getTransactionAttributeSource(), otherPc.getTransactionAttributeSource());
	}

	@Override
	public int hashCode() {
		return TransactionAttributeSourcePointcut.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + getTransactionAttributeSource();
	}


	/**
	 * Obtain the underlying TransactionAttributeSource (may be {@code null}).
	 * To be implemented by subclasses.
	 */
	@Nullable
	protected abstract TransactionAttributeSource getTransactionAttributeSource();
	// 由子类提供给我，告诉事务属性源 ~~~~ 借助 TransactionAttributeSource.isCandidateClass(clazz) 检查类是否为需要拦截的带有事务方法的类


	/**
	 * {@link ClassFilter} that delegates to {@link TransactionAttributeSource#isCandidateClass}
	 * for filtering classes whose methods are not worth searching to begin with.
	 */
	private class TransactionAttributeSourceClassFilter implements ClassFilter {
		// 方法的匹配 -- 静态匹配即可（因为事务无需要动态匹配这么细粒度~~~）

		@Override
		public boolean matches(Class<?> clazz) {
			// 1. 实现了如下三个接口的子类，就不需要被代理了,直接放行
			// TransactionalProxy它是SpringProxy的子类。  如果是被TransactionProxyFactoryBean生产出来的Bean，就会自动实现此接口，那么就不会被这里再次代理了
			// PlatformTransactionManager：spring抽象的事务管理器~~~
			// PersistenceExceptionTranslator对RuntimeException转换成DataAccessException的转换接口
			if (TransactionalProxy.class.isAssignableFrom(clazz) ||
					TransactionManager.class.isAssignableFrom(clazz) ||
					PersistenceExceptionTranslator.class.isAssignableFrom(clazz)) {
				return false;
			}
			
			// 2. 重要：拿到事务属性源~~~~~~调用其tas.isCandidateClass(clazz)帮助检查是否为有效的事务方法
			TransactionAttributeSource tas = getTransactionAttributeSource();
			
			// 3. 最终就是交给 SpringTransactionAttributeSource.isCandidateClass() 执行的哦
			// 源码就一句话: AnnotationUtils.isCandidateClass(targetClass, Transactional.class)
			// 也就是 确定给定的类targetClass是否是携带指定注解@Transactional的候选者（在类型、方法或字段级别）
			return (tas == null || tas.isCandidateClass(clazz));
		}
	}

}
