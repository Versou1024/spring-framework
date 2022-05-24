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

package org.springframework.format.number;

import java.util.Set;

import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.annotation.NumberFormat.Style;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;

/**
 * Formats fields annotated with the {@link NumberFormat} annotation.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see NumberFormat
 */
public class NumberFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport
		implements AnnotationFormatterFactory<NumberFormat> {

	// 注解@NumberFormate支持标注在的字段类型上
	// 	static {
	//		Set<Class<?>> numberTypes = new HashSet<>(8);
	//		numberTypes.add(Byte.class);
	//		numberTypes.add(Short.class);
	//		numberTypes.add(Integer.class);
	//		numberTypes.add(Long.class);
	//		numberTypes.add(BigInteger.class);
	//		numberTypes.add(Float.class);
	//		numberTypes.add(Double.class);
	//		numberTypes.add(BigDecimal.class);
	//		STANDARD_NUMBER_TYPES = Collections.unmodifiableSet(numberTypes);
	//	}
	@Override
	public Set<Class<?>> getFieldTypes() {
		return NumberUtils.STANDARD_NUMBER_TYPES;
	}

	@Override
	public Printer<Number> getPrinter(NumberFormat annotation, Class<?> fieldType) {
		return configureFormatterFrom(annotation);
	}

	@Override
	public Parser<Number> getParser(NumberFormat annotation, Class<?> fieldType) {
		return configureFormatterFrom(annotation);
	}


	private Formatter<Number> configureFormatterFrom(NumberFormat annotation) {
		// 1. 解析 @NumberFormat 注解中的pattern值
		String pattern = resolveEmbeddedValue(annotation.pattern());
		// 2. 有 pattern 时直接返回 NumberStyleFormatter
		if (StringUtils.hasLength(pattern)) {
			// 2.1 数字格式化器
			return new NumberStyleFormatter(pattern);
		}
		else {
			// 3. 否则解析 @NumberFormat 注解中的style值
			Style style = annotation.style();
			// 3.1 货币格式化器
			if (style == Style.CURRENCY) {
				return new CurrencyStyleFormatter();
			}
			// 3.2 百分比格式化器
			else if (style == Style.PERCENT) {
				return new PercentStyleFormatter();
			}
			// 3.3 数字格式化器
			else {
				return new NumberStyleFormatter();
			}
		}
	}

}
