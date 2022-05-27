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

package org.springframework.expression;

/**
 * Input provided to an expression parser that can influence an expression
 * parsing/compilation routine.
 *
 * @author Keith Donald
 * @author Andy Clement
 * @since 3.0
 */
public interface ParserContext {
	// 提供给可以影响 表达式解析/编译 的表达式解析器的输入。

	/**
	 * Whether or not the expression being parsed is a template. A template expression
	 * consists of literal text that can be mixed with evaluatable blocks. Some examples:
	 * <pre class="code">
	 * 	   Some literal text
	 *     Hello #{name.firstName}!
	 *     #{3 + 4}
	 * </pre>
	 * @return true if the expression is a template, false otherwise
	 */
	boolean isTemplate();
	// 被解析的表达式是否是模板。模板表达式由可以与可评估块混合的文字文本组成。一些例子：
	//	   	   Some literal text
	//	       Hello #{name.firstName}!
	//	       #{3 + 4}
	//
	//回报：
	//如果表达式是模板，则为 true，否则为 false

	/**
	 * For template expressions, returns the prefix that identifies the start of an
	 * expression block within a string. For example: "${"
	 * @return the prefix that identifies the start of an expression
	 */
	String getExpressionPrefix();
	// 对于模板表达式，返回标识字符串中表达式块开始的前缀。例如：“${”

	/**
	 * For template expressions, return the prefix that identifies the end of an
	 * expression block within a string. For example: "}"
	 * @return the suffix that identifies the end of an expression
	 */
	String getExpressionSuffix();
	// 对于模板表达式，返回标识字符串中表达式块结尾的前缀。例如： ”}”


	/**
	 * The default ParserContext implementation that enables template expression
	 * parsing mode. The expression prefix is "#{" and the expression suffix is "}".
	 * @see #isTemplate()
	 */
	ParserContext TEMPLATE_EXPRESSION = new ParserContext() {
		// 启用模板表达式解析模式的默认 ParserContext 实现。表达式前缀是“#{”，表达式后缀是“}”。

		@Override
		public boolean isTemplate() {
			return true;
		}

		@Override
		public String getExpressionPrefix() {
			return "#{";
		}

		@Override
		public String getExpressionSuffix() {
			return "}";
		}
	};

}
