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
import java.util.Collection;

import org.springframework.cache.Cache;

/**
 * Class describing the root object used during the expression evaluation.
 *
 * @author Costin Leau
 * @author Sam Brannen
 * @since 3.1
 */
class CacheExpressionRootObject {
	// @Cache系列注解中使用的spel表达式中传入到ExpressionContext中设置的rootObject
	// 这个类决定了 #root的取值范围哦
	// 持有caches\method\args\target\targetClass
	// #root.method 
	// #root.args[0]
	// #root.target
	// #root.targetClass

	// 当前执行CacheOperation对应的Cache -> 因为@Cacheable注解的cacheNames是数组属性
	private final Collection<? extends Cache> caches;

	// 方法
	private final Method method;

	// 参数
	private final Object[] args;

	// 执行方法的目标对象
	private final Object target;

	// 目标对象的class
	private final Class<?> targetClass;


	public CacheExpressionRootObject(
			Collection<? extends Cache> caches, Method method, Object[] args, Object target, Class<?> targetClass) {

		this.method = method;
		this.target = target;
		this.targetClass = targetClass;
		this.args = args;
		this.caches = caches;
	}


	public Collection<? extends Cache> getCaches() {
		return this.caches;
	}

	public Method getMethod() {
		return this.method;
	}

	public String getMethodName() {
		return this.method.getName();
	}

	public Object[] getArgs() {
		return this.args;
	}

	public Object getTarget() {
		return this.target;
	}

	public Class<?> getTargetClass() {
		return this.targetClass;
	}

}
