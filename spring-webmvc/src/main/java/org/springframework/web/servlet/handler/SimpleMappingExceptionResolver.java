/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.servlet.handler;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;

/**
 * {@link org.springframework.web.servlet.HandlerExceptionResolver} implementation
 * that allows for mapping exception class names to view names, either for a set of
 * given handlers or for all handlers in the DispatcherServlet.
 *
 * <p>Error views are analogous to error page JSPs, but can be used with any kind of
 * exception including any checked one, with fine-granular mappings for specific handlers.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 22.11.2003
 * @see org.springframework.web.servlet.DispatcherServlet
 */
public class SimpleMappingExceptionResolver extends AbstractHandlerExceptionResolver {
	// 几乎没有使用,已经被抛弃

	/** The default name of the exception attribute: "exception". */
	public static final String DEFAULT_EXCEPTION_ATTRIBUTE = "exception";


	@Nullable
	private Properties exceptionMappings; // 异常映射表
	// 通过异常类型Properties exceptionMappings;映射。它的key可以是全类名、短名称，同时还有继承效果：比如key是Exception那将匹配所有的异常。value是view name视图名称
	// 设置异常类名称和错误视图名称之间的映射。异常类名可以是子字符串，目前不支持通配符。例如，“ServletException”的值将匹配javax.servlet.ServletException和子类

	@Nullable
	private Class<?>[] excludedExceptions; // 设置要从异常映射中排除的一个或多个异常。

	@Nullable
	private String defaultErrorView; 		// 设置默认错误视图的名称。如果未找到特定映射，则将返回此视图

	@Nullable
	private Integer defaultStatusCode; // 默认的错误码

	private Map<String, Integer> statusCodes = new HashMap<>();
	// 设置此异常解析器将应用于给定已解析错误视图的 HTTP 状态代码。键是视图名称；值是状态代码


	@Nullable
	private String exceptionAttribute = DEFAULT_EXCEPTION_ATTRIBUTE; // 异常属性的key


	/**
	 * Set the mappings between exception class names and error view names.
	 * The exception class name can be a substring, with no wildcard support at present.
	 * A value of "ServletException" would match {@code javax.servlet.ServletException}
	 * and subclasses, for example.
	 * <p><b>NB:</b> Consider carefully how
	 * specific the pattern is, and whether to include package information (which isn't mandatory).
	 * For example, "Exception" will match nearly anything, and will probably hide other rules.
	 * "java.lang.Exception" would be correct if "Exception" was meant to define a rule for all
	 * checked exceptions. With more unusual exception names such as "BaseBusinessException"
	 * there's no need to use a FQN.
	 * @param mappings exception patterns (can also be fully qualified class names) as keys,
	 * and error view names as values
	 */
	public void setExceptionMappings(Properties mappings) {
		// 设置异常类名称和错误视图名称之间的映射。异常类名可以是子字符串，目前不支持通配符。例如，“ServletException”的值将匹配javax.servlet.ServletException和子类
		this.exceptionMappings = mappings;
	}

	/**
	 * Set one or more exceptions to be excluded from the exception mappings.
	 * Excluded exceptions are checked first and if one of them equals the actual
	 * exception, the exception will remain unresolved.
	 * @param excludedExceptions one or more excluded exception types
	 */
	public void setExcludedExceptions(Class<?>... excludedExceptions) {
		// 设置要从异常映射中排除的一个或多个异常。
		this.excludedExceptions = excludedExceptions;
	}

	/**
	 * Set the name of the default error view.
	 * This view will be returned if no specific mapping was found.
	 * <p>Default is none.
	 */
	public void setDefaultErrorView(String defaultErrorView) {
		// 设置默认错误视图的名称。如果未找到特定映射，则将返回此视图
		this.defaultErrorView = defaultErrorView;
	}

	/**
	 * Set the HTTP status code that this exception resolver will apply for a given
	 * resolved error view. Keys are view names; values are status codes.
	 * <p>Note that this error code will only get applied in case of a top-level request.
	 * It will not be set for an include request, since the HTTP status cannot be modified
	 * from within an include.
	 * <p>If not specified, the default status code will be applied.
	 * @see #setDefaultStatusCode(int)
	 */
	public void setStatusCodes(Properties statusCodes) {
		// 设置此异常解析器将应用于给定已解析错误视图的 HTTP 状态代码。键是视图名称；值是状态代码
		for (Enumeration<?> enumeration = statusCodes.propertyNames(); enumeration.hasMoreElements();) {
			String viewName = (String) enumeration.nextElement();
			Integer statusCode = Integer.valueOf(statusCodes.getProperty(viewName));
			this.statusCodes.put(viewName, statusCode);
		}
	}

