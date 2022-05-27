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

package org.springframework.expression.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.lang.Nullable;

/**
 * An expression parser that understands templates. It can be subclassed by expression
 * parsers that do not offer first class support for templating.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Andy Clement
 * @since 3.0
 */
public abstract class TemplateAwareExpressionParser implements ExpressionParser {

	@Override
	public Expression parseExpression(String expressionString) throws ParseException {
		return parseExpression(expressionString, null);
	}

	@Override
	public Expression parseExpression(String expressionString, @Nullable ParserContext context) throws ParseException {
		if (context != null && context.isTemplate()) {
			return parseTemplate(expressionString, context);
		}
		else {
			// 抽象方法,子类实现
			return doParseExpression(expressionString, context);
		}
	}


	private Expression parseTemplate(String expressionString, ParserContext context) throws ParseException {
		// 解析模板 -- 返回表达式

		// 1. expressionString 为空,直接报错
		if (expressionString.isEmpty()) {
			return new LiteralExpression("");
		}

		// 2. 解析expressionString作为template时其中的表达式
		Expression[] expressions = parseExpressions(expressionString, context);
		if (expressions.length == 1) {
			// 3.1 一个expression直接返回
			return expressions[0];
		}
		else {
			// 3. 多个expression,需要组合起来
			// 第一个参数: expressionString 表示原表达式
			// 第二个参数: expressions 表示解析后的expressions
			return new CompositeStringExpression(expressionString, expressions);
		}
	}

	/**
	 * Helper that parses given expression string using the configured parser. The
	 * expression string can contain any number of expressions all contained in "${...}"
	 * markers. For instance: "foo${expr0}bar${expr1}". The static pieces of text will
	 * also be returned as Expressions that just return that static piece of text. As a
	 * result, evaluating all returned expressions and concatenating the results produces
	 * the complete evaluated string. Unwrapping is only done of the outermost delimiters
	 * found, so the string 'hello ${foo${abc}}' would break into the pieces 'hello ' and
	 * 'foo${abc}'. This means that expression languages that used ${..} as part of their
	 * functionality are supported without any problem. The parsing is aware of the
	 * structure of an embedded expression. It assumes that parentheses '(', square
	 * brackets '[' and curly brackets '}' must be in pairs within the expression unless
	 * they are within a string literal and a string literal starts and terminates with a
	 * single quote '.
	 * @param expressionString the expression string
	 * @return the parsed expressions
	 * @throws ParseException when the expressions cannot be parsed
	 */
	private Expression[] parseExpressions(String expressionString, ParserContext context) throws ParseException {
		// 使用配置的解析器解析给定表达式字符串的助手。
		// 表达式字符串可以包含任意数量的表达式，这些表达式都包含在“${...}”标记中。
		// 例如：“foo${expr0}bar${expr1}”。静态文本也将作为仅返回该静态文本的表达式返回。
		// 因此，评估所有返回的表达式并连接结果会生成完整的评估字符串。
		// 仅对找到的最外层分隔符进行展开，
		// 因此字符串“hello ${foo${abc}}”将分成“hello”和“foo${abc}”。
		// 这意味着支持使用 ${..} 作为其功能的一部分的表达式语言没有任何问题。
		// 解析知道嵌入表达式的结构。它假定括号 '('、方括号 '[' 和大括号 '}' 在表达式中必须成对出现，
		// 除非它们在字符串文字中并且字符串文字以单引号 ' 开始和结束。
		List<Expression> expressions = new ArrayList<>();
		String prefix = context.getExpressionPrefix();
		String suffix = context.getExpressionSuffix();
		int startIdx = 0;

		while (startIdx < expressionString.length()) {
			int prefixIndex = expressionString.indexOf(prefix, startIdx);
			if (prefixIndex >= startIdx) {
				// an inner expression was found - this is a composite
				if (prefixIndex > startIdx) {
					expressions.add(new LiteralExpression(expressionString.substring(startIdx, prefixIndex)));
				}
				int afterPrefixIndex = prefixIndex + prefix.length();
				int suffixIndex = skipToCorrectEndSuffix(suffix, expressionString, afterPrefixIndex);
				if (suffixIndex == -1) {
					throw new ParseException(expressionString, prefixIndex,
							"No ending suffix '" + suffix + "' for expression starting at character " +
							prefixIndex + ": " + expressionString.substring(prefixIndex));
				}
				if (suffixIndex == afterPrefixIndex) {
					throw new ParseException(expressionString, prefixIndex,
							"No expression defined within delimiter '" + prefix + suffix +
							"' at character " + prefixIndex);
				}
				String expr = expressionString.substring(prefixIndex + prefix.length(), suffixIndex);
				expr = expr.trim();
				if (expr.isEmpty()) {
					throw new ParseException(expressionString, prefixIndex,
							"No expression defined within delimiter '" + prefix + suffix +
							"' at character " + prefixIndex);
				}
				expressions.add(doParseExpression(expr, context));
				startIdx = suffixIndex + suffix.length();
			}
			else {
				// no more ${expressions} found in string, add rest as static text
				expressions.add(new LiteralExpression(expressionString.substring(startIdx)));
				startIdx = expressionString.length();
			}
		}

		return expressions.toArray(new Expression[0]);
	}

