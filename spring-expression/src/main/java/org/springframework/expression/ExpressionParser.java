/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.expression;

/**
 * Parses expression strings into compiled expressions that can be evaluated.
 * Supports parsing templates as well as standard expression strings.
 *
 * @author Keith Donald
 * @author Andy Clement
 * @since 3.0
 */
public interface ExpressionParser {
	// 任何语言都需要有自己的语法，SpEL当然也不例外。所以我们应该能够想到，给一个字符串最终解析成一个值，这中间至少得经历：
	//字符串 -> 语法分析 -> 生成表达式对象 -> （添加执行上下文） -> 执行此表达式对象 -> 返回结果
	//
	//关于SpEL的几个概念：
	//
	//	1.表达式（“干什么”）：SpEL的核心，所以表达式语言都是围绕表达式进行的
	//	2.解析器（“谁来干”）：用于将字符串表达式解析为表达式对象
	//	3.上下文（“在哪干”）：表达式对象执行的环境，该环境可能定义变量、定义自定义函数、提供类型转换等等
	//	4.root根对象及活动上下文对象（“对谁干”）：root根对象是默认的活动上下文对象，活动上下文对象表示了当前表达式操作的对象

	// 将表达式字符串解析为可计算的已编译表达式。支持分析模板（Template）和标准表达式字符串。
	// 它是一个抽象，并没有要求具体的语法规则，Spring实现的语法规则是：SpEL语法。

	/**
	 * Parse the expression string and return an Expression object you can use for repeated evaluation.
	 * <p>Some examples:
	 * <pre class="code">
	 *     3 + 4
	 *     name.firstName
	 * </pre>
	 * @param expressionString the raw expression string to parse
	 * @return an evaluator for the parsed expression
	 * @throws ParseException an exception occurred during parsing
	 */
	Expression parseExpression(String expressionString) throws ParseException;
	// 解析表达式字符串并返回一个可用于重复评估的表达式对象。
	//一些例子：
	//	       3 + 4
	//	       name.firstName

	/**
	 * Parse the expression string and return an Expression object you can use for repeated evaluation.
	 * <p>Some examples:
	 * <pre class="code">
	 *     3 + 4
	 *     name.firstName
	 * </pre>
	 * @param expressionString the raw expression string to parse
	 * @param context a context for influencing this expression parsing routine (optional)
	 * @return an evaluator for the parsed expression
	 * @throws ParseException an exception occurred during parsing
	 */
	Expression parseExpression(String expressionString, ParserContext context) throws ParseException;
	// 解析表达式字符串并返回一个可用于重复评估的表达式对象。
	// 一些例子：
	//	       3 + 4
	//	       name.firstName
	//
	// 参数：
	//		expressionString – 要解析的原始表达式字符串
	//		context -- 用于影响此表达式解析例程的上下文（可选

}
