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

package org.springframework.web.method.annotation;

import java.beans.ConstructorProperties;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolve {@code @ModelAttribute} annotated method arguments and handle
 * return values from {@code @ModelAttribute} annotated methods.
 *
 * <p>Model attributes are obtained from the model or created with a default
 * constructor (and then added to the model). Once created the attribute is
 * populated via data binding to Servlet request parameters. Validation may be
 * applied if the argument is annotated with {@code @javax.validation.Valid}.
 * or Spring's own {@code @org.springframework.validation.annotation.Validated}.
 *
 * <p>When this handler is created with {@code annotationNotRequired=true}
 * any non-simple type argument and return value is regarded as a model
 * attribute with or without the presence of an {@code @ModelAttribute}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Vladislav Kisel
 * @since 3.1
 */
public class ModelAttributeMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {

	/*
	 * 它是一个Processor，既处理入参封装，也处理返回值.
	 * @ModelAttribute能标注在入参处来处理入参，能标在方法处来处理方法返回值。源码部分也只展示处理返回值部分：
	 */

	private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	protected final Log logger = LogFactory.getLog(getClass());

	private final boolean annotationNotRequired; // 如果为“true”，则非简单方法参数和返回值被视为带有或不带有@ModelAttribute注释的模型属


	/**
	 * Class constructor.
	 * @param annotationNotRequired if "true", non-simple method arguments and
	 * return values are considered model attributes with or without a
	 * {@code @ModelAttribute} annotation
	 */
	public ModelAttributeMethodProcessor(boolean annotationNotRequired) {
		this.annotationNotRequired = annotationNotRequired;
	}


	/**
	 * Returns {@code true} if the parameter is annotated with
	 * {@link ModelAttribute} or, if in default resolution mode, for any
	 * method parameter that is not a simple type.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 入参里标注了@ModelAttribute 或者（注意这个或者） annotationNotRequired = true并且不是isSimpleProperty()
		// isSimpleProperty()：八大基本类型/包装类型、Enum、Number等等 Date Class等等等等
		// 所以划重点：即使你没标注@ModelAttribute  单子还要不是基本类型等类型，都会进入到这里来处理
		// 当然这个行为是是收到annotationNotRequired属性影响的，具体的具体而论  它既有false的时候  也有true的时候

		// 1. 持有@ModelAttribute注解

		return (parameter.hasParameterAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(parameter.getParameterType())));
	}

	/**
	 * Resolve the argument from the model or if not found instantiate it with
	 * its default if it is available. The model attribute is then populated
	 * with request values via data binding and optionally validated
	 * if {@code @java.validation.Valid} is present on the argument.
	 * @throws BindException if data binding and validation result in an error
	 * and the next method parameter is not of type {@link Errors}
	 * @throws Exception if WebDataBinder initialization fails
	 */
	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
		// 说明：能进入到这里来的  证明入参里肯定是有对应注解的？？？
		// 显然不是，上面有说这是和属性值annotationNotRequired有关的~~~

		Assert.state(mavContainer != null, "ModelAttributeMethodProcessor requires ModelAndViewContainer");
		Assert.state(binderFactory != null, "ModelAttributeMethodProcessor requires WebDataBinderFactory");

		// 1. 拿到ModelKey名称~~~（注解里有写就以注解的为准）
		String name = ModelFactory.getNameForParameter(parameter);
		// 2. 拿到参数的注解@ModelAttribute本身
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		if (ann != null) {
			// 2.1 形参上的@ModelAttribute不为空 -- 注册是否应该为相应的模型属性发生数据绑定，对应于@ModelAttribute(binding=true/false)声明。
			mavContainer.setBinding(name, ann.binding());
		}

		Object attribute = null;
		BindingResult bindingResult = null;

