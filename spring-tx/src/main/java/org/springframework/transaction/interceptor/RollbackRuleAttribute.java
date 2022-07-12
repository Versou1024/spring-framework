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

package org.springframework.transaction.interceptor;

import java.io.Serializable;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Rule determining whether or not a given exception (and any subclasses)
 * should cause a rollback.
 *
 * <p>Multiple such rules can be applied to determine whether a transaction
 * should commit or rollback after an exception has been thrown.
 *
 * @author Rod Johnson
 * @since 09.04.2003
 * @see NoRollbackRuleAttribute
 */
@SuppressWarnings("serial")
public class RollbackRuleAttribute implements Serializable{
	// 命名: = 
	// RollbackRuleAttribute = Rollback Rule Attribute
	// 简单不做多余描述

	/**
	 * The {@link RollbackRuleAttribute rollback rule} for
	 * {@link RuntimeException RuntimeExceptions}.
	 */
	public static final RollbackRuleAttribute ROLLBACK_ON_RUNTIME_EXCEPTIONS = new RollbackRuleAttribute(RuntimeException.class);


	// 持有的,需要在异常栈中去找到的异常exceptionName,一旦找到就表示需要回滚
	private final String exceptionName;


	/**
	 * Create a new instance of the {@code RollbackRuleAttribute} class.
	 * <p>This is the preferred way to construct a rollback rule that matches
	 * the supplied {@link Exception} class, its subclasses, and its nested classes.
	 * @param clazz throwable class; must be {@link Throwable} or a subclass
	 * of {@code Throwable}
	 * @throws IllegalArgumentException if the supplied {@code clazz} is
	 * not a {@code Throwable} type or is {@code null}
	 */
	public RollbackRuleAttribute(Class<?> clazz) {
		Assert.notNull(clazz, "'clazz' cannot be null");
		if (!Throwable.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException(
					"Cannot construct rollback rule from [" + clazz.getName() + "]: it's not a Throwable");
		}
		this.exceptionName = clazz.getName();
	}

	/**
	 * Create a new instance of the {@code RollbackRuleAttribute} class
	 * for the given {@code exceptionName}.
	 * <p>This can be a substring, with no wildcard support at present. A value
	 * of "ServletException" would match
	 * {@code javax.servlet.ServletException} and subclasses, for example.
	 * <p><b>NB:</b> Consider carefully how specific the pattern is, and
	 * whether to include package information (which is not mandatory). For
	 * example, "Exception" will match nearly anything, and will probably hide
	 * other rules. "java.lang.Exception" would be correct if "Exception" was
	 * meant to define a rule for all checked exceptions. With more unusual
	 * exception names such as "BaseBusinessException" there's no need to use a
	 * fully package-qualified name.
	 * @param exceptionName the exception name pattern; can also be a fully
	 * package-qualified class name
	 * @throws IllegalArgumentException if the supplied
	 * {@code exceptionName} is {@code null} or empty
	 */
	public RollbackRuleAttribute(String exceptionName) {
		Assert.hasText(exceptionName, "'exceptionName' cannot be null or empty");
		this.exceptionName = exceptionName;
	}


	/**
	 * Return the pattern for the exception name.
	 */
	public String getExceptionName() {
		return this.exceptionName;
	}

	/**
	 * Return the depth of the superclass matching.
	 * <p>{@code 0} means {@code ex} matches exactly. Returns
	 * {@code -1} if there is no match. Otherwise, returns depth with the
	 * lowest depth winning.
	 */
	public int getDepth(Throwable ex) {
		// ❗️❗️❗️ 其核心方法就是当前方法getDepth()
		// 只要该方法返回非-1,即大于0小于Integer.MAX_VALUE的值,就表示在异常栈中找到对应异常
		
		return getDepth(ex.getClass(), 0);
	}


	private int getDepth(Class<?> exceptionClass, int depth) {
		// 1. 如果抛出的异常就是exceptionName,直接返回depth=0,表示完全匹配
		if (exceptionClass.getName().contains(this.exceptionName)) {
			return depth;
		}
		// 2. 如果抛出的异常时Throwable,非常大的范围,就返回-1,表示无法匹配
		if (exceptionClass == Throwable.class) {
			return -1;
		}
		// 3. 否则,去exceptionClass.superClass()中继续查找,并将depth+1
		return getDepth(exceptionClass.getSuperclass(), depth + 1);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RollbackRuleAttribute)) {
			return false;
		}
		RollbackRuleAttribute rhs = (RollbackRuleAttribute) other;
		return this.exceptionName.equals(rhs.exceptionName);
	}

	@Override
	public int hashCode() {
		return this.exceptionName.hashCode();
	}

	@Override
	public String toString() {
		return "RollbackRuleAttribute with pattern [" + this.exceptionName + "]";
	}

}
