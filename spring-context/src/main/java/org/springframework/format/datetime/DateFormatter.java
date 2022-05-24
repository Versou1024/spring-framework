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

package org.springframework.format.datetime;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.format.Formatter;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * A formatter for {@link java.util.Date} types.
 * Allows the configuration of an explicit date pattern and locale.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 * @see SimpleDateFormat
 */
public class DateFormatter implements Formatter<Date> {
	// 这个是最为重要的一个转换，因为Spring MVC中我们经常会使用Date来接收参数和返回，
	// 因此这个转换器个人建议有必要了解一下，非常有助于了解序列化的原理啥的~~~依赖于java.text.DateFormat来处理的。

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	private static final Map<ISO, String> ISO_PATTERNS;

	static {
		Map<ISO, String> formats = new EnumMap<>(ISO.class);
		formats.put(ISO.DATE, "yyyy-MM-dd");
		formats.put(ISO.TIME, "HH:mm:ss.SSSXXX");
		formats.put(ISO.DATE_TIME, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		ISO_PATTERNS = Collections.unmodifiableMap(formats);
	}


	@Nullable
	private String pattern; // 模式

	private int style = DateFormat.DEFAULT; // 样式

	@Nullable
	private String stylePattern;

	@Nullable
	private ISO iso; // ISO

	@Nullable
	private TimeZone timeZone; // 时区

	private boolean lenient = false;


	/**
	 * Create a new default DateFormatter.
	 */
	public DateFormatter() {
	}

	/**
	 * Create a new DateFormatter for the given date pattern.
	 */
	public DateFormatter(String pattern) {
		this.pattern = pattern;
	}


	/**
	 * Set the pattern to use to format date values.
	 * <p>If not specified, DateFormat's default style will be used.
	 */
	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	/**
	 * Set the ISO format used for this date.
	 * @param iso the {@link ISO} format
	 * @since 3.2
	 */
	public void setIso(ISO iso) {
		this.iso = iso;
	}

	/**
	 * Set the style to use to format date values.
	 * <p>If not specified, DateFormat's default style will be used.
	 * @see DateFormat#DEFAULT
	 * @see DateFormat#SHORT
	 * @see DateFormat#MEDIUM
	 * @see DateFormat#LONG
	 * @see DateFormat#FULL
	 */
	public void setStyle(int style) {
		this.style = style;
	}

	/**
	 * Set the two character to use to format date values. The first character used for
	 * the date style, the second is for the time style. Supported characters are
	 * <ul>
	 * <li>'S' = Small</li>
	 * <li>'M' = Medium</li>
	 * <li>'L' = Long</li>
	 * <li>'F' = Full</li>
	 * <li>'-' = Omitted</li>
	 * </ul>
	 * This method mimics the styles supported by Joda-Time.
	 * @param stylePattern two characters from the set {"S", "M", "L", "F", "-"}
	 * @since 3.2
	 */
	public void setStylePattern(String stylePattern) {
		this.stylePattern = stylePattern;
	}

	/**
	 * Set the TimeZone to normalize the date values into, if any.
	 */
	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}

	/**
	 * Specify whether or not parsing is to be lenient. Default is false.
	 * <p>With lenient parsing, the parser may allow inputs that do not precisely match the format.
	 * With strict parsing, inputs must match the format exactly.
	 */
	public void setLenient(boolean lenient) {
		this.lenient = lenient;
	}


	@Override
	public String print(Date date, Locale locale) {
		return getDateFormat(locale).format(date);
	}

	@Override
	public Date parse(String text, Locale locale) throws ParseException {
		return getDateFormat(locale).parse(text);
	}


	protected DateFormat getDateFormat(Locale locale) {
		// 1. 创建java的DateFormate
		DateFormat dateFormat = createDateFormat(locale);
		// 2. 设置时区
		if (this.timeZone != null) {
			dateFormat.setTimeZone(this.timeZone);
		}
		// 3. 指定日期/时间解析是否宽松
		dateFormat.setLenient(this.lenient);
		return dateFormat;
	}

	private DateFormat createDateFormat(Locale locale) {
		// 1. 有指定pattern,就直接使用pattern做解析和格式化操作
		// 创建并返回 SimpleDateFormate
		if (StringUtils.hasLength(this.pattern)) {
			return new SimpleDateFormat(this.pattern, locale);
		}
		// 2. pattern为空,iso非空且非ISO.NONE
		// 获取IOS对应的pattern模式,同样创创建SImpleDateFormate
		if (this.iso != null && this.iso != ISO.NONE) {
			String pattern = ISO_PATTERNS.get(this.iso);
			if (pattern == null) {
				throw new IllegalStateException("Unsupported ISO format " + this.iso);
			}
			SimpleDateFormat format = new SimpleDateFormat(pattern);
			format.setTimeZone(UTC);
			return format;
		}
		// 3. pattern和ISO都没有指定,查看stylePattern是否有效
		if (StringUtils.hasLength(this.stylePattern)) {
			// 利用 DateFormate 本来就有风格样式
			// 主要是分为 DateStyle 和 TimeStyle
			// 每个都有 SMLF 四种风格
			// 例如可以 DateStyle 为 Full,Time Style为 Small
			// 因此总的stylePattern应该就是上面组合的FS
			// 因此有很多中组合: FF SS MM LL F- S- 等等
			int dateStyle = getStylePatternForChar(0);
			int timeStyle = getStylePatternForChar(1);
			if (dateStyle != -1 && timeStyle != -1) {
				return DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale);
			}
			if (dateStyle != -1) {
				return DateFormat.getDateInstance(dateStyle, locale);
			}
			if (timeStyle != -1) {
				return DateFormat.getTimeInstance(timeStyle, locale);
			}
			throw new IllegalStateException("Unsupported style pattern '" + this.stylePattern + "'");

		}
		return DateFormat.getDateInstance(this.style, locale);
	}

	private int getStylePatternForChar(int index) {
		// 主要是将 S\M\L\F\- 解析为对应的DateFormate
		// 使用getDateTimeInstance获取日期和时间格式。您可以将不同的选项传递给这些工厂方法以控制结果的长度；从SHORT到MEDIUM到LONG到FULL 。确切的结果取决于语言环境，但通常：
		//		SHORT完全是数字，例如12.13.52或3:30pm
		//		MEDIUM较长，如Jan 12, 1952
		//		LONG更长，例如January 12, 1952或3:30:32pm
		//		FULL非常完整，例如Tuesday, April 12, 1952 AD or 3:30:42pm PST 。

		if (this.stylePattern != null && this.stylePattern.length() > index) {
			switch (this.stylePattern.charAt(index)) {
				case 'S': return DateFormat.SHORT;
				case 'M': return DateFormat.MEDIUM;
				case 'L': return DateFormat.LONG;
				case 'F': return DateFormat.FULL;
				case '-': return -1;
			}
		}
		throw new IllegalStateException("Unsupported style pattern '" + this.stylePattern + "'");
	}

}
