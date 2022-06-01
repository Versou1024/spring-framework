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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cache.Cache;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.lang.Nullable;

/**
 * Utility class handling the SpEL expression parsing.
 * Meant to be used as a reusable, thread-safe component.
 *
 * <p>Performs internal caching for performance reasons
 * using {@link AnnotatedElementKey}.
 *
 * @author Costin Leau
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
class CacheOperationExpressionEvaluator extends CachedExpressionEvaluator {
	// CachedExpressionEvaluator 提供 Spel 表达式能力 + 从缓存Map<ExpressionKey, Expression>获取Expression
	// CacheOperationExpressionEvaluator 则是封装为对应 condition\unless\key 三个对应属性的方法

	// 注意这两个属性是public的  在CacheAspectSupport里都有使用~~~

	/**
	 * Indicate that there is no result variable.
	 */
	public static final Object NO_RESULT = new Object();
	// 表示没有结果变量。

	/**
	 * Indicate that the result variable cannot be used at all.
	 */
	public static final Object RESULT_UNAVAILABLE = new Object();
	// 表示结果变量根本不能使用

	/**
	 * The name of the variable holding the result object.
	 */
	public static final String RESULT_VARIABLE = "result";
	// 保存结果对象的变量的名称

	// 比如 @Cacheable 中就有以下三个属性值

	// spel 的 key
	private final Map<ExpressionKey, Expression> keyCache = new ConcurrentHashMap<>(64);

	// spel 的 condition
	private final Map<ExpressionKey, Expression> conditionCache = new ConcurrentHashMap<>(64);

	// spel 的 unless
	private final Map<ExpressionKey, Expression> unlessCache = new ConcurrentHashMap<>(64);


	/**
	 * Create an {@link EvaluationContext}.
	 * @param caches the current caches
	 * @param method the method
	 * @param args the method arguments
	 * @param target the target object
	 * @param targetClass the target class
	 * @param result the return value (can be {@code null}) or
	 * {@link #NO_RESULT} if there is no return at this time
	 * @return the evaluation context
	 */
	public EvaluationContext createEvaluationContext(Collection<? extends Cache> caches,
			Method method, Object[] args, Object target, Class<?> targetClass, Method targetMethod,
			@Nullable Object result, @Nullable BeanFactory beanFactory) {
		// 这个方法是创建执行上下文。能给解释：为何可以使用#result这个key的原因
		// 此方法的入参确实不少：8个

		// 创建 EvaluationContext -- 这里就是为什么#target\#result生效的原因哦

		// 1. 创建一个rootObject
		// root对象，此对象的属性决定了你的#root能够取值的范围，具体下面有贴出此类~
		CacheExpressionRootObject rootObject = new CacheExpressionRootObject(
				caches, method, args, target, targetClass);
		// 2. 创建SpringCache的EvaluationContext
		CacheEvaluationContext evaluationContext = new CacheEvaluationContext(
				rootObject, targetMethod, args, getParameterNameDiscoverer());
		// 3. result 是 RESULT_UNAVAILABLE,加入到不可以使用的变量集合中
		if (result == RESULT_UNAVAILABLE) {
			evaluationContext.addUnavailableVariable(RESULT_VARIABLE);
		}
		// 4. 无结果,设置为变量name等于result
		else if (result != NO_RESULT) {
			// 这一句话就是为何我们可以使用#result的核心原因~
			evaluationContext.setVariable(RESULT_VARIABLE, result);
		}
		// 5. 允许设置BeanFactory
		// 从这里可知，缓存注解里也是可以使用容器内的Bean的~
		if (beanFactory != null) {
			evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
		}
		return evaluationContext;
	}

	// 都有缓存效果哦，因为都继承自抽象父类CachedExpressionEvaluator嘛
	// 抽象父类@since 4.2才出来，就是给解析过程提供了缓存的效果~~~~（注意此缓存非彼缓存）


	// 解析key的SpEL表达式
	@Nullable
	public Object key(String keyExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
		// 最终借用 Spel 的能力解析 key 的值
		// 是超类getExpression所提供的哦
		return getExpression(this.keyCache, methodKey, keyExpression).getValue(evalContext);
	}

	// condition和unless的解析结果若不是bool类型，统一按照false处理~~~~

	public boolean condition(String conditionExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
		// 最终借用 Spel 的能力解析 condition 的值
		return (Boolean.TRUE.equals(getExpression(this.conditionCache, methodKey, conditionExpression).getValue(
				evalContext, Boolean.class)));
	}

	public boolean unless(String unlessExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
		// 最终借用 Spel 的能力解析 unless 的值
		return (Boolean.TRUE.equals(getExpression(this.unlessCache, methodKey, unlessExpression).getValue(
				evalContext, Boolean.class)));
	}

	/**
	 * Clear all caches.
	 */
	void clear() {
		this.keyCache.clear();
		this.conditionCache.clear();
		this.unlessCache.clear();
	}

}