	/**
	 * Return true if the specified suffix can be found at the supplied position in the
	 * supplied expression string.
	 * @param expressionString the expression string which may contain the suffix
	 * @param pos the start position at which to check for the suffix
	 * @param suffix the suffix string
	 */
	private boolean isSuffixHere(String expressionString, int pos, String suffix) {
		int suffixPosition = 0;
		for (int i = 0; i < suffix.length() && pos < expressionString.length(); i++) {
			if (expressionString.charAt(pos++) != suffix.charAt(suffixPosition++)) {
				return false;
			}
		}
		if (suffixPosition != suffix.length()) {
			// the expressionString ran out before the suffix could entirely be found
			return false;
		}
		return true;
	}

	/**
	 * Copes with nesting, for example '${...${...}}' where the correct end for the first
	 * ${ is the final }.
	 * @param suffix the suffix
	 * @param expressionString the expression string
	 * @param afterPrefixIndex the most recently found prefix location for which the
	 * matching end suffix is being sought
	 * @return the position of the correct matching nextSuffix or -1 if none can be found
	 */
	private int skipToCorrectEndSuffix(String suffix, String expressionString, int afterPrefixIndex)
			throws ParseException {

		// Chew on the expression text - relying on the rules:
		// brackets must be in pairs: () [] {}
		// string literals are "..." or '...' and these may contain unmatched brackets
		int pos = afterPrefixIndex;
		int maxlen = expressionString.length();
		int nextSuffix = expressionString.indexOf(suffix, afterPrefixIndex);
		if (nextSuffix == -1) {
			return -1; // the suffix is missing
		}
		Deque<Bracket> stack = new ArrayDeque<>();
		while (pos < maxlen) {
			if (isSuffixHere(expressionString, pos, suffix) && stack.isEmpty()) {
				break;
			}
			char ch = expressionString.charAt(pos);
			switch (ch) {
				case '{':
				case '[':
				case '(':
					stack.push(new Bracket(ch, pos));
					break;
				case '}':
				case ']':
				case ')':
					if (stack.isEmpty()) {
						throw new ParseException(expressionString, pos, "Found closing '" + ch +
								"' at position " + pos + " without an opening '" +
								Bracket.theOpenBracketFor(ch) + "'");
					}
					Bracket p = stack.pop();
					if (!p.compatibleWithCloseBracket(ch)) {
						throw new ParseException(expressionString, pos, "Found closing '" + ch +
								"' at position " + pos + " but most recent opening is '" + p.bracket +
								"' at position " + p.pos);
					}
					break;
				case '\'':
				case '"':
					// jump to the end of the literal
					int endLiteral = expressionString.indexOf(ch, pos + 1);
					if (endLiteral == -1) {
						throw new ParseException(expressionString, pos,
								"Found non terminating string literal starting at position " + pos);
					}
					pos = endLiteral;
					break;
			}
			pos++;
		}
		if (!stack.isEmpty()) {
			Bracket p = stack.pop();
			throw new ParseException(expressionString, p.pos, "Missing closing '" +
					Bracket.theCloseBracketFor(p.bracket) + "' for '" + p.bracket + "' at position " + p.pos);
		}
		if (!isSuffixHere(expressionString, pos, suffix)) {
			return -1;
		}
		return pos;
	}


	/**
	 * Actually parse the expression string and return an Expression object.
	 * @param expressionString the raw expression string to parse
	 * @param context a context for influencing this expression parsing routine (optional)
	 * @return an evaluator for the parsed expression
	 * @throws ParseException an exception occurred during parsing
	 */
	protected abstract Expression doParseExpression(String expressionString, @Nullable ParserContext context)
			throws ParseException;


	/**
	 * This captures a type of bracket and the position in which it occurs in the
	 * expression. The positional information is used if an error has to be reported
	 * because the related end bracket cannot be found. Bracket is used to describe:
	 * square brackets [] round brackets () and curly brackets {}
	 */
	private static class Bracket {
		// 捕获了一种括号类型以及它在表达式中出现的位置。如果由于找不到相关的结束括号而必须报告错误，
		// 则使用位置信息。括号用来描述：方括号[]圆括号()和大括号{}

		char bracket;

		int pos;

		Bracket(char bracket, int pos) {
			this.bracket = bracket;
			this.pos = pos;
		}

		boolean compatibleWithCloseBracket(char closeBracket) {
			if (this.bracket == '{') {
				return closeBracket == '}';
			}
			else if (this.bracket == '[') {
				return closeBracket == ']';
			}
			return closeBracket == ')';
		}

		// 开括号
		static char theOpenBracketFor(char closeBracket) {
			if (closeBracket == '}') {
				return '{';
			}
			else if (closeBracket == ']') {
				return '[';
			}
			return '(';
		}

		// 闭括号
		static char theCloseBracketFor(char openBracket) {
			if (openBracket == '{') {
				return '}';
			}
			else if (openBracket == '[') {
				return ']';
			}
			return ')';
		}
	}

}
