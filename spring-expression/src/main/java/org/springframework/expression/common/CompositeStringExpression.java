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

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.TypedValue;
import org.springframework.lang.Nullable;

/**
 * Represents a template expression broken into pieces. Each piece will be an Expression
 * but pure text parts to the template will be represented as LiteralExpression objects.
 * An example of a template expression might be:
 *
 * <pre class="code">
 * &quot;Hello ${getName()}&quot;
 * </pre>
 *
 * which will be represented as a CompositeStringExpression of two parts. The first part
 * being a LiteralExpression representing 'Hello ' and the second part being a real
 * expression that will call {@code getName()} when invoked.
 *
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 3.0
 */
public class CompositeStringExpression implements Expression {
	// CompositeStringExpression
	// 表示一个分为多个部分的模板表达式（它只处理Template模式）。每个部分都是表达式，
	// 但模板的纯文本部分将表示为LiteralExpression对象。显然它是一个聚合

	private final String expressionString;

	/** The array of expressions that make up the composite expression. */
	private final Expression[] expressions;
	// 模板被分割后的多个表达式数组


	public CompositeStringExpression(String expressionString, Expression[] expressions) {
		this.expressionString = expressionString;
		this.expressions = expressions;
	}


	@Override
	public final String getExpressionString() {
		return this.expressionString;
	}

	public final Expression[] getExpressions() {
		return this.expressions;
	}

	@Override
	public String getValue() throws EvaluationException {
		// 它是把每个表达式的值都拼接起来了 因为它只会运用于Template模式~~~~~

		StringBuilder sb = new StringBuilder();
		for (Expression expression : this.expressions) {
			// 1. 获取表达式评估的值,转为String类型的
			String value = expression.getValue(String.class);
			if (value != null) {
				sb.append(value);
			}
		}
		return sb.toString();
	}

	@Override
	@Nullable
	public <T> T getValue(@Nullable Class<T> expectedResultType) throws EvaluationException {
		Object value = getValue();
		return ExpressionUtils.convertTypedValue(null, new TypedValue(value), expectedResultType);
	}

	@Override
	public String getValue(@Nullable Object rootObject) throws EvaluationException {
		StringBuilder sb = new StringBuilder();
		for (Expression expression : this.expressions) {
			String value = expression.getValue(rootObject, String.class);
			if (value != null) {
				sb.append(value);
			}
		}
		return sb.toString();
	}

	@Override
	@Nullable
	public <T> T getValue(@Nullable Object rootObject, @Nullable Class<T> desiredResultType) throws EvaluationException {
		// 可以设置 rootObject
		Object value = getValue(rootObject);
		return ExpressionUtils.convertTypedValue(null, new TypedValue(value), desiredResultType);
	}

	@Override
	public String getValue(EvaluationContext context) throws EvaluationException {
		// 可以设置 EvaluationContext
		StringBuilder sb = new StringBuilder();
		for (Expression expression : this.expressions) {
			String value = expression.getValue(context, String.class);
			if (value != null) {
				sb.append(value);
			}
		}
		return sb.toString();
	}

	@Override
	@Nullable
	public <T> T getValue(EvaluationContext context, @Nullable Class<T> expectedResultType)
			throws EvaluationException {

		Object value = getValue(context);
		return ExpressionUtils.convertTypedValue(context, new TypedValue(value), expectedResultType);
	}

	@Override
	public String getValue(EvaluationContext context, @Nullable Object rootObject) throws EvaluationException {
		StringBuilder sb = new StringBuilder();
		for (Expression expression : this.expressions) {
			String value = expression.getValue(context, rootObject, String.class);
			if (value != null) {
				sb.append(value);
			}
		}
		return sb.toString();
	}

	@Override
	@Nullable
	public <T> T getValue(EvaluationContext context, @Nullable Object rootObject, @Nullable Class<T> desiredResultType)
			throws EvaluationException {

		Object value = getValue(context,rootObject);
		return ExpressionUtils.convertTypedValue(context, new TypedValue(value), desiredResultType);
	}

	// 	getValueType() -> 返回值的类型一样的永远是String.class

	@Override
	public Class<?> getValueType() {
		return String.class;
	}

	@Override
	public Class<?> getValueType(EvaluationContext context) {
		return String.class;
	}

	@Override
	public Class<?> getValueType(@Nullable Object rootObject) throws EvaluationException {
		return String.class;
	}

	@Override
	public Class<?> getValueType(EvaluationContext context, @Nullable Object rootObject) throws EvaluationException {
		return String.class;
	}

	@Override
	public TypeDescriptor getValueTypeDescriptor() {
		return TypeDescriptor.valueOf(String.class);
	}

	@Override
	public TypeDescriptor getValueTypeDescriptor(@Nullable Object rootObject) throws EvaluationException {
		return TypeDescriptor.valueOf(String.class);
	}

	@Override
	public TypeDescriptor getValueTypeDescriptor(EvaluationContext context) {
		return TypeDescriptor.valueOf(String.class);
	}

	@Override
	public TypeDescriptor getValueTypeDescriptor(EvaluationContext context, @Nullable Object rootObject)
			throws EvaluationException {

		return TypeDescriptor.valueOf(String.class);
	}

	@Override
	public boolean isWritable(@Nullable Object rootObject) throws EvaluationException {
		return false;
	}

	@Override
	public boolean isWritable(EvaluationContext context) {
		return false;
	}

	@Override
	public boolean isWritable(EvaluationContext context, @Nullable Object rootObject) throws EvaluationException {
		return false;
	}

	@Override
	public void setValue(@Nullable Object rootObject, @Nullable Object value) throws EvaluationException {
		throw new EvaluationException(this.expressionString, "Cannot call setValue on a composite expression");
	}

	@Override
	public void setValue(EvaluationContext context, @Nullable Object value) throws EvaluationException {
		throw new EvaluationException(this.expressionString, "Cannot call setValue on a composite expression");
	}

	@Override
	public void setValue(EvaluationContext context, @Nullable Object rootObject, @Nullable Object value) throws EvaluationException {
		throw new EvaluationException(this.expressionString, "Cannot call setValue on a composite expression");
	}

}
