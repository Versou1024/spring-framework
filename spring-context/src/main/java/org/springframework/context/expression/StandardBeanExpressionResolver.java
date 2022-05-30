/*
 * Copyright 2002-2020 the original author or authors.
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
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanExpressionException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Standard implementation of the
 * {@link org.springframework.beans.factory.config.BeanExpressionResolver}
 * interface, parsing and evaluating Spring EL using Spring's expression module.
 *
 * <p>All beans in the containing {@code BeanFactory} are made available as
 * predefined variables with their common bean name, including standard context
 * beans such as "environment", "systemProperties" and "systemEnvironment".
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see BeanExpressionContext#getBeanFactory()
 * @see org.springframework.expression.ExpressionParser
 * @see org.springframework.expression.spel.standard.SpelExpressionParser
 * @see org.springframework.expression.spel.support.StandardEvaluationContext
 */
public class StandardBeanExpressionResolver implements BeanExpressionResolver {
	// StandardBeanExpressionResolver
	// 语言解析器的标准实现，支持解析SpEL语言。


	// 因为SpEL是支持自定义前缀、后缀的   此处保持了和SpEL默认值的统一
	// 它的属性值事public的   so你可以自定义~
	/** Default expression prefix: "#{". */
	public static final String DEFAULT_EXPRESSION_PREFIX = "#{";

	/** Default expression suffix: "}". */
	public static final String DEFAULT_EXPRESSION_SUFFIX = "}";


	private String expressionPrefix = DEFAULT_EXPRESSION_PREFIX;

	private String expressionSuffix = DEFAULT_EXPRESSION_SUFFIX;

	// 它的最终值是SpelExpressionParser
	private ExpressionParser expressionParser;

	// 每个表达式都对应一个Expression,这样可以不用重复解析了~~~
	private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(256);

	// 每个BeanExpressionContext都对应着一个取值上下文~~~
	private final Map<BeanExpressionContext, StandardEvaluationContext> evaluationCache = new ConcurrentHashMap<>(8);

	// 匿名内部类   解析上下文。  和TemplateParserContext的实现一样。个人觉得直接使用它更优雅
	// 和ParserContext.TEMPLATE_EXPRESSION 这个常量也一毛一样
	private final ParserContext beanExpressionParserContext = new ParserContext() {
		@Override
		public boolean isTemplate() {
			return true;
		}
		@Override
		public String getExpressionPrefix() {
			return expressionPrefix;
		}
		@Override
		public String getExpressionSuffix() {
			return expressionSuffix;
		}
	};


	/**
	 * Create a new {@code StandardBeanExpressionResolver} with default settings.
	 */
	public StandardBeanExpressionResolver() {
		// 空构造函数：默认就是使用的SpelExpressionParser  下面你也可以自己set你自己的实现~
		this.expressionParser = new SpelExpressionParser();
	}

