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

package org.springframework.aop.support;

import org.springframework.aop.Pointcut;
import org.springframework.lang.Nullable;

/**
 * Interface to be implemented by pointcuts that use String expressions.
 *
 * @author Rob Harrop
 * @since 2.0
 */
public interface ExpressionPointcut extends Pointcut {
	/*
	 * 表达式切入点：ExpressionPointcut 接口主要是为了支持AspectJ切点表达式语法而定义的接口。
	 * 这个是最强大的，Spring支持11种切点表达式，也是我们需要关注的重点
	 */

	/**
	 * Return the String expression for this pointcut.
	 */
	@Nullable
	String getExpression();

}
