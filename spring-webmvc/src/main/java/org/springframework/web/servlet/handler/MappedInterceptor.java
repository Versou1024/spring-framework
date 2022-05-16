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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Contains and delegates calls to a {@link HandlerInterceptor} along with
 * include (and optionally exclude) path patterns to which the interceptor should apply.
 * Also provides matching logic to test if the interceptor applies to a given request path.
 *
 * <p>A MappedInterceptor can be registered directly with any
 * {@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping}.
 * Furthermore, beans of type {@code MappedInterceptor} are automatically detected by
 * {@code AbstractHandlerMethodMapping} (including ancestor ApplicationContext's) which
 * effectively means the interceptor is registered "globally" with all handler mappings.
 *
 * @author Keith Donald
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.0
 */
public final class MappedInterceptor implements HandlerInterceptor {
	/**
	 * 具有映射能力的拦截器：
	 * 1、能够拦截指定范围的Url -- 装饰的额外能力
	 * 2、聚合HandlerInterceptor
	 */

	// 可以看到它哥俩都是可以不用指定，可以为null的
	@Nullable
	private final String[] includePatterns;
	@Nullable
	private final String[] excludePatterns;

	// 持有一个interceptor的引用，类似于目标类~
	private final HandlerInterceptor interceptor;

	// 注意：该类允许你自己指定路径的匹配规则。但是Spring里，不管哪个上层服务，默认使用的都是Ant风格的匹配
	// 并不是正则的匹配  所以效率上还是蛮高的~
	@Nullable
	private PathMatcher pathMatcher;


	/**
	 * Create a new MappedInterceptor instance.
	 * @param includePatterns the path patterns to map (empty for matching to all paths)
	 * @param interceptor the HandlerInterceptor instance to map to the given patterns
	 */
	public MappedInterceptor(@Nullable String[] includePatterns, HandlerInterceptor interceptor) {
		//======构造函数：发现它不仅仅兼容HandlerInterceptor,还可以把WebRequestInterceptor转换成此~
		this(includePatterns, null, interceptor);
	}

	/**
	 * Create a new MappedInterceptor instance.
	 * @param includePatterns the path patterns to map (empty for matching to all paths)
	 * @param excludePatterns the path patterns to exclude (empty for no specific excludes)
	 * @param interceptor the HandlerInterceptor instance to map to the given patterns
	 */
	public MappedInterceptor(@Nullable String[] includePatterns, @Nullable String[] excludePatterns,
			HandlerInterceptor interceptor) {

		this.includePatterns = includePatterns;
		this.excludePatterns = excludePatterns;
		this.interceptor = interceptor;
	}


	/**
	 * Create a new MappedInterceptor instance.
	 * @param includePatterns the path patterns to map (empty for matching to all paths)
	 * @param interceptor the WebRequestInterceptor instance to map to the given patterns
	 */
	public MappedInterceptor(@Nullable String[] includePatterns, WebRequestInterceptor interceptor) {
		this(includePatterns, null, interceptor);
	}

	/**
	 * Create a new MappedInterceptor instance.
	 * @param includePatterns the path patterns to map (empty for matching to all paths)
	 * @param excludePatterns the path patterns to exclude (empty for no specific excludes)
	 * @param interceptor the WebRequestInterceptor instance to map to the given patterns
	 */
	public MappedInterceptor(@Nullable String[] includePatterns, @Nullable String[] excludePatterns,
			WebRequestInterceptor interceptor) {

		this(includePatterns, excludePatterns, new WebRequestHandlerInterceptorAdapter(interceptor));
	}


	/**
	 * Configure a PathMatcher to use with this MappedInterceptor instead of the one passed
	 * by default to the {@link #matches(String, org.springframework.util.PathMatcher)} method.
	 * <p>This is an advanced property that is only required when using custom PathMatcher
	 * implementations that support mapping metadata other than the Ant-style path patterns
	 * supported by default.
	 */
	public void setPathMatcher(@Nullable PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	/**
	 * The configured PathMatcher, or {@code null} if none.
	 */
	@Nullable
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}

	/**
	 * The path into the application the interceptor is mapped to.
	 */
	@Nullable
	public String[] getPathPatterns() {
		return this.includePatterns;
	}

	/**
	 * The actual {@link HandlerInterceptor} reference.
	 */
	public HandlerInterceptor getInterceptor() {
		return this.interceptor;
	}


	/**
	 * Determine a match for the given lookup path.
	 * @param lookupPath the current request path
	 * @param pathMatcher a path matcher for path pattern matching
	 * @return {@code true} if the interceptor applies to the given request path
	 */
	public boolean matches(String lookupPath, PathMatcher pathMatcher) {
		// 原则：excludePatterns先执行，includePatterns后执行
		// 如果excludePatterns执行完都木有匹配的，并且includePatterns是空的，那就返回true（这是个处理方式技巧~  对这种互斥的情况  这一步判断很关键~~~）
		// 核心：是否匹配当前的拦截器
		// 1. 在excludePatterns中，返回false
		// 2. 不在excludePatterns，且includePatterns为空，直接返回true，
		// 3. 不在excludePatterns，但在includePatterns，返回true
		// 4. 不属于上述情况返回null
		PathMatcher pathMatcherToUse = (this.pathMatcher != null ? this.pathMatcher : pathMatcher);
		if (!ObjectUtils.isEmpty(this.excludePatterns)) {
			for (String pattern : this.excludePatterns) {
				if (pathMatcherToUse.match(pattern, lookupPath)) {
					return false;
				}
			}
		}
		if (ObjectUtils.isEmpty(this.includePatterns)) {
			return true;
		}
		for (String pattern : this.includePatterns) {
			if (pathMatcherToUse.match(pattern, lookupPath)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return this.interceptor.preHandle(request, response, handler);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable ModelAndView modelAndView) throws Exception {

		this.interceptor.postHandle(request, response, handler, modelAndView);
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable Exception ex) throws Exception {

		this.interceptor.afterCompletion(request, response, handler, ex);
	}

}
