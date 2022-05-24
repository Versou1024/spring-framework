/*
 * Copyright 2002-2020 the original author or authors.
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

import java.lang.annotation.Annotation;

import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * A convenient {@link BeanPostProcessor} implementation that delegates to a
 * JSR-303 provider for performing method-level validation on annotated methods.
 *
 * <p>Applicable methods have JSR-303 constraint annotations on their parameters
 * and/or on their return value (in the latter case specified at the method level,
 * typically as inline annotation), e.g.:
 *
 * <pre class="code">
 * public @NotNull Object myValidMethod(@NotNull String arg1, @Max(10) int arg2)
 * </pre>
 *
 * <p>Target classes with such annotated methods need to be annotated with Spring's
 * {@link Validated} annotation at the type level, for their methods to be searched for
 * inline constraint annotations. Validation groups can be specified through {@code @Validated}
 * as well. By default, JSR-303 will validate against its default group only.
 *
 * <p>As of Spring 5.0, this functionality requires a Bean Validation 1.1+ provider.
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see MethodValidationInterceptor
 * @see javax.validation.executable.ExecutableValidator
 */
@SuppressWarnings("serial")
public class MethodValidationPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor
		implements InitializingBean {
	// 一个方便的BeanPostProcessor实现，它委托给 JSR-303 提供者，用于对带注解的方法执行方法级别的验证。
	// 适用在方法的参数或返回值上具有 JSR-303 约束注释（在后一种情况下，在方法级别指定，通常作为内联注释），例如：
	//  public @NotNull Object myValidMethod(@NotNull String arg1, @Max(10) int arg2)
	//
	//	具有此类注解方法的目标类需要在类型级别使用 Spring 的 @Validated注解 进行注解，以便在其方法中搜索内联约束注解。
	//	验证组也可以通过@Validated指定。默认情况下，JSR-303 将仅针对其默认组进行验证。
	//	从 Spring 5.0 开始，此功能需要 Bean Validation 1.1+ 提供程序。

	// 备注：此处你标注@Valid是无用的~~~Spring可不提供识别
	// 当然你也可以自定义注解（下面提供了set方法~~~）
	// 但是注意：若自定义注解的话，此注解只决定了是否要代理，并不能指定分组哦  so，没啥事别给自己找麻烦吧
	private Class<? extends Annotation> validatedAnnotationType = Validated.class;

	// 这个是javax.validation.Validator
	@Nullable
	private Validator validator;


	/**
	 * Set the 'validated' annotation type.
	 * The default validated annotation type is the {@link Validated} annotation.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a class is supposed
	 * to be validated in the sense of applying method validation.
	 * @param validatedAnnotationType the desired annotation type
	 */
	public void setValidatedAnnotationType(Class<? extends Annotation> validatedAnnotationType) {
		// 可以自定义生效的注解
		Assert.notNull(validatedAnnotationType, "'validatedAnnotationType' must not be null");
		this.validatedAnnotationType = validatedAnnotationType;
	}

	/**
	 * Set the JSR-303 Validator to delegate to for validating methods.
	 * <p>Default is the default ValidatorFactory's default Validator.
	 */
	public void setValidator(Validator validator) {
		// 这个方法注意了：你可以自己传入一个Validator，并且可以是定制化的LocalValidatorFactoryBean哦~(推荐)
		// 建议传入LocalValidatorFactoryBean功能强大，从它里面生成一个验证器出来靠谱
		// Unwrap to the native Validator with forExecutables support
		if (validator instanceof LocalValidatorFactoryBean) {
			this.validator = ((LocalValidatorFactoryBean) validator).getValidator();
		}
		else if (validator instanceof SpringValidatorAdapter) {
			this.validator = validator.unwrap(Validator.class);
		}
		else {
			this.validator = validator;
		}
	}

	/**
	 * Set the JSR-303 ValidatorFactory to delegate to for validating methods,
	 * using its default Validator.
	 * <p>Default is the default ValidatorFactory's default Validator.
	 * @see javax.validation.ValidatorFactory#getValidator()
	 */
	public void setValidatorFactory(ValidatorFactory validatorFactory) {
		// 当然，你也可以简单粗暴的直接提供一个ValidatorFactory即可~

		this.validator = validatorFactory.getValidator();
	}


	// 毫无疑问，Pointcut使用AnnotationMatchingPointcut，并且支持内部类哦~
	// 说明@Aysnc使用的也是AnnotationMatchingPointcut，只不过因为它支持标注在类上和方法上，所以最终是组合的ComposablePointcut

	// 至于Advice通知，此处一样的是个`MethodValidationInterceptor`~~~~
	@Override
	public void afterPropertiesSet() {
		// 切入点 -- 只需要有对应的注解@Validated注解即可
		Pointcut pointcut = new AnnotationMatchingPointcut(this.validatedAnnotationType, true);
		// createMethodValidationAdvice(this.validator) 获取增强器 Advice
		this.advisor = new DefaultPointcutAdvisor(pointcut, createMethodValidationAdvice(this.validator));
	}

	/**
	 * Create AOP advice for method validation purposes, to be applied
	 * with a pointcut for the specified 'validated' annotation.
	 * @param validator the JSR-303 Validator to delegate to
	 * @return the interceptor to use (typically, but not necessarily,
	 * a {@link MethodValidationInterceptor} or subclass thereof)
	 * @since 4.2
	 */
	protected Advice createMethodValidationAdvice(@Nullable Validator validator) {
		// 增强器

		return (validator != null ? new MethodValidationInterceptor(validator) : new MethodValidationInterceptor());
	}

}