	/**
	 * Create a new {@code StandardBeanExpressionResolver} with the given bean class loader,
	 * using it as the basis for expression compilation.
	 * @param beanClassLoader the factory's bean class loader
	 */
	public StandardBeanExpressionResolver(@Nullable ClassLoader beanClassLoader) {
		// 解析代码相对来说还是比较简单的，毕竟复杂的解析逻辑都是SpEL里边~  这里只是使用一下而已~
		// ❗️❗️❗️
		// 这里会被 AbstractApplicationContext#prepareBeanFactory() 方法所调用
		this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(null, beanClassLoader));
	}


	/**
	 * Set the prefix that an expression string starts with.
	 * The default is "#{".
	 * @see #DEFAULT_EXPRESSION_PREFIX
	 */
	public void setExpressionPrefix(String expressionPrefix) {
		Assert.hasText(expressionPrefix, "Expression prefix must not be empty");
		this.expressionPrefix = expressionPrefix;
	}

	/**
	 * Set the suffix that an expression string ends with.
	 * The default is "}".
	 * @see #DEFAULT_EXPRESSION_SUFFIX
	 */
	public void setExpressionSuffix(String expressionSuffix) {
		Assert.hasText(expressionSuffix, "Expression suffix must not be empty");
		this.expressionSuffix = expressionSuffix;
	}

	/**
	 * Specify the EL parser to use for expression parsing.
	 * <p>Default is a {@link org.springframework.expression.spel.standard.SpelExpressionParser},
	 * compatible with standard Unified EL style expression syntax.
	 */
	public void setExpressionParser(ExpressionParser expressionParser) {
		Assert.notNull(expressionParser, "ExpressionParser must not be null");
		this.expressionParser = expressionParser;
	}


	@Override
	@Nullable
	public Object evaluate(@Nullable String value, BeanExpressionContext evalContext) throws BeansException {
		if (!StringUtils.hasLength(value)) {
			return value;
		}
		try {
			Expression expr = this.expressionCache.get(value);
			if (expr == null) {
				expr = this.expressionParser.parseExpression(value, this.beanExpressionParserContext);
				this.expressionCache.put(value, expr);
			}
			// 1. 构建getValue计算时的执行上下文~~~
			// 做种解析BeanName的ast为；org.springframework.expression.spel.ast.PropertyOrFieldReference
			StandardEvaluationContext sec = this.evaluationCache.get(evalContext);
			if (sec == null) {
				// 2. 注意这里: 设置了RootObject哦,因此根对象就是 BeanExpressionContext
				sec = new StandardEvaluationContext(evalContext);
				// 3. 此处新增了4个，加上一个默认的   所以一共就有5个属性访问器了
				// 这样我们的SpEL就能访问BeanFactory、Map、Environment等组件中的属性或方法啦了~
				// BeanExpressionContextAccessor 表示调用bean的方法~~~~(比如我们此处就是使用的它)  最终执行者为;BeanExpressionContext   它持有BeanFactory的引用嘛~
				// 如果是单村的Bean注入，最终使用的也是BeanExpressionContextAccessor 目前没有找到BeanFactoryAccessor的用于之地~~~
				// addPropertyAccessor只是：addBeforeDefault 所以只是把default的放在了最后，我们手动add的还是保持着顺序的~
				// 注意：这些属性访问器是有先后顺序的，具体看下面~~~
				sec.addPropertyAccessor(new BeanExpressionContextAccessor());
				sec.addPropertyAccessor(new BeanFactoryAccessor());
				sec.addPropertyAccessor(new MapAccessor());
				sec.addPropertyAccessor(new EnvironmentAccessor());
				// 4. setBeanResolver不是接口方法，仅仅辅助StandardEvaluationContext 去获取Bean
				sec.setBeanResolver(new BeanFactoryResolver(evalContext.getBeanFactory()));
				sec.setTypeLocator(new StandardTypeLocator(evalContext.getBeanFactory().getBeanClassLoader()));
				// 5. 若conversionService不为null，就使用工厂的。否则就使用SpEL里默认的DefaultConverterService那个
				// 最后包装成TypeConverter给set进去~~~
				ConversionService conversionService = evalContext.getBeanFactory().getConversionService();
				if (conversionService != null) {
					// 6. 如上，整个@Value的解析过程至此就全部完成了。可能有小伙伴会问：怎么不见Resource这种注入呢？其实，从上面不难看出，
					// 这个是ConversionService去做的事，它能够把一个字符串转换成Resource对象，仅此而已
					sec.setTypeConverter(new StandardTypeConverter(conversionService));
				}
				// 7. 这个很有意思，是一个protected的空方法，因此我们发现若我们自己要自定义BeanExpressionResolver，完全可以继承自StandardBeanExpressionResolver
				// 因为我们绝大多数情况下，只需要提供更多的计算环境即可~~~~~
				customizeEvaluationContext(sec);
				this.evaluationCache.put(evalContext, sec);
			}
			return expr.getValue(sec);
		}
		catch (Throwable ex) {
			throw new BeanExpressionException("Expression parsing failed", ex);
		}
	}

	/**
	 * Template method for customizing the expression evaluation context.
	 * <p>The default implementation is empty.
	 */
	protected void customizeEvaluationContext(StandardEvaluationContext evalContext) {
		//Spring留给我们扩展的SPI
	}

}
