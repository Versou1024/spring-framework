/*
 * Copyright 2002-2013 the original author or authors.
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

import javax.validation.ValidationException;

import org.apache.commons.logging.LogFactory;

/**
 * {@link LocalValidatorFactoryBean} subclass that simply turns
 * {@link org.springframework.validation.Validator} calls into no-ops
 * in case of no Bean Validation provider being available.
 *
 * <p>This is the actual class used by Spring's MVC configuration namespace,
 * in case of the {@code javax.validation} API being present but no explicit
 * Validator having been configured.
 *
 * @author Juergen Hoeller
 * @since 4.0.1
 */
public class OptionalValidatorFactoryBean extends LocalValidatorFactoryBean {
	// LocalValidatorFactoryBean子类，它只是将org.springframework.validation.Validator调用转换为无操作，
	// 以防没有可用的 Bean 验证提供程序。

	// 核心 在SpringMVC 中被使用校验器哦
	// 在 @EnableWebMvc 的 WebMvcConfigurationSupport 中
	// 以下代码块中进行了注册哦 -- @Bean修饰的mvcValidator方法
	// 	@Bean
	//	public Validator mvcValidator() {
	//		Validator validator = getValidator(); // 用户没有定制Validator,就是用默认的OptionalValidatorFactoryBean
	//		if (validator == null) {
	//			if (ClassUtils.isPresent("javax.validation.Validator", getClass().getClassLoader())) {
	//				Class<?> clazz;
	//				try {
	//					String className = "org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean";
	//					clazz = ClassUtils.forName(className, WebMvcConfigurationSupport.class.getClassLoader());
	//				}
	//				catch (ClassNotFoundException | LinkageError ex) {
	//					throw new BeanInitializationException("Failed to resolve default validator class", ex);
	//				}
	//				validator = (Validator) BeanUtils.instantiateClass(clazz);
	//			}
	//			else {
	//				validator = new NoOpValidator();
	//			}
	//		}
	//		return validator;
	//	}

	@Override
	public void afterPropertiesSet() {
		try {
			super.afterPropertiesSet();
		}
		catch (ValidationException ex) {
			LogFactory.getLog(getClass()).debug("Failed to set up a Bean Validation provider", ex);
		}
	}

}
