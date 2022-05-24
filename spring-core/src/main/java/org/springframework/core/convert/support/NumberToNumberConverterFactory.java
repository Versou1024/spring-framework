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

package org.springframework.core.convert.support;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.NumberUtils;

/**
 * Converts from any JDK-standard Number implementation to any other JDK-standard Number implementation.
 *
 * <p>Support Number classes including Byte, Short, Integer, Float, Double, Long, BigInteger, BigDecimal. This class
 * delegates to {@link NumberUtils#convertNumberToTargetClass(Number, Class)} to perform the conversion.
 *
 * @author Keith Donald
 * @since 3.0
 * @see java.lang.Byte
 * @see java.lang.Short
 * @see java.lang.Integer
 * @see java.lang.Long
 * @see java.math.BigInteger
 * @see java.lang.Float
 * @see java.lang.Double
 * @see java.math.BigDecimal
 * @see NumberUtils
 */
final class NumberToNumberConverterFactory implements ConverterFactory<Number, Number>, ConditionalConverter {
	// 1:N 将Number转换为Number的子类
	// 比如将 Number类型值为45 转换为 Integer类型的值 45

	@Override
	public <T extends Number> Converter<Number, T> getConverter(Class<T> targetType) {
		return new NumberToNumber<>(targetType);
	}

	@Override
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		// 匹配前提是 sourceType 和 targetType 不是同一个类型的
		return !sourceType.equals(targetType);
	}


	private static final class NumberToNumber<T extends Number> implements Converter<Number, T> {

		private final Class<T> targetType;

		public NumberToNumber(Class<T> targetType) {
			this.targetType = targetType;
		}

		@Override
		public T convert(Number source) {
			// convertNumberToTargetClass() 注意抛出：
			// IllegalArgumentException – 如果不支持目标类（即不是 JDK 中包含的标准 Number 子类）
			return NumberUtils.convertNumberToTargetClass(source, this.targetType);
		}
	}

}
