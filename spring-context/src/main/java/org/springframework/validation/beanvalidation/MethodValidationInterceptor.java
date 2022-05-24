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

import java.lang.reflect.Method;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.validation.annotation.Validated;

/**
 * An AOP Alliance {@link MethodInterceptor} implementation that delegates to a
 * JSR-303 provider for performing method-level validation on annotated methods.
 *
 * <p>Applicable methods have JSR-303 constraint annotations on their parameters
 * and/or on their return value (in the latter case specified at the method level,
 * typically as inline annotation).
 *
 * <p>E.g.: {@code public @NotNull Object myValidMethod(@NotNull String arg1, @Max(10) int arg2)}
 *
 * <p>Validation groups can be specified through Spring's {@link Validated} annotation
 * at the type level of the containing target class, applying to all public service methods
 * of that class. By default, JSR-303 will validate against its default group only.
 *
 * <p>As of Spring 5.0, this functionality requires a Bean Validation 1.1+ provider.
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see MethodValidationPostProcessor
 * @see javax.validation.executable.ExecutableValidator
 */
public class MethodValidationInterceptor implements MethodInterceptor {

	// 校验器
	private final Validator validator;


	/**
	 * Create a new MethodValidationInterceptor using a default JSR-303 validator underneath.
	 */
	public MethodValidationInterceptor() {
		this(Validation.buildDefaultValidatorFactory());
	}

	/**
	 * Create a new MethodValidationInterceptor using the given JSR-303 ValidatorFactory.
	 * @param validatorFactory the JSR-303 ValidatorFactory to use
	 */
	public MethodValidationInterceptor(ValidatorFactory validatorFactory) {
		this(validatorFactory.getValidator());
	}

	/**
	 * Create a new MethodValidationInterceptor using the given JSR-303 Validator.
	 * @param validator the JSR-303 Validator to use
	 */
	public MethodValidationInterceptor(Validator validator) {
		this.validator = validator;
	}


	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// Avoid Validator invocation on FactoryBean.getObjectType/isSingleton
		// 1. 避免在 FactoryBean.getObjectType或isSingleton 上调用验证器
		if (isFactoryBeanMetadataMethod(invocation.getMethod())) {
			return invocation.proceed();
		}

		// 2. 获取分组情况
		Class<?>[] groups = determineValidationGroups(invocation);

		// Standard Bean Validation 1.1 API
		// 3. 获取 验证器 -- 不同于Validator,对于可执行的方法/构造器需要使用ExecutableValidator校验器才可以
		ExecutableValidator execVal = this.validator.forExecutables();
		Method methodToValidate = invocation.getMethod();
		// 4. 存放验证结果
		Set<ConstraintViolation<Object>> result;

		try {
			// 5. 验证器验证参数
			result = execVal.validateParameters(
					invocation.getThis(), methodToValidate, invocation.getArguments(), groups);
		}
		catch (IllegalArgumentException ex) {
			// 6. 参数不通过验证
			// 此处回退了异步：找到bridged method方法再来一次
			// Probably a generic type mismatch between interface and impl as reported in SPR-12237 / HV-1011
			// Let's try to find the bridged method on the implementation class...
			methodToValidate = BridgeMethodResolver.findBridgedMethod(
					ClassUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass()));
			result = execVal.validateParameters(
					invocation.getThis(), methodToValidate, invocation.getArguments(), groups);
		}
		if (!result.isEmpty()) {
			// 7. 抛出异常
			throw new ConstraintViolationException(result);
		}

		// 8. 执行方法
		Object returnValue = invocation.proceed();

		// 9. 验证返回值的结果是否符合约束
		result = execVal.validateReturnValue(invocation.getThis(), methodToValidate, returnValue, groups);
		if (!result.isEmpty()) {
			// 10. 结果验证失败,抛出异常
			throw new ConstraintViolationException(result);
		}

		return returnValue;
	}

	private boolean isFactoryBeanMetadataMethod(Method method) {
		Class<?> clazz = method.getDeclaringClass();

		// Call from interface-based proxy handle, allowing for an efficient check?
		if (clazz.isInterface()) {
			return ((clazz == FactoryBean.class || clazz == SmartFactoryBean.class) &&
					!method.getName().equals("getObject"));
		}

		// Call from CGLIB proxy handle, potentially implementing a FactoryBean method?
		Class<?> factoryBeanType = null;
		if (SmartFactoryBean.class.isAssignableFrom(clazz)) {
			factoryBeanType = SmartFactoryBean.class;
		}
		else if (FactoryBean.class.isAssignableFrom(clazz)) {
			factoryBeanType = FactoryBean.class;
		}
		return (factoryBeanType != null && !method.getName().equals("getObject") &&
				ClassUtils.hasMethod(factoryBeanType, method));
	}

	/**
	 * Determine the validation groups to validate against for the given method invocation.
	 * <p>Default are the validation groups as specified in the {@link Validated} annotation
	 * on the containing target class of the method.
	 * @param invocation the current MethodInvocation
	 * @return the applicable validation groups as a Class array
	 */
	protected Class<?>[] determineValidationGroups(MethodInvocation invocation) {
		// 确定验证组

		// 1. 查找执行方法上是否有@Validated注解
		Validated validatedAnn = AnnotationUtils.findAnnotation(invocation.getMethod(), Validated.class);
		if (validatedAnn == null) {
			// 2. 方法上没有,查找所属类上是否有@Validated注解
			validatedAnn = AnnotationUtils.findAnnotation(invocation.getThis().getClass(), Validated.class);
		}
		// 3. 有的话,返回其value值,就是对应的groups分组情况
		return (validatedAnn != null ? validatedAnn.value() : new Class<?>[0]);
	}

}
