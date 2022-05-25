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

package org.springframework.web.servlet.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.PathMatcher;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Abstract base class for {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Supports ordering, a default handler, handler interceptors,
 * including handler interceptors mapped by path patterns.
 *
 * <p>Note: This base class does <i>not</i> support exposure of the
 * {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}. Support for this attribute
 * is up to concrete subclasses, typically based on request URL mappings.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 07.04.2003
 * @see #getHandlerInternal
 * @see #setDefaultHandler
 * @see #setAlwaysUseFullPath
 * @see #setUrlDecode
 * @see org.springframework.util.AntPathMatcher
 * @see #setInterceptors
 * @see org.springframework.web.servlet.HandlerInterceptor
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport implements HandlerMapping, Ordered, BeanNameAware {

	/*
	 * 实现
	 * WebApplicationObjectSupport：
	 * 		提供访问Spring应用上下文和ServletContext的能力
	 * 		主要关注点在WebApplicationObjectSupport的实现类可以initApplicationContext()完成实例化后的初始化操作
	 * HandlerMapping：
	 * 		获取HandlerExecutionChain
	 * Ordered：当前HandlerMapping的排序
	 * BeanNameAware：实现类的BeanName
	 *
	 * 实现类两大分支:
	 * 		AbstractUrlHandlerMapping系列:
	 * 		从命名中也能看出来，它和URL有关。它的大致思路为：
	 * 		将url对应的Handler保存在一个Map中，在getHandlerInternal方法中使用url从Map中获取Handler
	 * 			AbstractDetectingUrlHandlerMapping系列:
	 * 			又是个抽象类，继承自AbstractUrlHandlerMapping。它就越来越具有功能化了：Detecting表明它是有检测URL的功能的~
	 *
	 *
	 *
	 * 聚合：
	 * defaultHandler、urlPathHelper用于截取url的资源路径、interceptors扩展的拦截器、adaptedInterceptors所有的拦截器
	 * corsConfigurationSource/corsProcessor跨域配置、当前BeanName
	 */

	// 默认Handler,该类的实现类可以注册默认的Handler,在request无法映射到某个Handler时,会使用默认的Handler进行处理,起一个兜底的作用
	// 主要是在 本类的 getHandler() 方法中被使用
	@Nullable
	private Object defaultHandler;

	// url路径帮助器 - 能够从request中解析提取路径
	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	// 路径匹配器 -- 主要是将路径和模式匹配,对于url常见是的 /com/{xylink}
	private PathMatcher pathMatcher = new AntPathMatcher();

	// 该类的实现类可以向这个interceptors中提前注册拦截器,后续会在加入到adaptedInterceptors
	private final List<Object> interceptors = new ArrayList<>();

	// 最终持有的拦截器
	private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();

	@Nullable
	private CorsConfigurationSource corsConfigurationSource;

	// 跨域处理器 -- 使用默认的跨域处理器
	private CorsProcessor corsProcessor = new DefaultCorsProcessor();

	// 在HandlerMapping优先级最低
	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	// 当前实现类的beanName
	@Nullable
	private String beanName;


	/**
	 * Set the default handler for this handler mapping.
	 * This handler will be returned if no specific mapping was found.
	 * <p>Default is {@code null}, indicating no default handler.
	 */
	public void setDefaultHandler(@Nullable Object defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	/**
	 * Return the default handler for this handler mapping,
	 * or {@code null} if none.
	 */
	@Nullable
	public Object getDefaultHandler() {
		return this.defaultHandler;
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath(boolean)
	 */
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setAlwaysUseFullPath(alwaysUseFullPath);
		}
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setUrlDecode(boolean)
	 */
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlDecode(urlDecode);
		}
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setRemoveSemicolonContent(boolean)
	 */
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setRemoveSemicolonContent(removeSemicolonContent);
		}
	}

	/**
	 * Set the UrlPathHelper to use for resolution of lookup paths.
	 * <p>Use this to override the default UrlPathHelper with a custom subclass,
	 * or to share common UrlPathHelper settings across multiple HandlerMappings
	 * and MethodNameResolvers.
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setUrlPathHelper(urlPathHelper);
		}
	}

	/**
	 * Return the UrlPathHelper implementation to use for resolution of lookup paths.
	 */
	public UrlPathHelper getUrlPathHelper() {
		return this.urlPathHelper;
	}

	/**
	 * Set the PathMatcher implementation to use for matching URL paths
	 * against registered URL patterns. Default is AntPathMatcher.
	 * @see org.springframework.util.AntPathMatcher
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
		if (this.corsConfigurationSource instanceof UrlBasedCorsConfigurationSource) {
			((UrlBasedCorsConfigurationSource) this.corsConfigurationSource).setPathMatcher(pathMatcher);
		}
	}

	/**
	 * Return the PathMatcher implementation to use for matching URL paths
	 * against registered URL patterns.
	 */
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}

	/**
	 * Set the interceptors to apply for all handlers mapped by this handler mapping.
	 * <p>Supported interceptor types are HandlerInterceptor, WebRequestInterceptor, and MappedInterceptor.
	 * Mapped interceptors apply only to request URLs that match its path patterns.
	 * Mapped interceptor beans are also detected by type during initialization.
	 * @param interceptors array of handler interceptors
	 * @see #adaptInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 */
	public void setInterceptors(Object... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
	}

	/**
	 * Set the "global" CORS configurations based on URL patterns. By default the first
	 * matching URL pattern is combined with the CORS configuration for the handler, if any.
	 * @since 4.2
	 * @see #setCorsConfigurationSource(CorsConfigurationSource)
	 */
	public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
		Assert.notNull(corsConfigurations, "corsConfigurations must not be null");
		if (!corsConfigurations.isEmpty()) {
			UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
			source.setCorsConfigurations(corsConfigurations);
			source.setPathMatcher(this.pathMatcher);
			source.setUrlPathHelper(this.urlPathHelper);
			source.setLookupPathAttributeName(LOOKUP_PATH);
			this.corsConfigurationSource = source;
		}
		else {
			this.corsConfigurationSource = null;
		}
	}

	/**
	 * Set the "global" CORS configuration source. By default the first matching URL
	 * pattern is combined with the CORS configuration for the handler, if any.
	 * @since 5.1
	 * @see #setCorsConfigurations(Map)
	 */
	public void setCorsConfigurationSource(CorsConfigurationSource corsConfigurationSource) {
		Assert.notNull(corsConfigurationSource, "corsConfigurationSource must not be null");
		this.corsConfigurationSource = corsConfigurationSource;
	}

	/**
	 * Configure a custom {@link CorsProcessor} to use to apply the matched
	 * {@link CorsConfiguration} for a request.
	 * <p>By default {@link DefaultCorsProcessor} is used.
	 * @since 4.2
	 */
	public void setCorsProcessor(CorsProcessor corsProcessor) {
		Assert.notNull(corsProcessor, "CorsProcessor must not be null");
		this.corsProcessor = corsProcessor;
	}

	/**
	 * Return the configured {@link CorsProcessor}.
	 */
	public CorsProcessor getCorsProcessor() {
		return this.corsProcessor;
	}

	/**
	 * Specify the order value for this HandlerMapping bean.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	protected String formatMappingName() {
		return this.beanName != null ? "'" + this.beanName + "'" : "<unknown>";
	}


	/**
	 * Initializes the interceptors.
	 * @see #extendInterceptors(java.util.List)
	 * @see #initInterceptors()
	 */
	@Override
	protected void initApplicationContext() throws BeansException {
		// ❗️核心方法  - 相当于父类setApplicationContext完成了之后，就会执行到这里~~~

		// 1. 用于子类扩展拦截器，也可以提前通过 setInterceptors(Object...) -- 用户扩展的拦截器放入interceptors
		extendInterceptors(this.interceptors);
		// 2. 用于IOC容器查找MappedInterceptor.class的Bean注入 -- 可使用的拦截器放入 adaptedInterceptors
		detectMappedInterceptors(this.adaptedInterceptors);
		// 3. 将扩展的拦截器interceptors合并到adaptedInterceptors
		initInterceptors();
	}

	/**
	 * Extension hook that subclasses can override to register additional interceptors,
	 * given the configured interceptors (see {@link #setInterceptors}).
	 * <p>Will be invoked before {@link #initInterceptors()} adapts the specified
	 * interceptors into {@link HandlerInterceptor} instances.
	 * <p>The default implementation is empty.
	 * @param interceptors the configured interceptor List (never {@code null}), allowing
	 * to add further interceptors before as well as after the existing interceptors
	 */
	protected void extendInterceptors(List<Object> interceptors) {
		// 空方法体
	}

	/**
	 * Detect beans of type {@link MappedInterceptor} and add them to the list
	 * of mapped interceptors.
	 * <p>This is called in addition to any {@link MappedInterceptor}s that may
	 * have been provided via {@link #setInterceptors}, by default adding all
	 * beans of type {@link MappedInterceptor} from the current context and its
	 * ancestors. Subclasses can override and refine this policy.
	 * @param mappedInterceptors an empty list to add to
	 */
	protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
		// 去容器（含祖孙容器）内找到所有的MappedInterceptor类型的拦截器出来，添加进去到 mappedInterceptors
		// 非单例的Bean也包含
		// 备注MappedInterceptor为Spring MVC拦截器接口`HandlerInterceptor`的实现类  并且是个final类 Spring3.0后出来的。
		mappedInterceptors.addAll(BeanFactoryUtils.beansOfTypeIncludingAncestors(obtainApplicationContext(), MappedInterceptor.class, true, false).values());
	}

	/**
	 * Initialize the specified interceptors adapting
	 * {@link WebRequestInterceptor}s to {@link HandlerInterceptor}.
	 * @see #setInterceptors
	 * @see #adaptInterceptor
	 */
	protected void initInterceptors() {
		// 1. 它就是把调用者放进来的interceptors们，适配成HandlerInterceptor然后统一放在`adaptedInterceptors`里面装着~~~
		//	protected void initInterceptors() {
		if (!this.interceptors.isEmpty()) {
			for (int i = 0; i < this.interceptors.size(); i++) {
				Object interceptor = this.interceptors.get(i);
				if (interceptor == null) {
					throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null");
				}
				this.adaptedInterceptors.add(adaptInterceptor(interceptor));
			}
		}
	}

	/**
	 * Adapt the given interceptor object to {@link HandlerInterceptor}.
	 * <p>By default, the supported interceptor types are
	 * {@link HandlerInterceptor} and {@link WebRequestInterceptor}. Each given
	 * {@link WebRequestInterceptor} is wrapped with
	 * {@link WebRequestHandlerInterceptorAdapter}.
	 * @param interceptor the interceptor
	 * @return the interceptor downcast or adapted to HandlerInterceptor
	 * @see org.springframework.web.servlet.HandlerInterceptor
	 * @see org.springframework.web.context.request.WebRequestInterceptor
	 * @see WebRequestHandlerInterceptorAdapter
	 */
	protected HandlerInterceptor adaptInterceptor(Object interceptor) {
		// 适配其实也很简单~就是支持源生的HandlerInterceptor以及WebRequestInterceptor两种情况而已

		if (interceptor instanceof HandlerInterceptor) {
			return (HandlerInterceptor) interceptor;
		}
		else if (interceptor instanceof WebRequestInterceptor) {
			// WebRequestHandlerInterceptorAdapter它就是个`HandlerInterceptor`，内部持有一个WebRequestInterceptor的引用而已
			// 内部使用到了DispatcherServletWebRequest包request和response包装成`WebRequest`等等
			return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
		}
		else {
			throw new IllegalArgumentException("Interceptor type not supported: " + interceptor.getClass().getName());
		}
	}

	/**
	 * Return the adapted interceptors as {@link HandlerInterceptor} array.
	 * @return the array of {@link HandlerInterceptor HandlerInterceptor}s,
	 * or {@code null} if none
	 */
	@Nullable
	protected final HandlerInterceptor[] getAdaptedInterceptors() {
		return (!this.adaptedInterceptors.isEmpty() ? this.adaptedInterceptors.toArray(new HandlerInterceptor[0]) : null);
	}

	/**
	 * Return all configured {@link MappedInterceptor}s as an array.
	 * @return the array of {@link MappedInterceptor}s, or {@code null} if none
	 */
	@Nullable
	protected final MappedInterceptor[] getMappedInterceptors() {
		// 它只会返回MappedInterceptor这种类型的，上面getAdaptedInterceptors()是返回adaptedInterceptors所有
		List<MappedInterceptor> mappedInterceptors = new ArrayList<>(this.adaptedInterceptors.size());
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				mappedInterceptors.add((MappedInterceptor) interceptor);
			}
		}
		return (!mappedInterceptors.isEmpty() ? mappedInterceptors.toArray(new MappedInterceptor[0]) : null);
	}


	/**
	 * Look up a handler for the given request, falling back to the default
	 * handler if no specific one is found.
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or the default handler
	 * @see #getHandlerInternal
	 */
	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		/**
		 * ❗️核心1.0 ：
		 * 	重点：getHandlerInternal()、getHandlerExecutionChain()
		 * 	这个方法也是一个该抽象类提供的一个非常重要的模版方法：根据request获取到一个HandlerExecutionChain
		 * 	也是抽象类实现接口HandlerMapping的方法~~~
		 */

		// 1. 【抽象】交给子类实现,根据request获取一个handler
		Object handler = getHandlerInternal(request);
		// 2.  无法根据request获取handler时,就获取默认的Handler -- 需要子类提供defaultHandler,默认是返回的一个null
		if (handler == null) {
			handler = getDefaultHandler();
		}
		// 3. 没有提供默认的handler,也无法从request决定使用哪一个handler,直接返回null
		// 由于有多个HandlerMapping,我不行的话,就返回null,后面的HandlerMapping继续尝试吧
		if (handler == null) {
			return null;
		}
		//4. 我这个HandlerMapping找到了request对应的Handler
		// 但handler是String，String的话需要额外通过applicationContext进行加载
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}

		// ❗️核心2.0: 获取 HandlerExecutionChain

		// 5. 有了handler\request\interceptors那就开始准备构造HandlerExecutionChain吧
		HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);

		if (logger.isTraceEnabled()) {
			logger.trace("Mapped to " + handler);
		}
		else if (logger.isDebugEnabled() && !request.getDispatcherType().equals(DispatcherType.ASYNC)) {
			logger.debug("Mapped to " + executionChain.getHandler());
		}

		// 6. 做一个跨域处理 -- 检查当前Handler是否有跨域cors配置即实现CorsConfigurationSource，或者当前请求为预检请求
		// hasCorsConfigurationSource(handler) = 当前handler本身实现了CorsConfigurationSource接口/或者,提前配置了corsConfigurationSource
		// CorsUtils.isPreFlightRequest(request) = 或者请求是预检请求
		if (hasCorsConfigurationSource(handler) || CorsUtils.isPreFlightRequest(request)) {
			// 6.1 全局配置 -- 从当前HandlerMapping设置的跨域配置来源中获取为当前请求匹配的跨域配置
			CorsConfiguration config = (this.corsConfigurationSource != null ? this.corsConfigurationSource.getCorsConfiguration(request) : null);
			// 6.2 局部配置 -- 从handler本身实现了CorsConfigurationSource接口时,就从handler中尝试获取这个request的跨域配置
			// 从handler自己里找：若handler自己实现了CorsConfigurationSource接口，那就从自己这哪呗
			// 说明：此种方式适用于一个类就是一个处理器的case。比如servlet处理器
			// 所以对于@RequestMapping情况，这个值大部分情况都是null
			CorsConfiguration handlerConfig = getCorsConfiguration(handler, request);
			// 6.3 把全局配置和handler配置combine组合合并
			config = (config != null ? config.combine(handlerConfig) : handlerConfig);
			// 6.4 这个方法很重要。请看下面这个方法
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}

		// 7. 返回Handler
		return executionChain;
	}

	/**
	 * Look up a handler for the given request, returning {@code null} if no
	 * specific one is found. This method is called by {@link #getHandler};
	 * a {@code null} return value will lead to the default handler, if one is set.
	 * <p>On CORS pre-flight requests this method should return a match not for
	 * the pre-flight request but for the expected actual request based on the URL
	 * path, the HTTP methods from the "Access-Control-Request-Method" header, and
	 * the headers from the "Access-Control-Request-Headers" header thus allowing
	 * the CORS configuration to be obtained via {@link #getCorsConfiguration(Object, HttpServletRequest)},
	 * <p>Note: This method may also return a pre-built {@link HandlerExecutionChain},
	 * combining a handler object with dynamically determined interceptors.
	 * Statically specified interceptors will get merged into such an existing chain.
	 * @param request current HTTP request
	 * @return the corresponding handler instance, or {@code null} if none found
	 * @throws Exception if there is an internal error
	 */
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

	/**
	 * Build a {@link HandlerExecutionChain} for the given handler, including
	 * applicable interceptors.
	 * <p>The default implementation builds a standard {@link HandlerExecutionChain}
	 * with the given handler, the common interceptors of the handler mapping, and any
	 * {@link MappedInterceptor MappedInterceptors} matching to the current request URL. Interceptors
	 * are added in the order they were registered. Subclasses may override this
	 * in order to extend/rearrange the list of interceptors.
	 * <p><b>NOTE:</b> The passed-in handler object may be a raw handler or a
	 * pre-built {@link HandlerExecutionChain}. This method should handle those
	 * two cases explicitly, either building a new {@link HandlerExecutionChain}
	 * or extending the existing chain.
	 * <p>For simply adding an interceptor in a custom subclass, consider calling
	 * {@code super.getHandlerExecutionChain(handler, request)} and invoking
	 * {@link HandlerExecutionChain#addInterceptor} on the returned chain object.
	 * @param handler the resolved handler instance (never {@code null})
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain (never {@code null})
	 * @see #getAdaptedInterceptors()
	 */
	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		// 核心：由于超类ApplicationObjectSupport implements ApplicationContextAware 实现了感知接口
		// 同时在感知接口中，提供了initApplicationContext()，当前类就在初始化ApplicationContext中完成对拦截器的加载
		// 因此有了 拦截器\request\handler 就已经可以构造执行器链

		// 1. new 一个 HandlerExecutionChain
		HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ? (HandlerExecutionChain) handler : new HandlerExecutionChain(handler));
		// 2. 获取request的查找路径 -- 后续的MappingInterceptor是有映射到某个URL上的能力
		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request, LOOKUP_PATH);
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			// 3. 映射拦截器MappedInterceptor在拦截器上的基础上提供额外的映射能力，只有指定的Url才会允许被拿添加
			if (interceptor instanceof MappedInterceptor) {
				MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
				// 调用 mappedInterceptor#maches 方法检查是否匹配
				if (mappedInterceptor.matches(lookupPath, this.pathMatcher)) {
					chain.addInterceptor(mappedInterceptor.getInterceptor());
				}
			} else {
				// 4. 普通拦截器直接添加到执行链中
				chain.addInterceptor(interceptor);
			}
		}
		return chain;
	}

	/**
	 * Return {@code true} if there is a {@link CorsConfigurationSource} for this handler.
	 * @since 5.2
	 */
	protected boolean hasCorsConfigurationSource(Object handler) {
		// 1. 当前handler本身实现了CorsConfigurationSource接口
		// 2. 或者,提前配置了corsConfigurationSource

		if (handler instanceof HandlerExecutionChain) {
			handler = ((HandlerExecutionChain) handler).getHandler();
		}
		return (handler instanceof CorsConfigurationSource || this.corsConfigurationSource != null);
	}

	/**
	 * Retrieve the CORS configuration for the given handler.
	 * @param handler the handler to check (never {@code null}).
	 * @param request the current request.
	 * @return the CORS configuration for the handler, or {@code null} if none
	 * @since 4.2
	 */
	@Nullable
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		Object resolvedHandler = handler;
		// 1. handler是HandlerExecutionChain,需要拿出实际的handler
		if (handler instanceof HandlerExecutionChain) {
			resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
		}
		// 2. 如果handler同时是实现了CorsConfigurationSource,就从中获取属性源
		if (resolvedHandler instanceof CorsConfigurationSource) {
			return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
		}
		// 3. 返回null
		return null;
	}

	/**
	 * Update the HandlerExecutionChain for CORS-related handling.
	 * <p>For pre-flight requests, the default implementation replaces the selected
	 * handler with a simple HttpRequestHandler that invokes the configured
	 * {@link #setCorsProcessor}.
	 * <p>For actual requests, the default implementation inserts a
	 * HandlerInterceptor that makes CORS-related checks and adds CORS headers.
	 * @param request the current request
	 * @param chain the handler chain
	 * @param config the applicable CORS configuration (possibly {@code null})
	 * @since 4.2
	 */
	protected HandlerExecutionChain getCorsHandlerExecutionChain(HttpServletRequest request,
			HandlerExecutionChain chain, @Nullable CorsConfiguration config) {

		// 1. 再次检查是否为预检请求
		// 若是预检请求：就new一个新的HandlerExecutionChain。
		// PreFlightHandler是一个HttpRequestHandler哦~~~并且实现了接口CorsConfigurationSource
		if (CorsUtils.isPreFlightRequest(request)) {
			// 1.1 预检请求
			// 需要重新给定Handler,即PreFlightHandler,当然拦截器还是不变interceptors
			// 因此若是预检请求：针对此请求会直接new一个PreFlightHandler作为HttpRequestHandler处理器来处理它，而不再是交给匹配上的Handler去处理（这点特别的重要）
			//- PreFlightHandler#handle方法委托给了corsProcessor去处理跨域请求头、响应头的
			//- 值得注意的是：此时即使原Handler它不执行了，但匹配上的HandlerInterceptor们仍都还是会生效执行作用在OPTIONS方法上的
			HandlerInterceptor[] interceptors = chain.getInterceptors();
			return new HandlerExecutionChain(new PreFlightHandler(config), interceptors);
		}
		// 2. 否则就是普通请求,且这个普通请求是在全局/局部都有一个CorsConfiguration跨域配置
		// 添加一个预检请求的拦截器,以让跨域配置生效
		// 添加的拦截器就是 CorsInterceptor
		// 若是简单请求/真实请求：在原来的处理链上加一个拦截器chain.addInterceptor(new CorsInterceptor(config))，
		// 由这个拦截器它最终复杂来处理相关逻辑（全权委托给corsProcessor）
		else {
			chain.addInterceptor(0, new CorsInterceptor(config));
			return chain;
		}
	}


	private class PreFlightHandler implements HttpRequestHandler, CorsConfigurationSource {
		// 这个和上面的CorsInterceptor互斥，它最终也是委托给corsProcessor来处理请求，只是它是专门用于处理预检请求的。详见CORS请求处理流程部分。
		// 持有一个 CorsConfiguration 用于给预检请求做请求处理

		@Nullable
		private final CorsConfiguration config;

		public PreFlightHandler(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}


	private class CorsInterceptor extends HandlerInterceptorAdapter implements CorsConfigurationSource {
		// Cors拦截器。它最终会被放到处理器链HandlerExecutionChain里，用于拦截处理（最后一个拦截）。
		// 持有一个 CorsConfiguration 用于在请求之前做跨域配置

		@Nullable
		private final CorsConfiguration config;

		public CorsInterceptor(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
				throws Exception {

			// Consistent with CorsFilter, ignore ASYNC dispatches
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			if (asyncManager.hasConcurrentResult()) {
				return true;
			}

			return corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}

}
