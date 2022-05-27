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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * Strategy interface for resolving a value through evaluating it
 * as an expression, if applicable.
 *
 * <p>A raw {@link org.springframework.beans.factory.BeanFactory} does not
 * contain a default implementation of this strategy. However,
 * {@link org.springframework.context.ApplicationContext} implementations
 * will provide expression support out of the box.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public interface BeanExpressionResolver {
	// 策略接口，用于通过将值作为表达式进行评估来解析值（如果适用）。它持有Bean工厂~
	// 它的唯一实现类StandardBeanExpressionResolver（当然我们是可以自己实现的，比如后面我们自定义@Value功能，
	// 通过继承StandardBeanExpressionResolver来扩展实现~毕竟Spring很暖心的给我们开了口）

	/**
	 * Evaluate the given value as an expression, if applicable;
	 * return the value as-is otherwise.
	 * @param value the value to check
	 * @param evalContext the evaluation context
	 * @return the resolved value (potentially the given value as-is)
	 * @throws BeansException if evaluation failed
	 */
	@Nullable
	Object evaluate(@Nullable String value, BeanExpressionContext evalContext) throws BeansException;

}
