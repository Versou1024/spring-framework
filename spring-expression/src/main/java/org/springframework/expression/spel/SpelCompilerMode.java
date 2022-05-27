/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.expression.spel;

/**
 * Captures the possible configuration settings for a compiler that can be
 * used when evaluating expressions.
 *
 * @author Andy Clement
 * @since 4.1
 */
public enum SpelCompilerMode {
	// 捕获可在评估表达式时使用的编译器的可能配置设置。

	/**
	 * The compiler is switched off; this is the default.
	 */
	OFF, // 编译器关闭,每次允许每次解释这是默认设置。

	/**
	 * In immediate mode, expressions are compiled as soon as possible (usually after 1 interpreted run).
	 * If a compiled expression fails it will throw an exception to the caller.
	 */
	IMMEDIATE, // 在立即模式下，表达式会尽快编译（通常在 1 次解释运行之后）。如果编译表达式失败，它将向调用者抛出异常。

	/**
	 * In mixed mode, expression evaluation silently switches between interpreted and compiled over time.
	 * After a number of runs the expression gets compiled. If it later fails (possibly due to inferred
	 * type information changing) then that will be caught internally and the system switches back to
	 * interpreted mode. It may subsequently compile it again later.
	 */
	MIXED
	// 在混合模式下，表达式求值会随着时间在解释和编译之间静默切换。
	// 在多次运行后，表达式被编译。如果它稍后失败（可能是由于推断的类型信息更改），
	// 那么它将在内部被捕获并且系统切换回解释模式。它可能随后会再次编译它。

}
