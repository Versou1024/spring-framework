/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import java.util.Locale;

import javax.validation.MessageInterpolator;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.Assert;

/**
 * Delegates to a target {@link MessageInterpolator} implementation but enforces Spring's
 * managed Locale. Typically used to wrap the validation provider's default interpolator.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.context.i18n.LocaleContextHolder#getLocale()
 */
public class LocaleContextMessageInterpolator implements MessageInterpolator {
	// 消息插值器
	// 委托给目标MessageInterpolator实现，但强制执行 Spring 的托管 Locale。
	// 通常用于包装验证提供程序的默认插值器。

	private final MessageInterpolator targetInterpolator;


	/**
	 * Create a new LocaleContextMessageInterpolator, wrapping the given target interpolator.
	 * @param targetInterpolator the target MessageInterpolator to wrap
	 */
	public LocaleContextMessageInterpolator(MessageInterpolator targetInterpolator) {
		Assert.notNull(targetInterpolator, "Target MessageInterpolator must not be null");
		this.targetInterpolator = targetInterpolator;
	}


	@Override
	public String interpolate(String message, Context context) {
		return this.targetInterpolator.interpolate(message, context, LocaleContextHolder.getLocale());
	}

	@Override
	public String interpolate(String message, Context context, Locale locale) {
		return this.targetInterpolator.interpolate(message, context, locale);
	}

}
