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

package org.springframework.expression.spel;

import org.springframework.core.SpringProperties;
import org.springframework.lang.Nullable;

/**
 * Configuration object for the SpEL expression parser.
 *
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Andy Clement
 * @since 3.0
 * @see org.springframework.expression.spel.standard.SpelExpressionParser#SpelExpressionParser(SpelParserConfiguration)
 */
public class SpelParserConfiguration {
	// 这里的SpelParserConfiguration表示：顾名思义它表示SpEL的配置类。
	// 在构建SpelExpressionParser时我们可以给其传递一个SpelParserConfiguration对象以对SpelExpressionParser进行配置。
	// 其可以用于指定在遇到List或Array为null时是否自动new一个对应的实例（一般不建议修改此值~以保持语义统一）

	private static final SpelCompilerMode defaultCompilerMode;

	static {
		/// 可以通过 Spring的属性 spring.expression.compiler.mode 指定编辑模式  - 解释/编译/混合
		String compilerMode = SpringProperties.getProperty("spring.expression.compiler.mode");
		// 默认是 SpelCompilerMode.OFF - 即每次都需要解释
		defaultCompilerMode = (compilerMode != null ? SpelCompilerMode.valueOf(compilerMode.toUpperCase()) : SpelCompilerMode.OFF);
	}


	// SpelCompilerMode -> Spel 编译 模式
	private final SpelCompilerMode compilerMode;

	@Nullable
	private final ClassLoader compilerClassLoader;

	private final boolean autoGrowNullReferences;
	// 如果null引用应该自动增长，则返回true 。
	// 碰到为null的，是否给自动new一个对象，比如new String()，new ArrayList()等等~

	private final boolean autoGrowCollections;
	// 是否允许自动增长集合
	// 专门针对于集合是否new

	private final int maximumAutoGrowSize;
	// 自动增长集合的最大size
	// 集合能够自动增长到的最大值~~~~


	/**
	 * Create a new {@code SpelParserConfiguration} instance with default settings.
	 */
	public SpelParserConfiguration() {
		this(null, null, false, false, Integer.MAX_VALUE);
	}

	/**
	 * Create a new {@code SpelParserConfiguration} instance.
	 * @param compilerMode the compiler mode for the parser
	 * @param compilerClassLoader the ClassLoader to use as the basis for expression compilation
	 */
	public SpelParserConfiguration(@Nullable SpelCompilerMode compilerMode, @Nullable ClassLoader compilerClassLoader) {
		this(compilerMode, compilerClassLoader, false, false, Integer.MAX_VALUE);
	}

	/**
	 * Create a new {@code SpelParserConfiguration} instance.
	 * @param autoGrowNullReferences if null references should automatically grow
	 * @param autoGrowCollections if collections should automatically grow
	 * @see #SpelParserConfiguration(boolean, boolean, int)
	 */
	public SpelParserConfiguration(boolean autoGrowNullReferences, boolean autoGrowCollections) {
		this(null, null, autoGrowNullReferences, autoGrowCollections, Integer.MAX_VALUE);
	}

	/**
	 * Create a new {@code SpelParserConfiguration} instance.
	 * @param autoGrowNullReferences if null references should automatically grow
	 * @param autoGrowCollections if collections should automatically grow
	 * @param maximumAutoGrowSize the maximum size that the collection can auto grow
	 */
	public SpelParserConfiguration(boolean autoGrowNullReferences, boolean autoGrowCollections, int maximumAutoGrowSize) {
		this(null, null, autoGrowNullReferences, autoGrowCollections, maximumAutoGrowSize);
	}

	/**
	 * Create a new {@code SpelParserConfiguration} instance.
	 * @param compilerMode the compiler mode that parsers using this configuration object should use
	 * @param compilerClassLoader the ClassLoader to use as the basis for expression compilation
	 * @param autoGrowNullReferences if null references should automatically grow
	 * @param autoGrowCollections if collections should automatically grow
	 * @param maximumAutoGrowSize the maximum size that the collection can auto grow
	 */
	public SpelParserConfiguration(@Nullable SpelCompilerMode compilerMode, @Nullable ClassLoader compilerClassLoader,
			boolean autoGrowNullReferences, boolean autoGrowCollections, int maximumAutoGrowSize) {

		this.compilerMode = (compilerMode != null ? compilerMode : defaultCompilerMode);
		this.compilerClassLoader = compilerClassLoader;
		this.autoGrowNullReferences = autoGrowNullReferences;
		this.autoGrowCollections = autoGrowCollections;
		this.maximumAutoGrowSize = maximumAutoGrowSize;
	}


	/**
	 * Return the configuration mode for parsers using this configuration object.
	 */
	public SpelCompilerMode getCompilerMode() {
		return this.compilerMode;
	}

	/**
	 * Return the ClassLoader to use as the basis for expression compilation.
	 */
	@Nullable
	public ClassLoader getCompilerClassLoader() {
		return this.compilerClassLoader;
	}

	/**
	 * Return {@code true} if {@code null} references should be automatically grown.
	 */
	public boolean isAutoGrowNullReferences() {
		// 如果null引用应该自动增长，则返回true 。
		return this.autoGrowNullReferences;
	}

	/**
	 * Return {@code true} if collections should be automatically grown.
	 */
	public boolean isAutoGrowCollections() {
		// 如果集合应该自动增长，则返回true
		return this.autoGrowCollections;
	}

	/**
	 * Return the maximum size that a collection can auto grow.
	 */
	public int getMaximumAutoGrowSize() {
		// 返回集合可以自动增长的最大大小
		return this.maximumAutoGrowSize;
	}

}
