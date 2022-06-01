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

package org.springframework.context.expression;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * A method-based {@link org.springframework.expression.EvaluationContext} that
 * provides explicit support for method-based invocations.
 *
 * <p>Expose the actual method arguments using the following aliases:
 * <ol>
 * <li>pX where X is the index of the argument (p0 for the first argument)</li>
 * <li>aX where X is the index of the argument (a1 for the second argument)</li>
 * <li>the name of the parameter as discovered by a configurable {@link ParameterNameDiscoverer}</li>
 * </ol>
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 */
public class MethodBasedEvaluationContext extends StandardEvaluationContext {
	// 基于方法的org.springframework.expression.EvaluationContext为基于方法的调用提供显式支持。
	//	使用以下别名公开实际的方法参数：
	//		1. pX 其中 X 是参数的索引（第一个参数为 p0）
	//		2. aX 其中 X 是参数的索引（第二个参数为 a1）
	//		3. 由可配置的ParameterNameDiscoverer发现的参数名称
	// 因此其子类 CacheEvaluationContext 中 @Cacheable 标注的方法,可以使用使用
	// @Cacheable(key = "#p0")  @Cacheable(key = "#a0")  @Cacheable(key = "#argName")
	// 注意使用参数名,必须有对应的ParameterNameDiscoverer哦

	private final Method method; // 方法

	private final Object[] arguments; // 形参

	private final ParameterNameDiscoverer parameterNameDiscoverer; // 参数名发现器

	private boolean argumentsLoaded = false;


	public MethodBasedEvaluationContext(Object rootObject, Method method, Object[] arguments,
			ParameterNameDiscoverer parameterNameDiscoverer) {
		// 唯一构造函数 -- 要求必须传入 rootObject\method\arguments\parameterNameDiscoverer

		super(rootObject);
		this.method = method;
		this.arguments = arguments;
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}


	// 扩展查找执行name的变量名的方法 --
	// 因为这里扩展了通过 #px #ax #parameterName 三种变量的方式
	@Override
	@Nullable
	public Object lookupVariable(String name) {
		// 1. 允许超类的lookupVariable()先执行 -- 即先去EvaluationContext中设置的变量variables中查找是否有对应name的变量
		// 有就直接返回
		Object variable = super.lookupVariable(name);
		if (variable != null) {
			return variable;
		}
		// 2. argumentsLoaded 默认是false,然后懒加载method中所有的形参
		if (!this.argumentsLoaded) {
			lazyLoadArguments();
			this.argumentsLoaded = true;
			variable = super.lookupVariable(name);
		}
		return variable;
	}

	/**
	 * Load the param information only when needed.
	 */
	protected void lazyLoadArguments() {
		// Shortcut if no args need to be loaded
		// 1. 如果不需要加载 args 的快捷方式
		if (ObjectUtils.isEmpty(this.arguments)) {
			return;
		}

		// Expose indexed variables as well as parameter names (if discoverable)
		// 2. 使用 parameterNameDiscoverer 获取形参名
		String[] paramNames = this.parameterNameDiscoverer.getParameterNames(this.method);
		int paramCount = (paramNames != null ? paramNames.length : this.method.getParameterCount()); // 参数名
		int argsCount = this.arguments.length; // 参数值

		// 3. 开始调用父类的setVariable()加载进去
		for (int i = 0; i < paramCount; i++) {
			Object value = null;
			if (argsCount > paramCount && i == paramCount - 1) {
				// Expose remaining arguments as vararg array for last parameter
				value = Arrays.copyOfRange(this.arguments, i, argsCount);
			}
			else if (argsCount > i) {
				// Actual argument found - otherwise left as null
				value = this.arguments[i];
			}
			// ❗️❗️❗️ 将其作为形参值,作为变量设置到 EvaluationContext 中
			// 分别是 a序号\p序号\形参名 作为变量key,value作为形参值
			setVariable("a" + i, value);
			setVariable("p" + i, value);
			// 前提是有用parameterNameDiscoverer解析出paramNames
			if (paramNames != null && paramNames[i] != null) {
				setVariable(paramNames[i], value);
			}
		}
	}

}
