/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.format.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a field or method parameter should be formatted as a date or time.
 *
 * <p>Supports formatting by style pattern, ISO date time pattern, or custom format pattern string.
 * Can be applied to {@code java.util.Date}, {@code java.util.Calendar}, {@code Long} (for
 * millisecond timestamps) as well as JSR-310 <code>java.time</code> and Joda-Time value types.
 *
 * <p>For style-based formatting, set the {@link #style} attribute to be the style pattern code.
 * The first character of the code is the date style, and the second character is the time style.
 * Specify a character of 'S' for short style, 'M' for medium, 'L' for long, and 'F' for full.
 * A date or time may be omitted by specifying the style character '-'.
 *
 * <p>For ISO-based formatting, set the {@link #iso} attribute to be the desired {@link ISO} format,
 * such as {@link ISO#DATE}. For custom formatting, set the {@link #pattern} attribute to be the
 * DateTime pattern, such as {@code "yyyy/MM/dd hh:mm:ss a"}.
 *
 * <p>Each attribute is mutually exclusive, so only set one attribute per annotation instance
 * (the one most convenient for your formatting needs).
 *
 * <ul>
 * <li>When the pattern attribute is specified, it takes precedence over both the style and ISO attribute.</li>
 * <li>When the {@link #iso} attribute is specified, it takes precedence over the style attribute.</li>
 * <li>When no annotation attributes are specified, the default format applied is style-based
 * with a style code of 'SS' (short date, short time).</li>
 * </ul>
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see java.time.format.DateTimeFormatter
 * @see org.joda.time.format.DateTimeFormat
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface DateTimeFormat {

	// 以下三种值,只需要选择一种即可

	/**
	 * The style pattern to use to format the field.
	 * <p>Defaults to 'SS' for short date time. Set this attribute when you wish to format
	 * your field in accordance with a common style other than the default style.
	 */
	String style() default "SS";
	// 用于格式化字段的样式模式。
	// 短日期时间默认为“SS”。当您希望根据默认样式以外的通用样式设置字段格式时，请设置此属性

	/**
	 * The ISO pattern to use to format the field.
	 * <p>The possible ISO patterns are defined in the {@link ISO} enum.
	 * <p>Defaults to {@link ISO#NONE}, indicating this attribute should be ignored.
	 * Set this attribute when you wish to format your field in accordance with an ISO format.
	 */
	ISO iso() default ISO.NONE;
	// 用于格式化字段的 ISO 模式。
	// 可能的 ISO 模式在DateTimeFormat.ISO枚举中定义。
	// 默认为DateTimeFormat.ISO.NONE ，表示应忽略此属性。当您希望根据 ISO 格式设置字段格式时，请设置此属性。

	/**
	 * The custom pattern to use to format the field.
	 * <p>Defaults to empty String, indicating no custom pattern String has been specified.
	 * Set this attribute when you wish to format your field in accordance with a custom
	 * date time pattern not represented by a style or ISO format.
	 * <p>Note: This pattern follows the original {@link java.text.SimpleDateFormat} style,
	 * as also supported by Joda-Time, with strict parsing semantics towards overflows
	 * (e.g. rejecting a Feb 29 value for a non-leap-year). As a consequence, 'yy'
	 * characters indicate a year in the traditional style, not a "year-of-era" as in the
	 * {@link java.time.format.DateTimeFormatter} specification (i.e. 'yy' turns into 'uu'
	 * when going through that {@code DateTimeFormatter} with strict resolution mode).
	 */
	String pattern() default "";
	// 用于格式化字段的自定义模式。
	// 默认为空字符串，表示未指定自定义模式字符串。当您希望根据自定义日期时间模式设置字段格式时，请设置此属性，而不是由样式style或ISO格式表示。
	// 注意：此模式遵循原始的java.text.SimpleDateFormat样式，Joda-Time 也支持该样式，对溢出具有严格的解析语义（例如，拒绝非闰年的 2 月 29 日值）。因此，'yy' 字符表示传统样式中的年份，而不是java.time.format.DateTimeFormatter规范中的“年份”（即“yy”在经历那个时变成“uu”具有严格分辨率模式的DateTimeFormatter ）。


	/**
	 * Common ISO date time format patterns.
	 */
	enum ISO {

		/**
		 * The most common ISO Date Format {@code yyyy-MM-dd},
		 * e.g. "2000-10-31".
		 */
		DATE,
		// yyyy-MM-dd  2000-10-31

		/**
		 * The most common ISO Time Format {@code HH:mm:ss.SSSXXX},
		 * e.g. "01:30:00.000-05:00".
		 */
		TIME,
		// HH:mm:ss.SSSXXX  01:30:00.000-05:00

		/**
		 * The most common ISO DateTime Format {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX},
		 * e.g. "2000-10-31T01:30:00.000-05:00".
		 */
		DATE_TIME,
		// yyyy-MM-dd'T'HH:mm:ss.SSSXXX    2000-10-31T01:30:00.000-05:00

		/**
		 * Indicates that no ISO-based format pattern should be applied.
		 */
		NONE
	}

}
