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

package org.springframework.web.servlet.view;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Convenient base class for {@link org.springframework.web.servlet.ViewResolver}
 * implementations. Caches {@link org.springframework.web.servlet.View} objects
 * once resolved: This means that view resolution won't be a performance problem,
 * no matter how costly initial view retrieval is.
 *
 * <p>Subclasses need to implement the {@link #loadView} template method,
 * building the View object for a specific view name and locale.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #loadView
 */
public abstract class AbstractCachingViewResolver extends WebApplicationObjectSupport implements ViewResolver {
	/**
	 * ViewResolver的基本抽象类
	 * 主要作用：
	 * 1、在解析后缓存视图对象：这意味着视图解析不会成为性能问题，无论初始视图检索的成本有多高。
	 * 2、子类需要实现loadView模板方法，为特定的视图名称和区域设置构建视图对象。
	 */

	/** Default maximum number of entries for the view cache: 1024. */
	// 最大缓存view的数量
	public static final int DEFAULT_CACHE_LIMIT = 1024;

	/** Dummy marker object for unresolved views in the cache Maps. */
	private static final View UNRESOLVED_VIEW = new View() {// 用于标记未解析的View
		@Override
		@Nullable
		public String getContentType() {
			return null;
		}
		@Override
		public void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
		}
	};

	/** Default cache filter that always caches. */
	private static final CacheFilter DEFAULT_CACHE_FILTER = (view, viewName, locale) -> true; // 解析完后是否需要缓存，取决于该过滤器，默认是返回true


	/** The maximum number of entries in the cache. */
	private volatile int cacheLimit = DEFAULT_CACHE_LIMIT;
	// 经典的： 一个static final变量作为默认值，同时允许用户修改，应该另起一个非static final的，初始化等于static final变量
	// 适合场景：
	// 1、初始化时有默认值，后续允许用户修改，因此具备拆分为一个static final和一个非static final的变量
	// 2、为什么不直接 private CacheFilter cacheFilter = (view, viewName, locale) -> true;
	//   原因：由于cacheFilter不是共享的，因此如果当前类不是单例模式的，那么每次创建都会创建一个(view, viewName, locale) -> true的lambda表达式；
	// 		创建一百个该对象，就有一百个lambda表达式，而采用默认方式能够帮助减少创建的数量的

	/** Whether we should refrain from resolving views again if unresolved once. */
	private boolean cacheUnresolved = true;

	/** Filter function that determines if view should be cached. */
	private CacheFilter cacheFilter = DEFAULT_CACHE_FILTER;

	/** Fast access cache for Views, returning already cached instances without a global lock. */
	private final Map<Object, View> viewAccessCache = new ConcurrentHashMap<>(DEFAULT_CACHE_LIMIT);

	/** Map from view key to View instance, synchronized for View creation. */
	@SuppressWarnings("serial")
	private final Map<Object, View> viewCreationCache =
			new LinkedHashMap<Object, View>(DEFAULT_CACHE_LIMIT, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Object, View> eldest) {
					if (size() > getCacheLimit()) {
						viewAccessCache.remove(eldest.getKey());
						return true;
					}
					else {
						return false;
					}
				}
			};


	/**
	 * Specify the maximum number of entries for the view cache.
	 * Default is 1024.
	 */
	public void setCacheLimit(int cacheLimit) {
		this.cacheLimit = cacheLimit;
	}

	/**
	 * Return the maximum number of entries for the view cache.
	 */
	public int getCacheLimit() {
		return this.cacheLimit;
	}

	/**
	 * Enable or disable caching.
	 * <p>This is equivalent to setting the {@link #setCacheLimit "cacheLimit"}
	 * property to the default limit (1024) or to 0, respectively.
	 * <p>Default is "true": caching is enabled.
	 * Disable this only for debugging and development.
	 */
	public void setCache(boolean cache) {
		this.cacheLimit = (cache ? DEFAULT_CACHE_LIMIT : 0);
	}

	/**
	 * Return if caching is enabled.
	 */
	public boolean isCache() {
		return (this.cacheLimit > 0);
	}

	/**
	 * Whether a view name once resolved to {@code null} should be cached and
	 * automatically resolved to {@code null} subsequently.
	 * <p>Default is "true": unresolved view names are being cached, as of Spring 3.1.
	 * Note that this flag only applies if the general {@link #setCache "cache"}
	 * flag is kept at its default of "true" as well.
	 * <p>Of specific interest is the ability for some AbstractUrlBasedView
	 * implementations (FreeMarker, Tiles) to check if an underlying resource
	 * exists via {@link AbstractUrlBasedView#checkResource(Locale)}.
	 * With this flag set to "false", an underlying resource that re-appears
	 * is noticed and used. With the flag set to "true", one check is made only.
	 */
	public void setCacheUnresolved(boolean cacheUnresolved) {
		this.cacheUnresolved = cacheUnresolved;
	}

	/**
	 * Return if caching of unresolved views is enabled.
	 */
	public boolean isCacheUnresolved() {
		return this.cacheUnresolved;
	}

	/**
	 * Sets the filter that determines if view should be cached.
	 * Default behaviour is to cache all views.
	 * @since 5.2
	 */
	public void setCacheFilter(CacheFilter cacheFilter) {
		Assert.notNull(cacheFilter, "CacheFilter must not be null");
		this.cacheFilter = cacheFilter;
	}

	/**
	 * Return filter function that determines if view should be cached.
	 * @since 5.2
	 */
	public CacheFilter getCacheFilter() {
		return this.cacheFilter;
	}

	@Override
	@Nullable
	public View resolveViewName(String viewName, Locale locale) throws Exception {
		// 核心方法
		// 在 AbstractCachingViewResolver 中实现就是一个模板方法,主要是将缓存功能抽取出来呗

		if (!isCache()) {
			// 1. 不开启缓存，直接创建view
			return createView(viewName, locale);
		}
		else {
			// 2. 唯一的缓存key
			Object cacheKey = getCacheKey(viewName, locale);
			View view = this.viewAccessCache.get(cacheKey);
			if (view == null) {
				// 2.1 缓存未命中
				synchronized (this.viewCreationCache) {
					// 2.1.1 双重检查
					view = this.viewCreationCache.get(cacheKey);
					if (view == null) {
						// Ask the subclass to create the View object.
						// 2.1.2 子类createView
						view = createView(viewName, locale);
						// 2.2.2 子类无法解析,且需要缓存无法解析的逻辑视图名String[cacheUnresolved 开关是否开启]
						if (view == null && this.cacheUnresolved) {
							view = UNRESOLVED_VIEW;
						}
						// 2.2.3 子类可以根据该逻辑视图名viewName解析处view
						// 且满足缓存条件 -- 默认的cacheFilter永远返回true
						// 就加入到缓存中 viewAccessCache\ viewCreationCache
						if (view != null && this.cacheFilter.filter(view, viewName, locale)) {
							this.viewAccessCache.put(cacheKey, view);
							this.viewCreationCache.put(cacheKey, view);
						}
					}
				}
			}
			else {
				// 2.2 缓存命中
				if (logger.isTraceEnabled()) {
					logger.trace(formatKey(cacheKey) + "served from cache");
				}
			}
			// 3. 返回解析出的View
			// 这个很重要，因为没有被解析过  都会返回null
			// 而再真正责任链处理的时候，第一个不返回null的view，最终就会被返回了~~~
			return (view != UNRESOLVED_VIEW ? view : null);
		}
	}

	private static String formatKey(Object cacheKey) {
		return "View with key [" + cacheKey + "] ";
	}

	/**
	 * Return the cache key for the given view name and the given locale.
	 * <p>Default is a String consisting of view name and locale suffix.
	 * Can be overridden in subclasses.
	 * <p>Needs to respect the locale in general, as a different locale can
	 * lead to a different view resource.
	 */
	protected Object getCacheKey(String viewName, Locale locale) {
		return viewName + '_' + locale;
	}

	/**
	 * Provides functionality to clear the cache for a certain view.
	 * <p>This can be handy in case developer are able to modify views
	 * (e.g. FreeMarker templates) at runtime after which you'd need to
	 * clear the cache for the specified view.
	 * @param viewName the view name for which the cached view object
	 * (if any) needs to be removed
	 * @param locale the locale for which the view object should be removed
	 */
	public void removeFromCache(String viewName, Locale locale) {
		if (!isCache()) {
			logger.warn("Caching is OFF (removal not necessary)");
		}
		else {
			Object cacheKey = getCacheKey(viewName, locale);
			Object cachedView;
			synchronized (this.viewCreationCache) {
				this.viewAccessCache.remove(cacheKey);
				cachedView = this.viewCreationCache.remove(cacheKey);
			}
			if (logger.isDebugEnabled()) {
				// Some debug output might be useful...
				logger.debug(formatKey(cacheKey) +
						(cachedView != null ? "cleared from cache" : "not found in the cache"));
			}
		}
	}

	/**
	 * Clear the entire view cache, removing all cached view objects.
	 * Subsequent resolve calls will lead to recreation of demanded view objects.
	 */
	public void clearCache() {
		logger.debug("Clearing all views from the cache");
		synchronized (this.viewCreationCache) {
			this.viewAccessCache.clear();
			this.viewCreationCache.clear();
		}
	}


	/**
	 * Create the actual View object.
	 * <p>The default implementation delegates to {@link #loadView}.
	 * This can be overridden to resolve certain view names in a special fashion,
	 * before delegating to the actual {@code loadView} implementation
	 * provided by the subclass.
	 * @param viewName the name of the view to retrieve
	 * @param locale the Locale to retrieve the view for
	 * @return the View instance, or {@code null} if not found
	 * (optional, to allow for ViewResolver chaining)
	 * @throws Exception if the view couldn't be resolved
	 * @see #loadView
	 */
	@Nullable
	protected View createView(String viewName, Locale locale) throws Exception {
		// 为什么多此一举，将职责委托为loadView
		// 创建实际的视图对象。
		// 默认实现将委托给loadView。在委托给子类提供的实际loadView实现之前，可以重写它以特殊方式解析某些视图名称。
		return loadView(viewName, locale);
	}

	/**
	 * Subclasses must implement this method, building a View object
	 * for the specified view. The returned View objects will be
	 * cached by this ViewResolver base class.
	 * <p>Subclasses are not forced to support internationalization:
	 * A subclass that does not may simply ignore the locale parameter.
	 * @param viewName the name of the view to retrieve
	 * @param locale the Locale to retrieve the view for
	 * @return the View instance, or {@code null} if not found
	 * (optional, to allow for ViewResolver chaining)
	 * @throws Exception if the view couldn't be resolved
	 * @see #resolveViewName
	 */
	@Nullable
	protected abstract View loadView(String viewName, Locale locale) throws Exception;


	/**
	 * Filter that determines if view should be cached.
	 *
	 * @author Sergey Galkin
	 * @author Arjen Poutsma
	 * @since 5.2
	 */
	@FunctionalInterface
	public interface CacheFilter {
		// 函数式接口，可以写成内部接口
		/**
		 * Indicates whether the given view should be cached.
		 * The name and locale used to resolve the view are also provided.
		 * @param view the view
		 * @param viewName the name used to resolve the {@code view}
		 * @param locale the locale used to resolve the {@code view}
		 * @return {@code true} if the view should be cached; {@code false} otherwise
		 */
		boolean filter(View view, String viewName, Locale locale);
	}

}
