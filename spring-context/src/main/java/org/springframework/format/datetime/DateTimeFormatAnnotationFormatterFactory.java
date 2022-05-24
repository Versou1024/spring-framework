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

package org.springframework.format.datetime;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

/**
 * Formats fields annotated with the {@link DateTimeFormat} annotation using a {@link DateFormatter}.
 *
 * @author Phillip Webb
 * @since 3.2
 * @see org.springframework.format.datetime.joda.JodaDateTimeFormatAnnotationFormatterFactory
 */
public class DateTimeFormatAnnotationFormatterFactory  extends EmbeddedValueResolutionSupport
		implements AnnotationFormatterFactory<DateTimeFormat> {
	// 它和@DateTimeFormat这个注解有关，作用在Date、Calendar、Long类型上。

	private static final Set<Class<?>> FIELD_TYPES;

	// 作用在 Date\Calendar\Long类型上
	// 该注解只能放在下面这集中类型上面~~~~才会生效
	static {
		Set<Class<?>> fieldTypes = new HashSet<>(4);
		fieldTypes.add(Date.class);
		fieldTypes.add(Calendar.class);
		fieldTypes.add(Long.class);
		FIELD_TYPES = Collections.unmodifiableSet(fieldTypes);
	}


	@Override
	public Set<Class<?>> getFieldTypes() {
		return FIELD_TYPES;
	}

	@Override
	public Printer<?> getPrinter(DateTimeFormat annotation, Class<?> fieldType) {
		return getFormatter(annotation, fieldType);
	}

	@Override
	public Parser<?> getParser(DateTimeFormat annotation, Class<?> fieldType) {
		return getFormatter(annotation, fieldType);
	}

	protected Formatter<Date> getFormatter(DateTimeFormat annotation, Class<?> fieldType) {
		DateFormatter formatter = new DateFormatter();
		// style属性支持使用占位符的形式~  setStylePattern
		// 'S' = Small  'M' = Medium  'L' = Long 'F' = Full '-' = Omitted
		// 注意：这里需要同时设置两个。比如SS SM等等
		// 第一个表示Date日期格式，第二个表示Time事件格式~~~~  注解默认值是SS
		String style = resolveEmbeddedValue(annotation.style());
		if (StringUtils.hasLength(style)) {
			formatter.setStylePattern(style);
		}
		formatter.setIso(annotation.iso());
		// patter也支持占位符~~~
		// DateFormatter里说过，若pattern指定了，就直接使用SimpleDateFormat格式化了
		// 否则根据stylePattern来进行拿模版实例：return DateFormat.getTimeInstance(timeStyle, locale)
		//static {
		//	Map<ISO, String> formats = new EnumMap<>(ISO.class);
		//	formats.put(ISO.DATE, "yyyy-MM-dd");
		//	formats.put(ISO.TIME, "HH:mm:ss.SSSXXX");
		//	formats.put(ISO.DATE_TIME, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		//	ISO_PATTERNS = Collections.unmodifiableMap(formats);
		//}
		String pattern = resolveEmbeddedValue(annotation.pattern());
		if (StringUtils.hasLength(pattern)) {
			formatter.setPattern(pattern);
		}
		return formatter;
	}

}
