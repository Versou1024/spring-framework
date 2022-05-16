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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	// 初始size为3，从构造函数可见
	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 */
	public DefaultAdvisorAdapterRegistry() {
		// 初始化，自动注册三个Advisor的适配器 -- MethodBeforeAdviceAdapter、AfterReturningAdviceAdapter、ThrowsAdviceAdapter
		// 分别是针对：MethodBeforeAdvice、 AfterReturningAdvice、 ThrowsAdvice 三个的适配
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}


	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		if (adviceObject instanceof Advisor) {
			// Advisor无须包装
			return (Advisor) adviceObject;
		}
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		Advice advice = (Advice) adviceObject;
		// 如果是MethodInterceptor类型，就根本不用适配器。DefaultPointcutAdvisor是天生处理这种有连接点得通知器的
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			// 无须包装，pointCut适配为POINT_CUT.TRUE
			return new DefaultPointcutAdvisor(advice);
		}
		// 这一步很显然了，就是校验看看这个advice是否是我们支持的这些类型（系统默认给出3中，但是我们也可以自己往里添加注册的）
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				// 如果是支持的，也是被包装成了一个通用类型的DefaultPointcutAdvisor
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}

	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		/*
		 * 1、将 Advice/MethodInterceptor 包裹成 DefaultPointcutAdvisor
		 * 2、通过上面的三个适配类将 Advisor 中的 Advice 适配成对应的MethodInterceptor
		 * (PS: 在代理对象执行时, 执行的都是MethodInterceptor, 当然在进行配置时既可以配置 Advice, MethodInterceptor, Advisor均可)
		 */

		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		// 获取Advice
		Advice advice = advisor.getAdvice();
		// 如果还是advice本身就是MethodInterceptor，就可以添加到interceptors上
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			// 检查默认的三个AdvisorAdapter有哪些是支持的，支持的话，就加入进去
			if (adapter.supportsAdvice(advice)) {
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		// interceptors 如果为空，就直接报错
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		// 转换过去即可
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