	/**
	 * An alternative to {@link #setStatusCodes(Properties)} for use with
	 * Java-based configuration.
	 */
	public void addStatusCode(String viewName, int statusCode) {
		this.statusCodes.put(viewName, statusCode);
	}

	/**
	 * Returns the HTTP status codes provided via {@link #setStatusCodes(Properties)}.
	 * Keys are view names; values are status codes.
	 */
	public Map<String, Integer> getStatusCodesAsMap() {
		return Collections.unmodifiableMap(this.statusCodes);
	}

	/**
	 * Set the default HTTP status code that this exception resolver will apply
	 * if it resolves an error view and if there is no status code mapping defined.
	 * <p>Note that this error code will only get applied in case of a top-level request.
	 * It will not be set for an include request, since the HTTP status cannot be modified
	 * from within an include.
	 * <p>If not specified, no status code will be applied, either leaving this to the
	 * controller or view, or keeping the servlet engine's default of 200 (OK).
	 * @param defaultStatusCode the HTTP status code value, for example 500
	 * ({@link HttpServletResponse#SC_INTERNAL_SERVER_ERROR}) or 404 ({@link HttpServletResponse#SC_NOT_FOUND})
	 * @see #setStatusCodes(Properties)
	 */
	public void setDefaultStatusCode(int defaultStatusCode) {
		this.defaultStatusCode = defaultStatusCode;
	}

	/**
	 * Set the name of the model attribute as which the exception should be exposed.
	 * Default is "exception".
	 * <p>This can be either set to a different attribute name or to {@code null}
	 * for not exposing an exception attribute at all.
	 * @see #DEFAULT_EXCEPTION_ATTRIBUTE
	 */
	public void setExceptionAttribute(@Nullable String exceptionAttribute) {
		this.exceptionAttribute = exceptionAttribute;
	}


