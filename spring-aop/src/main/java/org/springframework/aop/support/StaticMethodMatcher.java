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

package org.springframework.aop.support;

import java.lang.reflect.Method;

import org.springframework.aop.MethodMatcher;

/**
 * Convenient abstract superclass for static method matchers, which don't care
 * about arguments at runtime.
 *
 * @author Rod Johnson
 */
public abstract class StaticMethodMatcher implements MethodMatcher {
	// StaticMethodMatcher 表示静态的MethodMatcher
	// 即默认将isRuntime返回false，将matches(Method method, Class<?> targetClass, Object... args)抛出异常，避免动态方法判断

	@Override
	public final boolean isRuntime() {
		return false;
		// 永远返回false表示只会去静态匹配
	}

	// 三参数matches抛出异常，使其不被调用
	@Override
	public final boolean matches(Method method, Class<?> targetClass, Object... args) {
		// should never be invoked because isRuntime() returns false
		throw new UnsupportedOperationException("Illegal MethodMatcher usage");
	}

}
