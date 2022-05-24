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

package org.springframework.core.convert.converter;

import org.springframework.lang.Nullable;

/**
 * A converter converts a source object of type {@code S} to a target of type {@code T}.
 *
 * <p>Implementations of this interface are thread-safe and can be shared.
 *
 * <p>Implementations may additionally implement {@link ConditionalConverter}.
 *
 * @author Keith Donald
 * @since 3.0
 * @param <S> the source type
 * @param <T> the target type
 */
@FunctionalInterface
public interface Converter<S, T> {
	/*
	 * 转换器 -- 支持从s转换到t
	 * 是类型到类型的转换
	 *
	 * 最开始的PropertyEditor是String到其他类型的转换
	 * 因此 Converter 是包含 PropertyEditor
	 *
	 * Spring提供了3种converter接口,分别是Converter、ConverterFactory和GenericConverter.一般用于1:1, 1:N, N:N的source->target类型转化。
	 * Converter接口 ：		 	使用最简单，最不灵活； 	支持1:1
	 * ConverterFactory接口 ：	使用较复杂，比较灵活；	支持1:N
	 * GenericConverter接口 ：	使用最复杂，也最灵活；	支持N:N
	 *
	 * 这个类大部分时候都是作为 ConverterFactory 的内部类
	 */

	/**
	 * Convert the source object of type {@code S} to target type {@code T}.
	 * @param source the source object to convert, which must be an instance of {@code S} (never {@code null})
	 * @return the converted object, which must be an instance of {@code T} (potentially {@code null})
	 * @throws IllegalArgumentException if the source cannot be converted to the desired target type
	 */
	@Nullable
	T convert(S source);
	// 将类型S的源对象转换为目标类型T 。

}
