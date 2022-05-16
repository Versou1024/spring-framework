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

package org.springframework.aop.framework;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Factory interface for advisor chains.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface AdvisorChainFactory {

	/*
	 * advisor链的工厂
	 * 唯一实现类：就是DefaultAdvisorChainFactory
	 * 这个接口主要定义了从 Advised中获取 Advisor 并判断其是否与 对应的 Method 相匹配,
	 * 最终返回的是MethodInterceptor。
	 * (其中对 Advisor 转化成 MethodInterceptor 的工作都是交由 DefaultAdvisorAdapterRegistry 来完成, )
	 * 它的实现类为：DefaultAdvisorChainFactory，提供的唯一方法是：
	 */

	/**
	 * Determine a list of {@link org.aopalliance.intercept.MethodInterceptor} objects
	 * for the given advisor chain configuration.
	 * @param config the AOP configuration in the form of an Advised object
	 * @param method the proxied method
	 * @param targetClass the target class (may be {@code null} to indicate a proxy without
	 * target object, in which case the method's declaring class is the next best option)
	 * @return a List of MethodInterceptors (may also include InterceptorAndDynamicMethodMatchers)
	 */
	List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass);
	// 确定一个列表：其中都是MethodInterceptor对象，其持有advisor链

}
