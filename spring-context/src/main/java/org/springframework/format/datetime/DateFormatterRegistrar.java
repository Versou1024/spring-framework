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
import java.util.Date;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Configures basic date formatting for use with Spring, primarily for
 * {@link org.springframework.format.annotation.DateTimeFormat} declarations.
 * Applies to fields of type {@link Date}, {@link Calendar} and {@code long}.
 *
 * <p>Designed for direct instantiation but also exposes the static
 * {@link #addDateConverters(ConverterRegistry)} utility method for
 * ad-hoc use against any {@code ConverterRegistry} instance.
 *
 * @author Phillip Webb
 * @since 3.2
 * @see org.springframework.format.datetime.standard.DateTimeFormatterRegistrar
 * @see org.springframework.format.datetime.joda.JodaTimeFormatterRegistrar
 * @see FormatterRegistrar#registerFormatters
 */
public class DateFormatterRegistrar implements FormatterRegistrar {
	// 对JSR310的那些时间类进行支持。包括：LocalDateTime、ZonedDateTime、OffsetDateTime、OffsetTime等等

	@Nullable
	private DateFormatter dateFormatter;


	/**
	 * Set a global date formatter to register.
	 * <p>If not specified, no general formatter for non-annotated
	 * {@link Date} and {@link Calendar} fields will be registered.
	 */
	public void setFormatter(DateFormatter dateFormatter) {
		Assert.notNull(dateFormatter, "DateFormatter must not be null");
		this.dateFormatter = dateFormatter;
	}


	@Override
	public void registerFormatters(FormatterRegistry registry) {
		addDateConverters(registry);
		// 它是个静态方法
		// 对`@DateTimeFormat`的支持~~~~~
		// 所以如果你导入了joda包，这个注解可能会失效的~~~~需要特别注意~~~~~~~~~~~ 但下面的DateToLongConverter之类的依旧好使~
		// 但是你导入的是JSR310   没有这个问题~~~~
		// In order to retain back compatibility we only register Date/Calendar
		// types when a user defined formatter is specified (see SPR-10105)
		if (this.dateFormatter != null) {
			registry.addFormatter(this.dateFormatter);
			registry.addFormatterForFieldType(Calendar.class, this.dateFormatter);
		}
		// 添加 DateTimeFormatAnnotationFormatterFactory
		registry.addFormatterForFieldAnnotation(new DateTimeFormatAnnotationFormatterFactory());
	}

	/**
	 * Add date converters to the specified registry.
	 * @param converterRegistry the registry of converters to add to
	 */
	public static void addDateConverters(ConverterRegistry converterRegistry) {
		// 将日期转换器添加到指定的注册表。
		// DateToLongConverter\DateToCalendarConverter\CalendarToDateConverter\CalendarToLongConverter\
		// LongToDateConverter\LongToCalendarConverter
		converterRegistry.addConverter(new DateToLongConverter());
		converterRegistry.addConverter(new DateToCalendarConverter());
		converterRegistry.addConverter(new CalendarToDateConverter());
		converterRegistry.addConverter(new CalendarToLongConverter());
		converterRegistry.addConverter(new LongToDateConverter());
		converterRegistry.addConverter(new LongToCalendarConverter());
		// 都是几个内部类
	}


	private static class DateToLongConverter implements Converter<Date, Long> {

		@Override
		public Long convert(Date source) {
			return source.getTime();
		}
	}


	private static class DateToCalendarConverter implements Converter<Date, Calendar> {

		@Override
		public Calendar convert(Date source) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(source);
			return calendar;
		}
	}


	private static class CalendarToDateConverter implements Converter<Calendar, Date> {

		@Override
		public Date convert(Calendar source) {
			return source.getTime();
		}
	}


	private static class CalendarToLongConverter implements Converter<Calendar, Long> {

		@Override
		public Long convert(Calendar source) {
			return source.getTimeInMillis();
		}
	}


	private static class LongToDateConverter implements Converter<Long, Date> {

		@Override
		public Date convert(Long source) {
			return new Date(source);
		}
	}


	private static class LongToCalendarConverter implements Converter<Long, Calendar> {

		@Override
		public Calendar convert(Long source) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(source);
			return calendar;
		}
	}

}