	/**
	 * Actually resolve the given exception that got thrown during on handler execution,
	 * returning a ModelAndView that represents a specific error page if appropriate.
	 * <p>May be overridden in subclasses, in order to apply specific exception checks.
	 * Note that this template method will be invoked <i>after</i> checking whether this
	 * resolved applies ("mappedHandlers" etc), so an implementation may simply proceed
	 * with its actual exception handling.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time
	 * of the exception (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding {@code ModelAndView} to forward to,
	 * or {@code null} for default processing in the resolution chain
	 */
	@Override
	@Nullable
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		// Expose ModelAndView for chosen error view.
		// 1. 为选定的错误视图公开 ModelAndView
		// 根据异常类型去exceptionMappings匹配到一个viewName
		// 实在木有匹配到，就用的defaultErrorView（当然defaultErrorView也可能为null没配置，不过建议配置）
		String viewName = determineViewName(ex, request);
		if (viewName != null) {
			// Apply HTTP status code for error views, if specified.
			// Only apply it if we're processing a top-level request.
			// 2. 为错误视图应用 HTTP 状态代码（如果指定）。仅当我们正在处理顶级请求时才应用它
			// 如果匹配上了一个视图后，再去使用视图匹配出一个statusCode
			// 若没匹配上就用defaultStatusCode（当然它也有可能为null）
			Integer statusCode = determineStatusCode(request, viewName);
			if (statusCode != null) {
				//	执行response.setStatus(statusCode)
				applyStatusCodeIfPossible(request, response, statusCode);
			}
			// new ModelAndView(viewName) 设置好viewName
			// 并且，并且，并且：mv.addObject(this.exceptionAttribute, ex)把异常信息放进去。exceptionAttribute的值默认为：exception
			return getModelAndView(viewName, ex, request);
		}
		else {
			return null;
		}
	}

	/**
	 * Determine the view name for the given exception, first checking against the
	 * {@link #setExcludedExceptions(Class[]) "excludedExecptions"}, then searching the
	 * {@link #setExceptionMappings "exceptionMappings"}, and finally using the
	 * {@link #setDefaultErrorView "defaultErrorView"} as a fallback.
	 * @param ex the exception that got thrown during handler execution
	 * @param request current HTTP request (useful for obtaining metadata)
	 * @return the resolved view name, or {@code null} if excluded or none found
	 */
	@Nullable
	protected String determineViewName(Exception ex, HttpServletRequest request) {
		String viewName = null;
		if (this.excludedExceptions != null) {
			for (Class<?> excludedEx : this.excludedExceptions) {
				if (excludedEx.equals(ex.getClass())) {
					return null;
				}
			}
		}
		// Check for specific exception mappings.
		if (this.exceptionMappings != null) {
			viewName = findMatchingViewName(this.exceptionMappings, ex);
		}
		// Return default error view else, if defined.
		if (viewName == null && this.defaultErrorView != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Resolving to default view '" + this.defaultErrorView + "'");
			}
			viewName = this.defaultErrorView;
		}
		return viewName;
	}

	/**
	 * Find a matching view name in the given exception mappings.
	 * @param exceptionMappings mappings between exception class names and error view names
	 * @param ex the exception that got thrown during handler execution
	 * @return the view name, or {@code null} if none found
	 * @see #setExceptionMappings
	 */
	@Nullable
	protected String findMatchingViewName(Properties exceptionMappings, Exception ex) {
		String viewName = null;
		String dominantMapping = null;
		int deepest = Integer.MAX_VALUE;
		for (Enumeration<?> names = exceptionMappings.propertyNames(); names.hasMoreElements();) {
			String exceptionMapping = (String) names.nextElement();
			int depth = getDepth(exceptionMapping, ex);
			if (depth >= 0 && (depth < deepest || (depth == deepest &&
					dominantMapping != null && exceptionMapping.length() > dominantMapping.length()))) {
				deepest = depth;
				dominantMapping = exceptionMapping;
				viewName = exceptionMappings.getProperty(exceptionMapping);
			}
		}
		if (viewName != null && logger.isDebugEnabled()) {
			logger.debug("Resolving to view '" + viewName + "' based on mapping [" + dominantMapping + "]");
		}
		return viewName;
	}

	/**
	 * Return the depth to the superclass matching.
	 * <p>0 means ex matches exactly. Returns -1 if there's no match.
	 * Otherwise, returns depth. Lowest depth wins.
	 */
	protected int getDepth(String exceptionMapping, Exception ex) {
		return getDepth(exceptionMapping, ex.getClass(), 0);
	}

	private int getDepth(String exceptionMapping, Class<?> exceptionClass, int depth) {
		if (exceptionClass.getName().contains(exceptionMapping)) {
			// Found it!
			return depth;
		}
		// If we've gone as far as we can go and haven't found it...
		if (exceptionClass == Throwable.class) {
			return -1;
		}
		return getDepth(exceptionMapping, exceptionClass.getSuperclass(), depth + 1);
	}

	/**
	 * Determine the HTTP status code to apply for the given error view.
	 * <p>The default implementation returns the status code for the given view name (specified through the
	 * {@link #setStatusCodes(Properties) statusCodes} property), or falls back to the
	 * {@link #setDefaultStatusCode defaultStatusCode} if there is no match.
	 * <p>Override this in a custom subclass to customize this behavior.
	 * @param request current HTTP request
	 * @param viewName the name of the error view
	 * @return the HTTP status code to use, or {@code null} for the servlet container's default
	 * (200 in case of a standard error view)
	 * @see #setDefaultStatusCode
	 * @see #applyStatusCodeIfPossible
	 */
	@Nullable
	protected Integer determineStatusCode(HttpServletRequest request, String viewName) {
		if (this.statusCodes.containsKey(viewName)) {
			return this.statusCodes.get(viewName);
		}
		return this.defaultStatusCode;
	}

	/**
	 * Apply the specified HTTP status code to the given response, if possible (that is,
	 * if not executing within an include request).
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param statusCode the status code to apply
	 * @see #determineStatusCode
	 * @see #setDefaultStatusCode
	 * @see HttpServletResponse#setStatus
	 */
	protected void applyStatusCodeIfPossible(HttpServletRequest request, HttpServletResponse response, int statusCode) {
		if (!WebUtils.isIncludeRequest(request)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Applying HTTP status " + statusCode);
			}
			response.setStatus(statusCode);
			request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, statusCode);
		}
	}

	/**
	 * Return a ModelAndView for the given request, view name and exception.
	 * <p>The default implementation delegates to {@link #getModelAndView(String, Exception)}.
	 * @param viewName the name of the error view
	 * @param ex the exception that got thrown during handler execution
	 * @param request current HTTP request (useful for obtaining metadata)
	 * @return the ModelAndView instance
	 */
	protected ModelAndView getModelAndView(String viewName, Exception ex, HttpServletRequest request) {
		return getModelAndView(viewName, ex);
	}

	/**
	 * Return a ModelAndView for the given view name and exception.
	 * <p>The default implementation adds the specified exception attribute.
	 * Can be overridden in subclasses.
	 * @param viewName the name of the error view
	 * @param ex the exception that got thrown during handler execution
	 * @return the ModelAndView instance
	 * @see #setExceptionAttribute
	 */
	protected ModelAndView getModelAndView(String viewName, Exception ex) {
		ModelAndView mv = new ModelAndView(viewName);
		if (this.exceptionAttribute != null) {
			// 将异常放入到modelAndView的属性中
			mv.addObject(this.exceptionAttribute, ex);
		}
		return mv;
	}

}
