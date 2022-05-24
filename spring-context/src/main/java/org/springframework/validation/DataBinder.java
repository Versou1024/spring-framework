/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.validation;

import java.beans.PropertyEditor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyBatchUpdateException;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.Formatter;
import org.springframework.format.support.FormatterPropertyEditorAdapter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * Binder that allows for setting property values onto a target object,
 * including support for validation and binding result analysis.
 * The binding process can be customized through specifying allowed fields,
 * required fields, custom editors, etc.
 *
 * <p>Note that there are potential security implications in failing to set an array
 * of allowed fields. In the case of HTTP form POST data for example, malicious clients
 * can attempt to subvert an application by supplying values for fields or properties
 * that do not exist on the form. In some cases this could lead to illegal data being
 * set on command objects <i>or their nested objects</i>. For this reason, it is
 * <b>highly recommended to specify the {@link #setAllowedFields allowedFields} property</b>
 * on the DataBinder.
 *
 * <p>The binding results can be examined via the {@link BindingResult} interface,
 * extending the {@link Errors} interface: see the {@link #getBindingResult()} method.
 * Missing fields and property access exceptions will be converted to {@link FieldError FieldErrors},
 * collected in the Errors instance, using the following error codes:
 *
 * <ul>
 * <li>Missing field error: "required"
 * <li>Type mismatch error: "typeMismatch"
 * <li>Method invocation error: "methodInvocation"
 * </ul>
 *
 * <p>By default, binding errors get resolved through the {@link BindingErrorProcessor}
 * strategy, processing for missing fields and property access exceptions: see the
 * {@link #setBindingErrorProcessor} method. You can override the default strategy
 * if needed, for example to generate different error codes.
 *
 * <p>Custom validation errors can be added afterwards. You will typically want to resolve
 * such error codes into proper user-visible error messages; this can be achieved through
 * resolving each error via a {@link org.springframework.context.MessageSource}, which is
 * able to resolve an {@link ObjectError}/{@link FieldError} through its
 * {@link org.springframework.context.MessageSource#getMessage(org.springframework.context.MessageSourceResolvable, java.util.Locale)}
 * method. The list of message codes can be customized through the {@link MessageCodesResolver}
 * strategy: see the {@link #setMessageCodesResolver} method. {@link DefaultMessageCodesResolver}'s
 * javadoc states details on the default resolution rules.
 *
 * <p>This generic data binder can be used in any kind of environment.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Stephane Nicoll
 * @author Kazuki Shimizu
 * @see #setAllowedFields
 * @see #setRequiredFields
 * @see #registerCustomEditor
 * @see #setMessageCodesResolver
 * @see #setBindingErrorProcessor
 * @see #bind
 * @see #getBindingResult
 * @see DefaultMessageCodesResolver
 * @see DefaultBindingErrorProcessor
 * @see org.springframework.context.MessageSource
 */
public class DataBinder implements PropertyEditorRegistry, TypeConverter {
	/*
	 * DataBinder 直接实现了PropertyEditorRegistry和TypeConverter这两个接口，因此它可以注册java.beans.PropertyEditor，并且能完成类型转换（TypeConverter）。
	 * 从源源码的分析中，大概能总结到DataBinder它提供了如下能力：
	 * 		1. 把属性值PropertyValues绑定到target上（bind()方法，依赖于PropertyAccessor实现~）
	 * 		2. 提供校验的能力：提供了public方法validate()对各个属性使用Validator执行校验~
	 * 		3. 提供了注册属性编辑器（PropertyEditor）和对类型进行转换的能力（TypeConverter）
	 *
	 * 还需要注意的是：
	 * initBeanPropertyAccess和initDirectFieldAccess两个初始化PropertyAccessor方法是互斥的
	 * 1. initBeanPropertyAccess()创建的是BeanPropertyBindingResult，内部依赖BeanWrapper
	 * 2. initDirectFieldAccess创建的是DirectFieldBindingResult，内部依赖DirectFieldAccessor
	 * 这两个方法内部没有显示的调用，但是Spring内部默认使用的是initBeanPropertyAccess()，具体可以参照getInternalBindingResult()方法~
	 *
	 * DataBinder提供了这两个非常独立的原子方法：绑定 + 校验。他俩结合完成了我们的数据绑定+数据校验，完全的和业务无关~
	 */

	/** Default object name used for binding: "target". */
	public static final String DEFAULT_OBJECT_NAME = "target";

	/** Default limit for array and collection growing: 256. */
	public static final int DEFAULT_AUTO_GROW_COLLECTION_LIMIT = 256; // 集合属性的长度不够时，且在256以下时，就允许扩容


	/**
	 * We'll create a lot of DataBinder instances: Let's use a static logger.
	 */
	protected static final Log logger = LogFactory.getLog(DataBinder.class);

	@Nullable
	private final Object target; // 待设置属性的目标对象

	private final String objectName; // 目标对线的名字name

	@Nullable
	private AbstractPropertyBindingResult bindingResult;// BindingResult：绑定错误、失败的时候会放进这里来~

	@Nullable
	private SimpleTypeConverter typeConverter; // 简单的类型转换器 - 注意SimpleTypeConverter底层就是和BeanWrapperImpl一样使用的TypeConverterDelegate，并且同时开启注册默认属性编辑器的功能
	// SimpleTypeConverter 是 TypeConverter -- 因此可以知道,里面会有PropertyEditor以及Converter两大数据绑定

	private boolean ignoreUnknownFields = true; // 是否忽略未知字段

	private boolean ignoreInvalidFields = false; // 是否忽略忽略非法的字段（比如我要Integer，你给传aaa，那肯定就不让绑定了，抛错）

	private boolean autoGrowNestedPaths = true; // 默认是支持级联的~~~

	private int autoGrowCollectionLimit = DEFAULT_AUTO_GROW_COLLECTION_LIMIT;

	@Nullable
	private String[] allowedFields; // 允许的字段

	@Nullable
	private String[] disallowedFields; // 不允许的字段

	@Nullable
	private String[] requiredFields; // 必须的字段

	@Nullable
	private ConversionService conversionService; // 核心 -- ConversionService -- 仅仅负责Converter的管理

	@Nullable
	private MessageCodesResolver messageCodesResolver; 	// 状态码处理器

	private BindingErrorProcessor bindingErrorProcessor = new DefaultBindingErrorProcessor();// 绑定出现错误的处理器~

	private final List<Validator> validators = new ArrayList<>(); 	// 校验器（这个非常重要)


	/**
	 * Create a new DataBinder instance, with default object name.
	 * @param target the target object to bind onto (or {@code null}
	 * if the binder is just used to convert a plain parameter value)
	 * @see #DEFAULT_OBJECT_NAME
	 */
	public DataBinder(@Nullable Object target) {
		// 使用默认对象名称创建一个新的 DataBinder 实例。
		// DEFAULT_OBJECT_NAME = "target"
		this(target, DEFAULT_OBJECT_NAME);
	}

	/**
	 * Create a new DataBinder instance.
	 * @param target the target object to bind onto (or {@code null}
	 * if the binder is just used to convert a plain parameter value)
	 * @param objectName the name of the target object
	 */
	public DataBinder(@Nullable Object target, String objectName) {
		// target需要绑定的对象，objectName绑定对象的名字
		this.target = ObjectUtils.unwrapOptional(target);
		this.objectName = objectName;
	}


	/**
	 * Return the wrapped target object.
	 */
	@Nullable
	public Object getTarget() {
		// 返回包装的目标对象。
		return this.target;
	}

	/**
	 * Return the name of the bound object.
	 */
	public String getObjectName() {
		// 返回绑定对象的名称。
		return this.objectName;
	}

	/**
	 * Set whether this binder should attempt to "auto-grow" a nested path that contains a null value.
	 * <p>If "true", a null path location will be populated with a default object value and traversed
	 * instead of resulting in an exception. This flag also enables auto-growth of collection elements
	 * when accessing an out-of-bounds index.
	 * <p>Default is "true" on a standard DataBinder. Note that since Spring 4.1 this feature is supported
	 * for bean property access (DataBinder's default mode) and field access.
	 * @see #initBeanPropertyAccess()
	 * @see org.springframework.beans.BeanWrapper#setAutoGrowNestedPaths
	 */
	public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
		// 设置此绑定器是否应尝试“自动增长”包含空值的嵌套路径。
		// 如果为“true”，则将使用默认对象值填充空路径位置并进行遍历，而不是导致异常。此标志还可以在访问越界索引时启用集合元素的自动增长。
		// 在标准 DataBinder 上默认为“true”。请注意，从 Spring 4.1 开始，bean 属性访问（DataBinder 的默认模式）和字段访问支持此功能。

		// 例如路径属性为 car.brand ,目标绑定对象是 man,但是man对象有car这个成员,但是没有初始化,因此car实际上为null值
		// 当autoGrowNestedPaths为true时,就不会抛出异常,而是会先用空构造函数创建一个Car对象,赋给man,然后给car对象的brand赋值
		Assert.state(this.bindingResult == null,
				"DataBinder is already initialized - call setAutoGrowNestedPaths before other configuration methods");
		this.autoGrowNestedPaths = autoGrowNestedPaths;
	}

	/**
	 * Return whether "auto-growing" of nested paths has been activated.
	 */
	public boolean isAutoGrowNestedPaths() {
		return this.autoGrowNestedPaths;
	}

	/**
	 * Specify the limit for array and collection auto-growing.
	 * <p>Default is 256, preventing OutOfMemoryErrors in case of large indexes.
	 * Raise this limit if your auto-growing needs are unusually high.
	 * @see #initBeanPropertyAccess()
	 * @see org.springframework.beans.BeanWrapper#setAutoGrowCollectionLimit
	 */
	public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
		// 集合/数组集合属性扩容的上限
		// 比如 dataBinder.bind(""list[10000]","xx");
		Assert.state(this.bindingResult == null,
				"DataBinder is already initialized - call setAutoGrowCollectionLimit before other configuration methods");
		this.autoGrowCollectionLimit = autoGrowCollectionLimit;
	}

	/**
	 * Return the current limit for array and collection auto-growing.
	 */
	public int getAutoGrowCollectionLimit() {
		return this.autoGrowCollectionLimit;
	}

	/**
	 * Initialize standard JavaBean property access for this DataBinder.
	 * <p>This is the default; an explicit call just leads to eager initialization.
	 * @see #initDirectFieldAccess()
	 * @see #createBeanPropertyBindingResult()
	 */
	public void initBeanPropertyAccess() {
		// 初始化 BeanPropertyAccess  -- 前提:BindingResult为null
		Assert.state(this.bindingResult == null,
				"DataBinder is already initialized - call initBeanPropertyAccess before other configuration methods");
		this.bindingResult = createBeanPropertyBindingResult();
	}

	/**
	 * Create the {@link AbstractPropertyBindingResult} instance using standard
	 * JavaBean property access.
	 * @since 4.2.1
	 */
	protected AbstractPropertyBindingResult createBeanPropertyBindingResult() {
		BeanPropertyBindingResult result = new BeanPropertyBindingResult(getTarget(),
				getObjectName(), isAutoGrowNestedPaths(), getAutoGrowCollectionLimit());

		if (this.conversionService != null) {
			// 设置转换服务
			result.initConversion(this.conversionService);
		}
		if (this.messageCodesResolver != null) {
			// 设置消息转换器
			result.setMessageCodesResolver(this.messageCodesResolver);
		}

		return result;
	}

	/**
	 * Initialize direct field access for this DataBinder,
	 * as alternative to the default bean property access.
	 * @see #initBeanPropertyAccess()
	 * @see #createDirectFieldBindingResult()
	 */
	public void initDirectFieldAccess() {
		// 注意: initBeanPropertyAccess 与  initDirectFieldAccess 只能选择一个执行哦
		// 功能类似的
		Assert.state(this.bindingResult == null,
				"DataBinder is already initialized - call initDirectFieldAccess before other configuration methods");
		this.bindingResult = createDirectFieldBindingResult();
	}

	/**
	 * Create the {@link AbstractPropertyBindingResult} instance using direct
	 * field access.
	 * @since 4.2.1
	 */
	protected AbstractPropertyBindingResult createDirectFieldBindingResult() {
		DirectFieldBindingResult result = new DirectFieldBindingResult(getTarget(),
				getObjectName(), isAutoGrowNestedPaths());

		if (this.conversionService != null) {
			result.initConversion(this.conversionService);
		}
		if (this.messageCodesResolver != null) {
			result.setMessageCodesResolver(this.messageCodesResolver);
		}

		return result;
	}

	/**
	 * Return the internal BindingResult held by this DataBinder,
	 * as an AbstractPropertyBindingResult.
	 */
	protected AbstractPropertyBindingResult getInternalBindingResult() {
		// 默认会使用 initBeanPropertyAccess() 来做一个 BindResult 的绑定

		if (this.bindingResult == null) {
			initBeanPropertyAccess();
		}
		return this.bindingResult;
	}

	/**
	 * Return the underlying PropertyAccessor of this binder's BindingResult.
	 */
	protected ConfigurablePropertyAccessor getPropertyAccessor() {
		return getInternalBindingResult().getPropertyAccessor();
	}

	/**
	 * Return this binder's underlying SimpleTypeConverter.
	 */
	protected SimpleTypeConverter getSimpleTypeConverter() {
		// 懒加载SimpleTypeConverter
		// 注意需要向 SimpleTypeConverter 中注入 conversionService 哦

		if (this.typeConverter == null) {
			this.typeConverter = new SimpleTypeConverter();
			if (this.conversionService != null) {
				this.typeConverter.setConversionService(this.conversionService);
			}
		}
		return this.typeConverter;
	}

	/**
	 * Return the underlying TypeConverter of this binder's BindingResult.
	 */
	protected PropertyEditorRegistry getPropertyEditorRegistry() {
		// 返回此绑定器的 BindingResult 的基础 TypeConverter。

		if (getTarget() != null) {
			// 有可能是BeanWrapperImpl,也有可能是DirectFieldAccessor
			return getInternalBindingResult().getPropertyAccessor();
		}
		else {
			return getSimpleTypeConverter();
		}
	}

	/**
	 * Return the underlying TypeConverter of this binder's BindingResult.
	 */
	protected TypeConverter getTypeConverter() {
		if (getTarget() != null) {
			// target 不为 null，就获取内部的绑定结果
			return getInternalBindingResult().getPropertyAccessor();
		}
		else {
			// 简单类型转换器
			return getSimpleTypeConverter();
		}
	}

	/**
	 * Return the BindingResult instance created by this DataBinder.
	 * This allows for convenient access to the binding results after
	 * a bind operation.
	 * @return the BindingResult instance, to be treated as BindingResult
	 * or as Errors instance (Errors is a super-interface of BindingResult)
	 * @see Errors
	 * @see #bind
	 */
	public BindingResult getBindingResult() {
		// 返回此 DataBinder 创建的 BindingResult 实例。这允许在绑定操作之后方便地访问绑定结果。
		return getInternalBindingResult();
	}


	/**
	 * Set whether to ignore unknown fields, that is, whether to ignore bind
	 * parameters that do not have corresponding fields in the target object.
	 * <p>Default is "true". Turn this off to enforce that all bind parameters
	 * must have a matching field in the target object.
	 * <p>Note that this setting only applies to <i>binding</i> operations
	 * on this DataBinder, not to <i>retrieving</i> values via its
	 * {@link #getBindingResult() BindingResult}.
	 * @see #bind
	 */
	public void setIgnoreUnknownFields(boolean ignoreUnknownFields) {
		// 设置是否忽略未知字段，即是否忽略目标对象中没有对应字段的绑定参数。
		// 默认为“真”。关闭此选项以强制所有绑定参数必须在目标对象中具有匹配字段。
		// 请注意，此设置仅适用于此 DataBinder 上的绑定操作，不适用于通过其BindingResult检索值。
		this.ignoreUnknownFields = ignoreUnknownFields;
	}

	/**
	 * Return whether to ignore unknown fields when binding.
	 */
	public boolean isIgnoreUnknownFields() {
		return this.ignoreUnknownFields;
	}

	/**
	 * Set whether to ignore invalid fields, that is, whether to ignore bind
	 * parameters that have corresponding fields in the target object which are
	 * not accessible (for example because of null values in the nested path).
	 * <p>Default is "false". Turn this on to ignore bind parameters for
	 * nested objects in non-existing parts of the target object graph.
	 * <p>Note that this setting only applies to <i>binding</i> operations
	 * on this DataBinder, not to <i>retrieving</i> values via its
	 * {@link #getBindingResult() BindingResult}.
	 * @see #bind
	 */
	public void setIgnoreInvalidFields(boolean ignoreInvalidFields) {
		this.ignoreInvalidFields = ignoreInvalidFields;
	}

	/**
	 * Return whether to ignore invalid fields when binding.
	 */
	public boolean isIgnoreInvalidFields() {
		return this.ignoreInvalidFields;
	}

	/**
	 * Register fields that should be allowed for binding. Default is all
	 * fields. Restrict this for example to avoid unwanted modifications
	 * by malicious users when binding HTTP request parameters.
	 * <p>Supports "xxx*", "*xxx" and "*xxx*" patterns. More sophisticated matching
	 * can be implemented by overriding the {@code isAllowed} method.
	 * <p>Alternatively, specify a list of <i>disallowed</i> fields.
	 * @param allowedFields array of field names
	 * @see #setDisallowedFields
	 * @see #isAllowed(String)
	 */
	public void setAllowedFields(@Nullable String... allowedFields) {
		// 注册应该允许绑定的字段。默认为所有字段
		this.allowedFields = PropertyAccessorUtils.canonicalPropertyNames(allowedFields);
	}

	/**
	 * Return the fields that should be allowed for binding.
	 * @return array of field names
	 */
	@Nullable
	public String[] getAllowedFields() {
		return this.allowedFields;
	}

	/**
	 * Register fields that should <i>not</i> be allowed for binding. Default is none.
	 * Mark fields as disallowed for example to avoid unwanted modifications
	 * by malicious users when binding HTTP request parameters.
	 * <p>Supports "xxx*", "*xxx" and "*xxx*" patterns. More sophisticated matching
	 * can be implemented by overriding the {@code isAllowed} method.
	 * <p>Alternatively, specify a list of <i>allowed</i> fields.
	 * @param disallowedFields array of field names
	 * @see #setAllowedFields
	 * @see #isAllowed(String)
	 */
	public void setDisallowedFields(@Nullable String... disallowedFields) {
		// 注册不应允许绑定的字段。默认为无
		this.disallowedFields = PropertyAccessorUtils.canonicalPropertyNames(disallowedFields);
	}

	/**
	 * Return the fields that should <i>not</i> be allowed for binding.
	 * @return array of field names
	 */
	@Nullable
	public String[] getDisallowedFields() {
		return this.disallowedFields;
	}

	/**
	 * Register fields that are required for each binding process.
	 * <p>If one of the specified fields is not contained in the list of
	 * incoming property values, a corresponding "missing field" error
	 * will be created, with error code "required" (by the default
	 * binding error processor).
	 * @param requiredFields array of field names
	 * @see #setBindingErrorProcessor
	 * @see DefaultBindingErrorProcessor#MISSING_FIELD_ERROR_CODE
	 */
	public void setRequiredFields(@Nullable String... requiredFields) {
		// 注册每个绑定过程所需的字段
		this.requiredFields = PropertyAccessorUtils.canonicalPropertyNames(requiredFields);
		if (logger.isDebugEnabled()) {
			logger.debug("DataBinder requires binding of required fields [" +
					StringUtils.arrayToCommaDelimitedString(requiredFields) + "]");
		}
	}

	/**
	 * Return the fields that are required for each binding process.
	 * @return array of field names
	 */
	@Nullable
	public String[] getRequiredFields() {
		return this.requiredFields;
	}

	/**
	 * Set the strategy to use for resolving errors into message codes.
	 * Applies the given strategy to the underlying errors holder.
	 * <p>Default is a DefaultMessageCodesResolver.
	 * @see BeanPropertyBindingResult#setMessageCodesResolver
	 * @see DefaultMessageCodesResolver
	 */
	public void setMessageCodesResolver(@Nullable MessageCodesResolver messageCodesResolver) {
		// 将错误解析为消息代码的策略。将给定策略应用于基础错误持有者
		Assert.state(this.messageCodesResolver == null, "DataBinder is already initialized with MessageCodesResolver");
		this.messageCodesResolver = messageCodesResolver;
		if (this.bindingResult != null && messageCodesResolver != null) {
			this.bindingResult.setMessageCodesResolver(messageCodesResolver);
		}
	}

	/**
	 * Set the strategy to use for processing binding errors, that is,
	 * required field errors and {@code PropertyAccessException}s.
	 * <p>Default is a DefaultBindingErrorProcessor.
	 * @see DefaultBindingErrorProcessor
	 */
	public void setBindingErrorProcessor(BindingErrorProcessor bindingErrorProcessor) {
		// 设置用于处理绑定错误的策略，即必填字段错误和PropertyAccessException 。
		// 默认为 DefaultBindingErrorProcessor。
		Assert.notNull(bindingErrorProcessor, "BindingErrorProcessor must not be null");
		this.bindingErrorProcessor = bindingErrorProcessor;
	}

	/**
	 * Return the strategy for processing binding errors.
	 */
	public BindingErrorProcessor getBindingErrorProcessor() {
		return this.bindingErrorProcessor;
	}

	/**
	 * Set the Validator to apply after each binding step.
	 * @see #addValidators(Validator...)
	 * @see #replaceValidators(Validator...)
	 */
	public void setValidator(@Nullable Validator validator) {
		// 将验证器设置为在每个绑定步骤后应用。

		assertValidators(validator);
		this.validators.clear();
		if (validator != null) {
			this.validators.add(validator);
		}
	}

	private void assertValidators(Validator... validators) {
		Object target = getTarget();
		for (Validator validator : validators) {
			if (validator != null && (target != null && !validator.supports(target.getClass()))) {
				throw new IllegalStateException("Invalid target for Validator [" + validator + "]: " + target);
			}
		}
	}

	/**
	 * Add Validators to apply after each binding step.
	 * @see #setValidator(Validator)
	 * @see #replaceValidators(Validator...)
	 */
	public void addValidators(Validator... validators) {
		assertValidators(validators);
		this.validators.addAll(Arrays.asList(validators));
	}

	/**
	 * Replace the Validators to apply after each binding step.
	 * @see #setValidator(Validator)
	 * @see #addValidators(Validator...)
	 */
	public void replaceValidators(Validator... validators) {
		assertValidators(validators);
		this.validators.clear();
		this.validators.addAll(Arrays.asList(validators));
	}

	/**
	 * Return the primary Validator to apply after each binding step, if any.
	 */
	@Nullable
	public Validator getValidator() {
		return (!this.validators.isEmpty() ? this.validators.get(0) : null);
	}

	/**
	 * Return the Validators to apply after data binding.
	 */
	public List<Validator> getValidators() {
		return Collections.unmodifiableList(this.validators);
	}


	//---------------------------------------------------------------------
	// Implementation of PropertyEditorRegistry/TypeConverter interface
	// 实现 PropertyEditorRegistry/TypeConverter 接口
	//---------------------------------------------------------------------

	/**
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 */
	public void setConversionService(@Nullable ConversionService conversionService) {
		Assert.state(this.conversionService == null, "DataBinder is already initialized with ConversionService");
		this.conversionService = conversionService;
		if (this.bindingResult != null && conversionService != null) {
			this.bindingResult.initConversion(conversionService);
		}
	}

	/**
	 * Return the associated ConversionService, if any.
	 */
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	/**
	 * Add a custom formatter, applying it to all fields matching the
	 * {@link Formatter}-declared type.
	 * <p>Registers a corresponding {@link PropertyEditor} adapter underneath the covers.
	 * @param formatter the formatter to add, generically declared for a specific type
	 * @since 4.2
	 * @see #registerCustomEditor(Class, PropertyEditor)
	 */
	public void addCustomFormatter(Formatter<?> formatter) {
		// formatter 在本类没有直接存储的容器

		// 1. 先用FormatterPropertyEditorAdapter将formatter适配为PropertyEditor
		FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
		// 2. 然后就可以使用PropertyEditorRegistry进行注册registerCustomEditor
		getPropertyEditorRegistry().registerCustomEditor(adapter.getFieldType(), adapter);
	}

	/**
	 * Add a custom formatter for the field type specified in {@link Formatter} class,
	 * applying it to the specified fields only, if any, or otherwise to all fields.
	 * <p>Registers a corresponding {@link PropertyEditor} adapter underneath the covers.
	 * @param formatter the formatter to add, generically declared for a specific type
	 * @param fields the fields to apply the formatter to, or none if to be applied to all
	 * @since 4.2
	 * @see #registerCustomEditor(Class, String, PropertyEditor)
	 */
	public void addCustomFormatter(Formatter<?> formatter, String... fields) {
		FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
		Class<?> fieldType = adapter.getFieldType();
		if (ObjectUtils.isEmpty(fields)) {
			getPropertyEditorRegistry().registerCustomEditor(fieldType, adapter);
		}
		else {
			for (String field : fields) {
				getPropertyEditorRegistry().registerCustomEditor(fieldType, field, adapter);
			}
		}
	}

	/**
	 * Add a custom formatter, applying it to the specified field types only, if any,
	 * or otherwise to all fields matching the {@link Formatter}-declared type.
	 * <p>Registers a corresponding {@link PropertyEditor} adapter underneath the covers.
	 * @param formatter the formatter to add (does not need to generically declare a
	 * field type if field types are explicitly specified as parameters)
	 * @param fieldTypes the field types to apply the formatter to, or none if to be
	 * derived from the given {@link Formatter} implementation class
	 * @since 4.2
	 * @see #registerCustomEditor(Class, PropertyEditor)
	 */
	public void addCustomFormatter(Formatter<?> formatter, Class<?>... fieldTypes) {
		FormatterPropertyEditorAdapter adapter = new FormatterPropertyEditorAdapter(formatter);
		if (ObjectUtils.isEmpty(fieldTypes)) {
			getPropertyEditorRegistry().registerCustomEditor(adapter.getFieldType(), adapter);
		}
		else {
			for (Class<?> fieldType : fieldTypes) {
				getPropertyEditorRegistry().registerCustomEditor(fieldType, adapter);
			}
		}
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
		// 注册属性编辑器 propertyEditor
		getPropertyEditorRegistry().registerCustomEditor(requiredType, propertyEditor);
	}

	@Override
	public void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String field, PropertyEditor propertyEditor) {
		// 第二个参数就是:propertyPath
		getPropertyEditorRegistry().registerCustomEditor(requiredType, field, propertyEditor);
	}

	@Override
	@Nullable
	public PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath) {
		return getPropertyEditorRegistry().findCustomEditor(requiredType, propertyPath);
	}

	@Override
	@Nullable
	public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType) throws TypeMismatchException {
		// 尝试转换
		// getTypeConverter()
		// getInternalBindingResult().getPropertyAccessor()
		// 或者是 SimpleTypeConverter
		return getTypeConverter().convertIfNecessary(value, requiredType);
	}

	@Override
	@Nullable
	public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType,
			@Nullable MethodParameter methodParam) throws TypeMismatchException {
		// 尝试转换
		return getTypeConverter().convertIfNecessary(value, requiredType, methodParam);
	}

	@Override
	@Nullable
	public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType, @Nullable Field field)
			throws TypeMismatchException {
		// 尝试转换
		return getTypeConverter().convertIfNecessary(value, requiredType, field);
	}

	@Nullable
	@Override
	public <T> T convertIfNecessary(@Nullable Object value, @Nullable Class<T> requiredType,
			@Nullable TypeDescriptor typeDescriptor) throws TypeMismatchException {

		return getTypeConverter().convertIfNecessary(value, requiredType, typeDescriptor);
	}


	/**
	 * Bind the given property values to this binder's target.
	 * <p>This call can create field errors, representing basic binding
	 * errors like a required field (code "required"), or type mismatch
	 * between value and bean property (code "typeMismatch").
	 * <p>Note that the given PropertyValues should be a throwaway instance:
	 * For efficiency, it will be modified to just contain allowed fields if it
	 * implements the MutablePropertyValues interface; else, an internal mutable
	 * copy will be created for this purpose. Pass in a copy of the PropertyValues
	 * if you want your original instance to stay unmodified in any case.
	 * @param pvs property values to bind
	 * @see #doBind(org.springframework.beans.MutablePropertyValues)
	 */
	public void bind(PropertyValues pvs) {
		// 数据绑定 PropertyValues
		MutablePropertyValues mpvs = (pvs instanceof MutablePropertyValues ?
				(MutablePropertyValues) pvs : new MutablePropertyValues(pvs));
		doBind(mpvs);
	}

	/**
	 * Actual implementation of the binding process, working with the
	 * passed-in MutablePropertyValues instance.
	 * @param mpvs the property values to bind,
	 * as MutablePropertyValues instance
	 * @see #checkAllowedFields
	 * @see #checkRequiredFields
	 * @see #applyPropertyValues
	 */
	protected void doBind(MutablePropertyValues mpvs) {
		// 1. 检查是否有允许Fields -- 通过 allowedFields 和 disallowedFields 做判断
		checkAllowedFields(mpvs);
		// 2. 检查是否有必须要的字段 -- 如果缺少将提示异常
		checkRequiredFields(mpvs);
		// 3. 开始使用
		applyPropertyValues(mpvs);
	}

	/**
	 * Check the given property values against the allowed fields,
	 * removing values for fields that are not allowed.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getAllowedFields
	 * @see #isAllowed(String)
	 */
	protected void checkAllowedFields(MutablePropertyValues mpvs) {
		PropertyValue[] pvs = mpvs.getPropertyValues();
		for (PropertyValue pv : pvs) {
			String field = PropertyAccessorUtils.canonicalPropertyName(pv.getName());
			// 1. 字段不允许使用
			if (!isAllowed(field)) {
				// 2. 从mpvs中移除
				mpvs.removePropertyValue(pv);
				// 3. 向BindResult中记录被抑制的field -- 校验器就可以忽略这种字段
				// 然后做一个日志输出
				getBindingResult().recordSuppressedField(field);
				if (logger.isDebugEnabled()) {
					logger.debug("Field [" + field + "] has been removed from PropertyValues " +
							"and will not be bound, because it has not been found in the list of allowed fields");
				}
			}
		}
	}

	/**
	 * Return if the given field is allowed for binding.
	 * Invoked for each passed-in property value.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*" matches,
	 * as well as direct equality, in the specified lists of allowed fields and
	 * disallowed fields. A field matching a disallowed pattern will not be accepted
	 * even if it also happens to match a pattern in the allowed list.
	 * <p>Can be overridden in subclasses.
	 * @param field the field to check
	 * @return if the field is allowed
	 * @see #setAllowedFields
	 * @see #setDisallowedFields
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean isAllowed(String field) {
		// allowed 为空,或者filed匹配allowed中的字段
		// 且
		// disallowed 为空,或者field不匹配disallowed中的字段
		// 就表示允许这个字段

		String[] allowed = getAllowedFields();
		String[] disallowed = getDisallowedFields();
		return ((ObjectUtils.isEmpty(allowed) || PatternMatchUtils.simpleMatch(allowed, field)) &&
				(ObjectUtils.isEmpty(disallowed) || !PatternMatchUtils.simpleMatch(disallowed, field)));
	}

	/**
	 * Check the given property values against the required fields,
	 * generating missing field errors where appropriate.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getRequiredFields
	 * @see #getBindingErrorProcessor
	 * @see BindingErrorProcessor#processMissingFieldError
	 */
	protected void checkRequiredFields(MutablePropertyValues mpvs) {
		// 根据必填字段检查给定的属性值，在适当的情况下生成缺失字段错误。

		String[] requiredFields = getRequiredFields();
		if (!ObjectUtils.isEmpty(requiredFields)) {
			// 1. propertyValues 存储有效的fileName即canonicalName
			Map<String, PropertyValue> propertyValues = new HashMap<>();
			PropertyValue[] pvs = mpvs.getPropertyValues();
			for (PropertyValue pv : pvs) {
				String canonicalName = PropertyAccessorUtils.canonicalPropertyName(pv.getName());
				propertyValues.put(canonicalName, pv);
			}
			// 遍历 requiredFields
			for (String field : requiredFields) {
				PropertyValue pv = propertyValues.get(field);
				boolean empty = (pv == null || pv.getValue() == null);
				// requiredField有对应的pv或者pv的value为null
				if (!empty) {
					// 检查pv.getValue()是不是空串
					if (pv.getValue() instanceof String) {
						empty = !StringUtils.hasText((String) pv.getValue());
					}
					else if (pv.getValue() instanceof String[]) {
						String[] values = (String[]) pv.getValue();
						empty = (values.length == 0 || !StringUtils.hasText(values[0]));
					}
				}
				// 没有requiredField有对应的pv或者pv的value为null
				if (empty) {
					// Use bind error processor to create FieldError.
					// 处理缺少字段的异常情况吧
					// 向 BindingResult 写入关于这个field无效
					getBindingErrorProcessor().processMissingFieldError(field, getInternalBindingResult());
					// Remove property from property values to bind:
					// It has already caused a field error with a rejected value.
					if (pv != null) {
						mpvs.removePropertyValue(pv);
						propertyValues.remove(field);
					}
				}
			}
		}
	}

	/**
	 * Apply given property values to the target object.
	 * <p>Default implementation applies all of the supplied property
	 * values as bean property values. By default, unknown fields will
	 * be ignored.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getTarget
	 * @see #getPropertyAccessor
	 * @see #isIgnoreUnknownFields
	 * @see #getBindingErrorProcessor
	 * @see BindingErrorProcessor#processPropertyAccessException
	 */
	protected void applyPropertyValues(MutablePropertyValues mpvs) {
		try {
			// Bind request parameters onto target object.
			// 将请求参数mpvs绑定到目标对象上。
			getPropertyAccessor().setPropertyValues(mpvs, isIgnoreUnknownFields(), isIgnoreInvalidFields());
		}
		catch (PropertyBatchUpdateException ex) {
			// Use bind error processor to create FieldErrors.
			for (PropertyAccessException pae : ex.getPropertyAccessExceptions()) {
				getBindingErrorProcessor().processPropertyAccessException(pae, getInternalBindingResult());
			}
		}
	}


	/**
	 * Invoke the specified Validators, if any.
	 * @see #setValidator(Validator)
	 * @see #getBindingResult()
	 */
	public void validate() {
		// 校验 -  对target遍历校验器validators

		Object target = getTarget();
		Assert.state(target != null, "No target to validate");
		BindingResult bindingResult = getBindingResult();
		// Call each validator with the same binding result
		for (Validator validator : getValidators()) {
			// 验证过程中任何异常或错误,都会交给bindingResult
			validator.validate(target, bindingResult);
		}
	}

	/**
	 * Invoke the specified Validators, if any, with the given validation hints.
	 * <p>Note: Validation hints may get ignored by the actual target Validator.
	 * @param validationHints one or more hint objects to be passed to a {@link SmartValidator}
	 * @since 3.1
	 * @see #setValidator(Validator)
	 * @see SmartValidator#validate(Object, Errors, Object...)
	 */
	public void validate(Object... validationHints) {
		// 校验 -- 对target遍历多个校验器

		Object target = getTarget();
		Assert.state(target != null, "No target to validate");
		BindingResult bindingResult = getBindingResult();
		// 1. 使用相同的绑定结果调用每个验证器
		for (Validator validator : getValidators()) {
			// 2. validationHints 就是分组信息,只有SmartValidator可以做分组校验
			if (!ObjectUtils.isEmpty(validationHints) && validator instanceof SmartValidator) {
				((SmartValidator) validator).validate(target, bindingResult, validationHints);
			}
			// 3. 直接validate校验
			else if (validator != null) {
				validator.validate(target, bindingResult);
			}
		}
	}

	/**
	 * Close this DataBinder, which may result in throwing
	 * a BindException if it encountered any errors.
	 * @return the model Map, containing target object and Errors instance
	 * @throws BindException if there were any errors in the bind operation
	 * @see BindingResult#getModel()
	 */
	public Map<?, ?> close() throws BindException {
		if (getBindingResult().hasErrors()) {
			throw new BindException(getBindingResult());
		}
		return getBindingResult().getModel();
	}

}
