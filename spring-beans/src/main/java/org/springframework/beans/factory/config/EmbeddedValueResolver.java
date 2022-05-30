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

import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * {@link StringValueResolver} adapter for resolving placeholders and
 * expressions against a {@link ConfigurableBeanFactory}.
 *
 * <p>Note that this adapter resolves expressions as well, in contrast
 * to the {@link ConfigurableBeanFactory#resolveEmbeddedValue} method.
 * The {@link BeanExpressionContext} used is for the plain bean factory,
 * with no scope specified for any contextual objects to access.
 *
 * @author Juergen Hoeller
 * @since 4.3
 * @see ConfigurableBeanFactory#resolveEmbeddedValue(String)
 * @see ConfigurableBeanFactory#getBeanExpressionResolver()
 * @see BeanExpressionContext
 */
public class EmbeddedValueResolver implements StringValueResolver {
	// StringValueResolver唯一public实现类为：EmbeddedValueResolver
	// 大部分 StringValueResolver 都是内部实现类 -- 且大多都是通过一个Lambda表达式完成的

	// EmbeddedValueResolver
	// 帮助ConfigurableBeanFactory处理placeholders占位符的。
	// ConfigurableBeanFactory#resolveEmbeddedValue处理占位符真正干活的间接的就是它~~

	// BeanExpressionContext 的重点是BeanExpression解析时,提供BeanFactory查找Bean的能力
	private final BeanExpressionContext exprContext;

	// BeanExpressionResolver 之前有非常详细的讲解，简直不要太熟悉~  它支持的是SpEL  可以说非常的强大
	// 并且它有BeanExpressionContext就能拿到BeanFactory工厂，就能使用它的`resolveEmbeddedValue`来处理占位符~~~~
	// 双重功能都有了~~~拥有了和@Value一样的能力，非常强大~~~
	@Nullable
	private final BeanExpressionResolver exprResolver;


	public EmbeddedValueResolver(ConfigurableBeanFactory beanFactory) {
		this.exprContext = new BeanExpressionContext(beanFactory, null);
		// Spring 标准环境注册的就是 StandardBeanExpressionResolver
		this.exprResolver = beanFactory.getBeanExpressionResolver();
	}


	@Override
	@Nullable
	public String resolveStringValue(String strVal) {
		// 1. 先使用Bean工厂处理占位符resolveEmbeddedValue -- 这里处理的是${}
		// 占位符填充
		String value = this.exprContext.getBeanFactory().resolveEmbeddedValue(strVal);
		// 2. 再使用el表达式参与计算 ~~~~ 处理的是 ${}
		// el表达式
		if (this.exprResolver != null && value != null) {
			Object evaluated = this.exprResolver.evaluate(value, this.exprContext);
			value = (evaluated != null ? evaluated.toString() : null);
		}
		return value;
	}

}
