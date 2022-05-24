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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Currency;
import java.util.Locale;

import org.springframework.lang.Nullable;

/**
 * A BigDecimal formatter for number values in currency style.
 *
 * <p>Delegates to {@link java.text.NumberFormat#getCurrencyInstance(Locale)}.
 * Configures BigDecimal parsing so there is no loss of precision.
 * Can apply a specified {@link java.math.RoundingMode} to parsed values.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 4.2
 * @see #setLenient
 * @see #setRoundingMode
 */
public class CurrencyStyleFormatter extends AbstractNumberFormatter {
	// 从 String 转为 货币类型

	private int fractionDigits = 2;  // 默认保留两位小数点

	@Nullable
	private RoundingMode roundingMode;  // 四舍五入

	@Nullable
	private Currency currency;  // 货币 java.util.Currency

	@Nullable
	private String pattern;


	/**
	 * Specify the desired number of fraction digits.
	 * Default is 2.
	 */
	public void setFractionDigits(int fractionDigits) {
		this.fractionDigits = fractionDigits;
	}

	/**
	 * Specify the rounding mode to use for decimal parsing.
	 * Default is {@link java.math.RoundingMode#UNNECESSARY}.
	 */
	public void setRoundingMode(RoundingMode roundingMode) {
		this.roundingMode = roundingMode;
	}

	/**
	 * Specify the currency, if known.
	 */
	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	/**
	 * Specify the pattern to use to format number values.
	 * If not specified, the default DecimalFormat pattern is used.
	 * @see java.text.DecimalFormat#applyPattern(String)
	 */
	public void setPattern(String pattern) {
		this.pattern = pattern;
	}


	@Override
	public BigDecimal parse(String text, Locale locale) throws ParseException {
		BigDecimal decimal = (BigDecimal) super.parse(text, locale);
		if (this.roundingMode != null) {
			decimal = decimal.setScale(this.fractionDigits, this.roundingMode);
		}
		else {
			decimal = decimal.setScale(this.fractionDigits);
		}
		return decimal;
	}

	@Override
	protected NumberFormat getNumberFormat(Locale locale) {
		// 使用到java中的NumberFormat

		DecimalFormat format = (DecimalFormat) NumberFormat.getCurrencyInstance(locale);
		// 设置parse(String, ParsePosition)方法是否返回BigDecimal
		format.setParseBigDecimal(true);
		// 设置数字的小数部分允许的最大位数。对于除BigInteger和BigDecimal对象以外的格式化数字，使用newValue和 340 中的较低值。负输入值替换为 0。
		format.setMaximumFractionDigits(this.fractionDigits);
		// 设置数字的小数部分允许的最小位数。对于除BigInteger和BigDecimal对象以外的格式化数字，使用newValue和 340 中的较低值。负输入值替换为 0
		format.setMinimumFractionDigits(this.fractionDigits);
		if (this.roundingMode != null) {
			// 设置四舍五入的模式
			format.setRoundingMode(this.roundingMode);
		}
		if (this.currency != null) {
			// 在格式化货币值时设置此数字格式使用的货币。这不会更新数字格式使用的最小或最大小数位数。
			format.setCurrency(this.currency);
		}
		if (this.pattern != null) {
			// 将给定的模式应用于此 Format 对象。模式是各种格式属性的简写规范。这些属性也可以通过各种 setter 方法单独更改。
			format.applyPattern(this.pattern);
		}
		return format;
	}

}
