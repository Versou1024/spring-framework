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

package org.springframework.validation.beanvalidation;

import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorContext;
import javax.validation.ValidatorFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

/**
 * Configurable bean class that exposes a specific JSR-303 Validator
 * through its original interface as well as through the Spring
 * {@link org.springframework.validation.Validator} interface.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public class CustomValidatorBean extends SpringValidatorAdapter implements Validator, InitializingBean {
	// 可配置（Custom）的Bean类，也同样的实现了双接口。它可以配置ValidatorFactory验证器工厂、MessageInterpolator插值器等…

	// 可配置以下三个配置项
	@Nullable
	private ValidatorFactory validatorFactory; // javax 的校验器工厂

	@Nullable
	private MessageInterpolator messageInterpolator; // 消息插值器

	@Nullable
	private TraversableResolver traversableResolver; // 可转移解析器


	/**
	 * Set the ValidatorFactory to obtain the target Validator from.
	 * <p>Default is {@link javax.validation.Validation#buildDefaultValidatorFactory()}.
	 */
	public void setValidatorFactory(ValidatorFactory validatorFactory) {
		this.validatorFactory = validatorFactory;
	}

	/**
	 * Specify a custom MessageInterpolator to use for this Validator.
	 */
	public void setMessageInterpolator(MessageInterpolator messageInterpolator) {
		this.messageInterpolator = messageInterpolator;
	}

	/**
	 * Specify a custom TraversableResolver to use for this Validator.
	 */
	public void setTraversableResolver(TraversableResolver traversableResolver) {
		this.traversableResolver = traversableResolver;
	}


	@Override
	public void afterPropertiesSet() {
		// 初始化操作

		if (this.validatorFactory == null) {
			// 1. 构建默认的validatorFactory
			this.validatorFactory = Validation.buildDefaultValidatorFactory();
		}

		// 2. 获取 ValidatorContext
		ValidatorContext validatorContext = this.validatorFactory.usingContext();
		// 3. 用户设置的 messageInterpolator 不存在,就获取默认的 validatorFactory.getMessageInterpolator()
		MessageInterpolator targetInterpolator = this.messageInterpolator;
		if (targetInterpolator == null) {
			targetInterpolator = this.validatorFactory.getMessageInterpolator();
		}
		validatorContext.messageInterpolator(new LocaleContextMessageInterpolator(targetInterpolator));
		// 4. traversableResolver 也是类似的
		if (this.traversableResolver != null) {
			validatorContext.traversableResolver(this.traversableResolver);
		}

		// 5. 最重要的,将Validator设置到适配器中
		// 这里就是最重要的一步哦: -- 将 Hibernate 的 Validator 拿出来赋给 SpringValidatorAdapter 的 targetValidator 属性
		// 真不错
		setTargetValidator(validatorContext.getValidator());
	}

}
