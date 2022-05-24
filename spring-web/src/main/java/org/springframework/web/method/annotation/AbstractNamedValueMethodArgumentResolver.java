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

package org.springframework.web.method.annotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Abstract base class for resolving method arguments from a named value.
 * Request parameters, request headers, and path variables are examples of named
 * values. Each may have a name, a required flag, and a default value.
 *
 * <p>Subclasses define how to do the following:
 * <ul>
 * <li>Obtain named value information for a method parameter
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required
 * <li>Optionally handle a resolved value
 * </ul>
 *
 * <p>A default value string can contain ${...} placeholders and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 *
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {
	// @since 3.1  负责从路径变量、请求、头等中拿到值。（都可以指定name、required、默认值等属性）
	// 子类需要做如下事：获取方法参数的命名值信息、将名称解析为参数值
	// 当需要参数值时处理缺少的参数值、可选地处理解析值

	//特别注意的是：默认值可以使用${}占位符，或者SpEL语句#{}是木有问题的

	@Nullable
	private final ConfigurableBeanFactory configurableBeanFactory; // BeanFactory

	@Nullable
	private final BeanExpressionContext expressionContext; // spel 表达式解析器

	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);
	// 缓存 - MethodParameter 对应 NamedValueInfo


	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}

	/**
	 * Create a new {@link AbstractNamedValueMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		// 注入BeanFactory
		this.configurableBeanFactory = beanFactory;
		this.expressionContext = (beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}


	@Override
	@Nullable
	// 注意此方法是final的，并不希望子类覆盖掉他~
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
		// 核心方法 -- 解析参数
		// 对此部分的处理步骤，我把它简述如下：
		//		基于MethodParameter构建NameValueInfo <-- 主要有name, defaultValue, required（其实主要是解析方法参数上标注的注解~）
		//		通过BeanExpressionResolver(解析${}占位符以及SpEL的#{}) 解析name
		//		通过模版方法resolveName从 HttpServletRequest\Http Headers\URI template variables 等等中获取对应的属性值（具体由子类去实现）
		//		对 arg==null这种情况的处理, 要么使用默认值, 若 required = true && arg == null, 则一般报出异常（boolean类型除外~）
		//		通过WebDataBinder将arg转换成Methodparameter.getParameterType()类型（注意：这里仅仅只是用了数据转换而已，并没有用bind()方法）

		// 1、获取形参上的注解并形成NamedValueInfo
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);
		// 2、支持到了Java 8 中支持的 java.util.Optional
		MethodParameter nestedParameter = parameter.nestedIfOptional();

		// 3. name属性（也就是注解标注的value/name属性）这里既会解析占位符，还会解析SpEL表达式，非常强大
		// 因为此时的 name 可能还是被 ${} 符号包裹, 则通过 BeanExpressionResolver 来进行解析
		Object resolvedName = resolveEmbeddedValuesAndExpressions(namedValueInfo.name);
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}

		// 4、【抽象方法】通过resolvedName和nestedParameter，去webQuest中查找指定名字的resolvedName的value
		// 模版抽象方法：将给定的参数类型和值名称解析为参数值。  由子类去实现
		// @PathVariable     --> 通过对uri解析后得到的decodedUriVariables值(常用)
		// @RequestParam     --> 通过 HttpServletRequest.getParameterValues(name) 获取（常用）
		// @RequestAttribute --> 通过 HttpServletRequest.getAttribute(name) 获取   <-- 这里的 scope 是 request
		// @SessionAttribute --> 略
		// @RequestHeader    --> 通过 HttpServletRequest.getHeaderValues(name) 获取
		// @CookieValue      --> 通过 HttpServletRequest.getCookies() 获取
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);
		// 若解析出来值仍旧为null，那就走defaultValue （若指定了的话）
		if (arg == null) {
			// 5、通过resolvedName查找解析失败，说明webRequest中不存在指定的resolveName
			if (namedValueInfo.defaultValue != null) {
				// 6、注解上有默认值，就使用默认值做绑定 --
				// 同样也需要解析defaultValue中的占位符${}，以及Spel表达式解析#{}
				arg = resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
			}
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {
				// 7、defaultValue默认值也不存在，但要求必须存在对应的值时，且不是特殊的Optional值，因此需要处理miss value时的情况
				// 它是个protected方法，默认抛出ServletRequestBindingException异常
				// 各子类都复写了此方法，转而抛出自己的异常（但都是ServletRequestBindingException的异常子类）
				handleMissingValue(namedValueInfo.name, nestedParameter, webRequest);
			}
			// 8、执行到此，说明允许设置为null值/False值
			// handleNullValue是private方法，来处理null值
			// 针对Boolean类型有这个判断：Boolean.TYPE.equals(paramType) 就return Boolean.FALSE;
			// 此处注意：Boolean.TYPE = Class.getPrimitiveClass("boolean") 它指的基本类型的boolean，而不是Boolean类型哦~~~
			// 如果到了这一步（value是null），但你还是基本类型，那就抛出异常了（只有boolean类型不会抛异常哦~）
			// 这里多嘴一句，即使请求传值为&bool=1，效果同bool=true的（1：true 0：false） 并且不区分大小写哦（TrUe效果同true）
			arg = handleNullValue(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());
		}
		// 10、通过name找到对应的需要填充的值，但填充的值为空字符串，且defaultValue不为空，就直接用defaultValue代替找到的填充值""
		// 兼容空串，若传入的是空串，依旧还是使用默认值（默认值支持占位符和SpEL）
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
			arg = resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
		}

		// 完成自动化的数据绑定~~~


		if (binderFactory != null) {
			// 11、WebDataBinderFactory为webRequest创建DataBinder -- 表示绑定的参数来自webRequest，绑定对象的名为namedValueInfo.name
			// 每个参数都会去创建一个Binder哦
			// 注意这里哦: target为null,那没会导致 getTypeConverter() 中 使用 getSimpleTypeConverter()
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
			try {
				// 12、如果有必要就开始进行转换 convertIfNecessary 并且做校验器校验
				// arg解析出来的值、parameter.getParameterType()参数类型、parameter方法形参
				arg = binder.convertIfNecessary(arg, parameter.getParameterType(), parameter);
			}
			catch (ConversionNotSupportedException ex) {
				// 注意这个异常：MethodArgumentConversionNotSupportedException  类型不匹配的异常
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				// //MethodArgumentTypeMismatchException是TypeMismatchException 的子类
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
		}

		// 13、处理解析出来的结果
		// protected的方法，本类为空实现，交给子类去复写（并不是必须的）
		// 唯独只有 PathVariableMethodArgumentResolver实现该方法,把解析处理的值存储到 HttpServletRequest.setAttribute中（若key已经存在也不会存储了）
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		// 14. 返回参数arg
		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		// 模板方法 -- 主要是在提供一个缓存的存取的模板,核心方法还是子类实现 createNamedValueInfo(parameter)

		// 检查缓存中parameter是否有对应的NamedValueInfo -- 提供上层的缓存，而具体的创建create或者update更新都是交给子类
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
		if (namedValueInfo == null) {
			// 1. 缓存未命中,根据形参parameter创建NameValueInfo
			// 【抽象】根据形参的注解或者形参创建NamedValueInfo
			namedValueInfo = createNamedValueInfo(parameter);
			// 2. 更新NamedValueInfo,没有name时需要更新name,没有defaultValue时的更新默认值
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			// 3. 存入缓存中
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);
	// 根据形参的注解或者形参创建NamedValueInfo

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		// 更新NamedValueInfo

		// 1. 当info.name不存在时，表明注解上没有指定的name属性。因而直接将形参名作为name设置到info中
		String name = info.name;
		if (info.name.isEmpty()) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument of type [" + parameter.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
			}
		}
		// 2. 注解中是否设置过defaultValue，如果没有就返回null，有的话就是用info.defaultValue
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 */
	@Nullable
	private Object resolveEmbeddedValuesAndExpressions(String value) {
		// 通过Values和Expression进行解析
		// configurableBeanFactory 与 expressionContext 为空时，直接返回value
		if (this.configurableBeanFactory == null || this.expressionContext == null) {
			return value;
		}
		// 利用 resolveEmbeddedValue 解析value中的占位符${}
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null) {
			return value;
		}
		// SPEL表达式计算#{}
		// expressionContext 解析上下文，可以存储#{}需要解析的变量
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return the resolved argument (may be {@code null})
	 * @throws Exception in case of errors
	 */
	@Nullable
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 4.3
	 */
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 */
	@Nullable
	private Object handleNullValue(String name, @Nullable Object value, Class<?> paramType) {
		// 引用都给null值，除了BOOLEAN给False值
		if (value == null) {
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param webRequest the current request
	 */
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 */
	protected static class NamedValueInfo {
		/*
		 * 封装类：
		 * 封装注解@PathVariable、@RequestParam等请求参数中的name、是否必须的required、是否有默认值 defaultValue
		 */

		private final String name;

		private final boolean required;

		@Nullable
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
