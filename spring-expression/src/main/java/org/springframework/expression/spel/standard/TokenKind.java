/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.expression.spel.standard;

/**
 * Token Kinds.
 *
 * @author Andy Clement
 * @since 3.0
 */
enum TokenKind {

	// ordered by priority - operands first
	// 按优先级排序 - 操作数在前

	// 基本类型 - int\long\hexInt\hexLong\real\realFloat

	LITERAL_INT,

	LITERAL_LONG,

	LITERAL_HEXINT,

	LITERAL_HEXLONG,

	LITERAL_STRING,

	LITERAL_REAL,

	LITERAL_REAL_FLOAT,

	// 符号

	LPAREN("("),

	RPAREN(")"),

	COMMA(","),

	IDENTIFIER,

	COLON(":"),

	HASH("#"),

	RSQUARE("]"),

	LSQUARE("["),

	LCURLY("{"),

	RCURLY("}"),

	DOT("."),

	PLUS("+"),

	STAR("*"),

	MINUS("-"),

	SELECT_FIRST("^["), // 选择第一个 -- 用于集合/map中

	SELECT_LAST("$["), // 选择最后个 -- 用于集合/map中

	QMARK("?"),

	PROJECT("!["), // 集合投影 - 对集合中每个元素都做遍历

	DIV("/"),

	GE(">="),

	GT(">"),

	LE("<="),

	LT("<"),

	EQ("=="),

	NE("!="),

	MOD("%"),

	NOT("!"),

	ASSIGN("="),

	INSTANCEOF("instanceof"), // instanceOf 判断

	MATCHES("matches"), // 正则匹配

	BETWEEN("between"), // between

	SELECT("?["), // 过滤符号

	POWER("^"),

	ELVIS("?:"),
	// 就是三元运算符的 ?: 的缩写,即 x!=null ?: y
	// 转换就是 x!=null ? x : y

	SAFE_NAVI("?."), // 安全引用 -- 避免空指针异常 例如 car?.getBrand() -> 防止 car 为空的

	BEAN_REF("@"), // bean 引用 -- 使用@符号 + beanName

	FACTORY_BEAN_REF("&"), // FactoryBean 引用 --- 使用 & 符号 + FactoryBeanName

	SYMBOLIC_OR("||"),

	SYMBOLIC_AND("&&"),

	INC("++"),

	DEC("--");


	// 代表符号
	final char[] tokenChars;

	// 表示是否有载荷
	// 例如
	// LITERAL_INT 返回 true
	// DEC 返回 false
	private final boolean hasPayload;  // is there more to this token than simply the kind


	private TokenKind(String tokenString) {
		this.tokenChars = tokenString.toCharArray();
		this.hasPayload = (this.tokenChars.length == 0);
	}

	private TokenKind() {
		this("");
	}


	@Override
	public String toString() {
		return (name() + (this.tokenChars.length !=0 ? "(" + new String(this.tokenChars) +")" : ""));
	}

	public boolean hasPayload() {
		return this.hasPayload;
	}

	public int getLength() {
		return this.tokenChars.length;
	}

}
