/*
 * Copyright 2002-2014 the original author or authors.
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
import java.util.ResourceBundle;

import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;

import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceResourceBundle;
import org.springframework.util.Assert;

/**
 * Implementation of Hibernate Validator 4.3/5.x's {@link ResourceBundleLocator} interface,
 * exposing a Spring {@link MessageSource} as localized {@link MessageSourceResourceBundle}.
 *
 * @author Juergen Hoeller
 * @since 3.0.4
 * @see ResourceBundleLocator
 * @see MessageSource
 * @see MessageSourceResourceBundle
 */
public class MessageSourceResourceBundleLocator implements ResourceBundleLocator {
	// 这个类也非常有意思，它扩展了Hibernate包的ResourceBundleLocator资源包定位器做国际化，而使用
	// Spring自己的国际化资源：org.springframework.context.MessageSource
	// 说明：ResourceBundleLocator是它Hibernate的一个SPI，Hibernate内部自己对它可是也有实现的哦~（Bean Validation内部大量的用到了SPI技术，有兴趣的可以了解）

	private final MessageSource messageSource;

	/**
	 * Build a MessageSourceResourceBundleLocator for the given MessageSource.
	 * @param messageSource the Spring MessageSource to wrap
	 */
	public MessageSourceResourceBundleLocator(MessageSource messageSource) {
		Assert.notNull(messageSource, "MessageSource must not be null");
		this.messageSource = messageSource;
	}

	@Override
	public ResourceBundle getResourceBundle(Locale locale) {
		return new MessageSourceResourceBundle(this.messageSource, locale);
	}

}
