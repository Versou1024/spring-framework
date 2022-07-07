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

import java.util.Map;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Shared utility class used to evaluate and cache SpEL expressions that
 * are defined on {@link java.lang.reflect.AnnotatedElement}.
 *
 * @author Stephane Nicoll
 * @since 4.2
 * @see AnnotatedElementKey
 */
public abstract class CachedExpressionEvaluator {
	// 位于: org.springframework.context.expression
	// Cached Expression Evaluator = 具有缓存Expression的Evaluator

	// Spel表达式解析器
	private final SpelExpressionParser parser; 

	// 参数名发现器
	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	/**
	 * Create a new instance with the specified {@link SpelExpressionParser}.
	 */
	protected CachedExpressionEvaluator(SpelExpressionParser parser) {
		Assert.notNull(parser, "SpelExpressionParser must not be null");
		this.parser = parser;
	}

	/**
	 * Create a new instance with a default {@link SpelExpressionParser}.
	 */
	protected CachedExpressionEvaluator() {
		this(new SpelExpressionParser());
	}


	/**
	 * Return the {@link SpelExpressionParser} to use.
	 */
	protected SpelExpressionParser getParser() {
		return this.parser;
	}

	/**
	 * Return a shared parameter name discoverer which caches data internally.
	 * @since 4.3
	 */
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}


	/**
	 * Return the {@link Expression} for the specified SpEL value
	 * <p>Parse the expression if it hasn't been already.
	 * @param cache the cache to use
	 * @param elementKey the element on which the expression is defined
	 * @param expression the expression to parse
	 */
	protected Expression getExpression(Map<ExpressionKey, Expression> cache,
			AnnotatedElementKey elementKey, String expression) {
		// 1. 参数cache就是子类管理的缓存,传递进来
		ExpressionKey expressionKey = createKey(elementKey, expression);
		// 2. 检查是否有对应的ExpressionKey的映射 -> Expression
		Expression expr = cache.get(expressionKey);
		if (expr == null) {
			// 2.1 只有在缓存未命中的时候,才不得不使用Spel即SpelExpressionParser解析器去解析出Expression
			expr = getParser().parseExpression(expression);
			cache.put(expressionKey, expr);
		}
		// 3. 如果缓存命中,就不需要每次都去调用SpelExpressionParser去parse()解析这个expression [提供性能]
		return expr;
	}

	private ExpressionKey createKey(AnnotatedElementKey elementKey, String expression) {
		return new ExpressionKey(elementKey, expression);
	}


	/**
	 * An expression key.
	 */
	protected static class ExpressionKey implements Comparable<ExpressionKey> {

		private final AnnotatedElementKey element;

		private final String expression;

		protected ExpressionKey(AnnotatedElementKey element, String expression) {
			Assert.notNull(element, "AnnotatedElementKey must not be null");
			Assert.notNull(expression, "Expression must not be null");
			this.element = element;
			this.expression = expression;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ExpressionKey)) {
				return false;
			}
			ExpressionKey otherKey = (ExpressionKey) other;
			return (this.element.equals(otherKey.element) &&
					ObjectUtils.nullSafeEquals(this.expression, otherKey.expression));
		}

		@Override
		public int hashCode() {
			return this.element.hashCode() * 29 + this.expression.hashCode();
		}

		@Override
		public String toString() {
			return this.element + " with expression \"" + this.expression + "\"";
		}

		@Override
		public int compareTo(ExpressionKey other) {
			int result = this.element.toString().compareTo(other.element.toString());
			if (result == 0) {
				result = this.expression.compareTo(other.expression);
			}
			return result;
		}
	}

}
