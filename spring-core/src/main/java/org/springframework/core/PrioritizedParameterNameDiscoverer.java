/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * {@link ParameterNameDiscoverer} implementation that tries several discoverer
 * delegates in succession. Those added first in the {@code addDiscoverer} method
 * have highest priority. If one returns {@code null}, the next will be tried.
 *
 * <p>The default behavior is to return {@code null} if no discoverer matches.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
public class PrioritizedParameterNameDiscoverer implements ParameterNameDiscoverer {
	/**
	 * 非具体实现者 -
	 * ParameterNameDiscoverer 仅仅提供方法和构造函数的形参名发现方法；
	 *
	 * 1、PrioritizedParameterNameDiscoverer 不但实现上述两个方法，作为默认模板实现
	 * 2、PrioritizedParameterNameDiscoverer 在原有的基础上，聚合多个 ParameterNameDiscoverer，并在模板中实现遍历参数名发现器，优先级从index0开始使用
	 */

	private final List<ParameterNameDiscoverer> parameterNameDiscoverers = new LinkedList<>(); // LinkedList类型，在使用增强for循环时，需要注意是能够保证顺序


	/**
	 * Add a further {@link ParameterNameDiscoverer} delegate to the list of
	 * discoverers that this {@code PrioritizedParameterNameDiscoverer} checks.
	 */
	public void addDiscoverer(ParameterNameDiscoverer pnd) {
		this.parameterNameDiscoverers.add(pnd);
	}


	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		// 模板实现 - 遍历形参名发现器，直到其中一个可以完成形参名的解析
		for (ParameterNameDiscoverer pnd : this.parameterNameDiscoverers) {
			String[] result = pnd.getParameterNames(method);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		// 模板实现 - 遍历形参名发现器，直到其中一个可以完成形参名的解析
		for (ParameterNameDiscoverer pnd : this.parameterNameDiscoverers) {
			String[] result = pnd.getParameterNames(ctor);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

}