		// 3. 如果model里有这个属性，那就好说，直接拿出来完事 -- 在ModelFactory.initModel中会提前运行@Model标注的局部或全局方法哦
		if (mavContainer.containsAttribute(name)) {
			attribute = mavContainer.getModel().get(name);
		}
		else {
			// 4. 如果mavContainer 不存在形参名指定属性，就需要中request中进行创建
			// 这是一个复杂的创建逻辑：
			// 	1. 如果是空构造，直接new一个实例出来
			// 	2. 若不是空构造，支持@ConstructorProperties解析给构造赋值
			//   	注意:这里就支持fieldDefaultPrefix前缀、fieldMarkerPrefix分隔符等能力了 最终完成获取一个属性
			// 	3. 调用BeanUtils.instantiateClass(ctor, args)来创建实例
			// 注意：但若是非空构造出来，是立马会执行valid校验的，此步骤若是空构造生成的实例，此步不会进行valid的，但是下一步会哦~
			try {
				// 通过调用形参类型的构造器创建对应的实例
				attribute = createAttribute(name, parameter, binderFactory, webRequest);
			}
			catch (BindException ex) {
				if (isBindExceptionRequired(parameter)) {
					// No BindingResult parameter -> fail with BindException
					throw ex;
				}
				// Otherwise, expose null/empty value and associated BindingResult
				if (parameter.getParameterType() == Optional.class) {
					attribute = Optional.empty();
				}
				bindingResult = ex.getBindingResult();
			}
		}
		// 5. 已经拿到有值的attribute, bindingResult==null即没有异常
		if (bindingResult == null) {
			// 5.1 为当前请求\attribute\nam创建binder -- Bean属性绑定和验证，如果binding时绑定失败，则跳过。
			WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name); // attribute 就是目标对象,name 就是目标对象的名字
			// 5.2 binder.getTarget() 返回包装的对象
			if (binder.getTarget() != null) {
				// 5.3 这个name对应的attribute没有禁用绑定
				if (!mavContainer.isBindingDisabled(name)) {
					// 5.4 就可以绑定到webRequest上
					bindRequestParameters(binder, webRequest);
				}
				// 5.5 执行valid校验~~~~ 如果适用，请验证模型属性。默认实现会检查@javax.validation.Valid 、Spring 的Validated以及名称以“Valid”开头的自定义注解。
				validateIfApplicable(binder, parameter);
				// 注意：此处抛出的异常是BindException
				// 6. RequestResponseBodyMethodProcessor抛出的异常是：MethodArgumentNotValidException
				// 期间是否发生过绑定异常
				if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
					throw new BindException(binder.getBindingResult());
				}
			}
			// Value type adaptation, also covering java.util.Optional
			if (!parameter.getParameterType().isInstance(attribute)) {
				attribute = binder.convertIfNecessary(binder.getTarget(), parameter.getParameterType(), parameter);
			}
			bindingResult = binder.getBindingResult();
		}

		// 可以即使是标注在入参上的@ModelAtrribute的属性值，最终也都是会放进Model里的~~~可怕吧
		Map<String, Object> bindingResultModel = bindingResult.getModel();
		mavContainer.removeAttributes(bindingResultModel);
		mavContainer.addAllAttributes(bindingResultModel);

		return attribute;
	}

	/**
	 * Extension point to create the model attribute if not found in the model,
	 * with subsequent parameter binding through bean properties (unless suppressed).
	 * <p>The default implementation typically uses the unique public no-arg constructor
	 * if available but also handles a "primary constructor" approach for data classes:
	 * It understands the JavaBeans {@link ConstructorProperties} annotation as well as
	 * runtime-retained parameter names in the bytecode, associating request parameters
	 * with constructor arguments by name. If no such constructor is found, the default
	 * constructor will be used (even if not public), assuming subsequent bean property
	 * bindings through setter methods.
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param parameter the method parameter declaration
	 * @param binderFactory for creating WebDataBinder instance
	 * @param webRequest the current request
	 * @return the created model attribute (never {@code null})
	 * @throws BindException in case of constructor argument binding failure
	 * @throws Exception in case of constructor invocation failure
	 * @see #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)
	 * @see BeanUtils#findPrimaryConstructor(Class)
	 */
	protected Object createAttribute(String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {
		// 如果在mavContainer中找不到对应name的属性值，则创建ModelAttribute，通过 bean 属性绑定参数（除非被抑制）。
		// 默认实现通常
		// 	1. 使用唯一的公共无参数构造函数（如果可用）
		// 	2. 但也处理数据类的“主构造函数”方法：它理解@ConstructorProperties注释以及字节码中运行时保留的参数名称，将请求参数与构造函数相关联按名称的参数。
		// 	3. 如果没有找到这样的构造函数，将使用默认构造函数（即使不是公共的），假设后续的 bean 属性绑定通过 setter 方法。

		// 1. 形参MethodParameter
		MethodParameter nestedParameter = parameter.nestedIfOptional();
		// 2. 形参实际类型
		Class<?> clazz = nestedParameter.getNestedParameterType();

		// 3.查找Kotlin下被解析形参的主要构造器 -- primaryConstructor只有Kotlin才支持 ~~ 可以忽略
		Constructor<?> ctor = BeanUtils.findPrimaryConstructor(clazz);
		if (ctor == null) {
			// 4. 非Kotlin时，即为Java类型，通过getConstructors获取构造器集合
			Constructor<?>[] ctors = clazz.getConstructors();
			// 5. 只有一个构造器,ctor可以直接指定
			if (ctors.length == 1) {
				ctor = ctors[0];
			}else {
				try {
					// 6. 多个构造器,需要getDeclaredConstructor()
					ctor = clazz.getDeclaredConstructor();
				}
				catch (NoSuchMethodException ex) {
					throw new IllegalStateException("No primary or default constructor found for " + clazz, ex);
				}
			}
		}

		// 7. 利用ctor构造器，为parameter构造实例
		Object attribute = constructAttribute(ctor, attributeName, parameter, binderFactory, webRequest);
		// 8. 如果形参是OPTIONAL类型的,还需要调用of封装进去
		if (parameter != nestedParameter) {
			attribute = Optional.of(attribute);
		}
		return attribute;
	}

	/**
	 * Construct a new attribute instance with the given constructor.
	 * <p>Called from
	 * {@link #createAttribute(String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}
	 * after constructor resolution.
	 * @param ctor the constructor to use
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param binderFactory for creating WebDataBinder instance
	 * @param webRequest the current request
	 * @return the created model attribute (never {@code null})
	 * @throws BindException in case of constructor argument binding failure
	 * @throws Exception in case of constructor invocation failure
	 * @since 5.1
	 */
	@SuppressWarnings("deprecation")
	protected Object constructAttribute(Constructor<?> ctor, String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {
		// 使用给定的构造函数构造一个新的属性实例

		// 1. 钩子方法: 允许子类来构造一个属性实例
		Object constructed = constructAttribute(ctor, attributeName, binderFactory, webRequest);
		// 2. 默认返回null,除非子类重写
		if (constructed != null) {
			return constructed;
		}

		// 3. 构造器参数数量
		if (ctor.getParameterCount() == 0) {
			// A single default constructor -> clearly a standard JavaBeans arrangement.
			// 3.1 空构造函数,直接实例化即可
			return BeanUtils.instantiateClass(ctor);
		}

		// A single data class constructor -> resolve constructor arguments from request parameters.
		// 4. 构造器上是否有@ConstructorProperties注解
		ConstructorProperties cp = ctor.getAnnotation(ConstructorProperties.class);
		// 5. 获取构造器上的形参名/也可以是注解@ConstructorProperties暗示的形参名
		String[] paramNames = (cp != null ? cp.value() : parameterNameDiscoverer.getParameterNames(ctor));
		Assert.state(paramNames != null, () -> "Cannot resolve parameter names for constructor " + ctor);
		// 6. 获取构造器参数type
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Assert.state(paramNames.length == paramTypes.length,
				() -> "Invalid number of parameter names: " + paramNames.length + " for constructor " + ctor);

		// 7. 开始构造参数
		Object[] args = new Object[paramTypes.length];
		WebDataBinder binder = binderFactory.createBinder(webRequest, null, attributeName);
		String fieldDefaultPrefix = binder.getFieldDefaultPrefix(); // 返回标记默认字段的参数的前缀。
		String fieldMarkerPrefix = binder.getFieldMarkerPrefix(); // 返回标记可能为空字段的参数的前缀。
		boolean bindingFailure = false; // 绑定失败标记位
		Set<String> failedParams = new HashSet<>(4);

		for (int i = 0; i < paramNames.length; i++) {
			// 7.1 参数名\参数类型
			String paramName = paramNames[i];
			Class<?> paramType = paramTypes[i];
			// 7.2 根据请求参数封装
			Object value = webRequest.getParameterValues(paramName);

			// Since WebRequest#getParameter exposes a single-value parameter as an array
			// with a single element, we unwrap the single value in such cases, analogous
			// to WebExchangeDataBinder.addBindValue(Map<String, Object>, String, List<?>).
			// 7.3 如果有这个对应构造器的paramName的请求参数,且为数组或集合,只需要取出其中一个
			if (ObjectUtils.isArray(value) && Array.getLength(value) == 1) {
				value = Array.get(value, 0);
			}

			// 7.4 如果这个构造器的参数paramName不存在请求参数中,是否有默认的前缀fieldDefaultPrefix
			if (value == null) {
				if (fieldDefaultPrefix != null) {
					// 7.5 有默认的前缀加上前缀后再次查找数据
					value = webRequest.getParameter(fieldDefaultPrefix + paramName);
				}
				// 7.6 加上前缀还是找不到构造器参数name对应的请求参数时
				if (value == null && fieldMarkerPrefix != null) {
					// 7.7 就加上为空字段的参数的前缀,通过binder.getEmptyValue()获取空值
					if (webRequest.getParameter(fieldMarkerPrefix + paramName) != null) {
						value = binder.getEmptyValue(paramType);
					}
				}
			}

			try {
				MethodParameter methodParam = new FieldAwareConstructorParameter(ctor, i, paramName);
				// 7.8 值value不存在,且构造器参数是Optional类型的,就用Optional.empty(),否则就是null
				if (value == null && methodParam.isOptional()) {
					args[i] = (methodParam.getParameterType() == Optional.class ? Optional.empty() : null);
				}
				// 7.9 值value存在 -- 注意这个存在也可能是 binder.getEmptyValue()获取的空
				else {
					args[i] = binder.convertIfNecessary(value, paramType, methodParam);
				}
			}
			catch (TypeMismatchException ex) {
				ex.initPropertyName(paramName);
				args[i] = value;
				failedParams.add(paramName);
				binder.getBindingResult().recordFieldValue(paramName, paramType, value);
				binder.getBindingErrorProcessor().processPropertyAccessException(ex, binder.getBindingResult());
				bindingFailure = true;
			}
		}

		if (bindingFailure) {
			BindingResult result = binder.getBindingResult();
			for (int i = 0; i < paramNames.length; i++) {
				String paramName = paramNames[i];
				if (!failedParams.contains(paramName)) {
					Object value = args[i];
					result.recordFieldValue(paramName, paramTypes[i], value);
					validateValueIfApplicable(binder, parameter, ctor.getDeclaringClass(), paramName, value);
				}
			}
			throw new BindException(result);
		}

		return BeanUtils.instantiateClass(ctor, args);
	}

	/**
	 * Construct a new attribute instance with the given constructor.
	 * @since 5.0
	 * @deprecated as of 5.1, in favor of
	 * {@link #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}
	 */
	@Deprecated
	@Nullable
	protected Object constructAttribute(Constructor<?> ctor, String attributeName,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		return null;
	}

	/**
	 * Extension point to bind the request to the target object.
	 * @param binder the data binder instance to use for the binding
	 * @param request the current request
	 */
	protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
		// 将请求绑定到目标对象的扩展点

		((WebRequestDataBinder) binder).bind(request);
	}

	/**
	 * Validate the model attribute if applicable.
	 * <p>The default implementation checks for {@code @javax.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @see WebDataBinder#validate(Object...)
	 * @see SmartValidator#validate(Object, Errors, Object...)
	 */
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
		for (Annotation ann : parameter.getParameterAnnotations()) {
			Object[] validationHints = determineValidationHints(ann);
			if (validationHints != null) {
				binder.validate(validationHints);
				break;
			}
		}
	}

	/**
	 * Validate the specified candidate value if applicable.
	 * <p>The default implementation checks for {@code @javax.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @param targetType the target type
	 * @param fieldName the name of the field
	 * @param value the candidate value
	 * @since 5.1
	 * @see #validateIfApplicable(WebDataBinder, MethodParameter)
	 * @see SmartValidator#validateValue(Class, String, Object, Errors, Object...)
	 */
	protected void validateValueIfApplicable(WebDataBinder binder, MethodParameter parameter,
			Class<?> targetType, String fieldName, @Nullable Object value) {

		for (Annotation ann : parameter.getParameterAnnotations()) {
			Object[] validationHints = determineValidationHints(ann);
			if (validationHints != null) {
				for (Validator validator : binder.getValidators()) {
					if (validator instanceof SmartValidator) {
						try {
							((SmartValidator) validator).validateValue(targetType, fieldName, value,
									binder.getBindingResult(), validationHints);
						}
						catch (IllegalArgumentException ex) {
							// No corresponding field on the target class...
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * Determine any validation triggered by the given annotation.
	 * @param ann the annotation (potentially a validation annotation)
	 * @return the validation hints to apply (possibly an empty array),
	 * or {@code null} if this annotation does not trigger any validation
	 * @since 5.1
	 */
	@Nullable
	private Object[] determineValidationHints(Annotation ann) {
		Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
		if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
			Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
			if (hints == null) {
				return new Object[0];
			}
			return (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
		}
		return null;
	}

	/**
	 * Whether to raise a fatal bind exception on validation errors.
	 * <p>The default implementation delegates to {@link #isBindExceptionRequired(MethodParameter)}.
	 * @param binder the data binder used to perform data binding
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @see #isBindExceptionRequired(MethodParameter)
	 */
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		return isBindExceptionRequired(parameter);
	}

	/**
	 * Whether to raise a fatal bind exception on validation errors.
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @since 5.0
	 */
	protected boolean isBindExceptionRequired(MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	/**
	 * Return {@code true} if there is a method-level {@code @ModelAttribute}
	 * or, in default resolution mode, for any return value type that is not
	 * a simple type.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		// 方法上标注有@ModelAttribute这个注解
		// 或者annotationNotRequired为true并且是简单类型isSimpleProperty() = true
		// 简单类型释义：8大基本类型+包装类型+Enum+CharSequence+Number+Date+URI+Local+Class
		// 数组类型并且是简单的数组(上面那些类型的数组类型)类型  也算作简单类型
		return (returnType.hasMethodAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(returnType.getParameterType())));
	}

	/**
	 * Add non-null return values to the {@link ModelAndViewContainer}.
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		// 做法相当简单，就是吧返回值放在model里面~~~~

		if (returnValue != null) {
			// 这个方法：@ModelAttribute指明了name，那就以它的为准
			// 否则就是一个复杂的获取过程：string...
			String name = ModelFactory.getNameForReturnValue(returnValue, returnType);
			// 存入 mavContainer 中
			mavContainer.addAttribute(name, returnValue);
		}
	}


	/**
	 * {@link MethodParameter} subclass which detects field annotations as well.
	 * @since 5.1
	 */
	private static class FieldAwareConstructorParameter extends MethodParameter {

		private final String parameterName;

		@Nullable
		private volatile Annotation[] combinedAnnotations;

		public FieldAwareConstructorParameter(Constructor<?> constructor, int parameterIndex, String parameterName) {
			super(constructor, parameterIndex);
			this.parameterName = parameterName;
		}

		@Override
		public Annotation[] getParameterAnnotations() {
			Annotation[] anns = this.combinedAnnotations;
			if (anns == null) {
				anns = super.getParameterAnnotations();
				try {
					Field field = getDeclaringClass().getDeclaredField(this.parameterName);
					Annotation[] fieldAnns = field.getAnnotations();
					if (fieldAnns.length > 0) {
						List<Annotation> merged = new ArrayList<>(anns.length + fieldAnns.length);
						merged.addAll(Arrays.asList(anns));
						for (Annotation fieldAnn : fieldAnns) {
							boolean existingType = false;
							for (Annotation ann : anns) {
								if (ann.annotationType() == fieldAnn.annotationType()) {
									existingType = true;
									break;
								}
							}
							if (!existingType) {
								merged.add(fieldAnn);
							}
						}
						anns = merged.toArray(new Annotation[0]);
					}
				}
				catch (NoSuchFieldException | SecurityException ex) {
					// ignore
				}
				this.combinedAnnotations = anns;
			}
			return anns;
		}

		@Override
		public String getParameterName() {
			return this.parameterName;
		}
	}

}
