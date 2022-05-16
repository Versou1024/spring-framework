/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * A generic composite servlet {@link Filter} that just delegates its behavior
 * to a chain (list) of user-supplied filters, achieving the functionality of a
 * {@link FilterChain}, but conveniently using only {@link Filter} instances.
 *
 * <p>This is useful for filters that require dependency injection, and can
 * therefore be set up in a Spring application context. Typically, this
 * composite would be used in conjunction with {@link DelegatingFilterProxy},
 * so that it can be declared in Spring but applied to a servlet context.
 *
 * @author Dave Syer
 * @since 3.1
 */
public class CompositeFilter implements Filter {

	/**
	 * 复合过滤器：聚合Filter，并由外界调用CompositeFilter#doFilter触发内部所有的Filter的执行
	 *
	 * 类似 装饰器模式 + 外观模式 + 过滤器链模式
	 *
	 * 从下面的代码不难看出：
	 * 	1、当执行到CompositeFilter时，如果filters不为空的话，实际上就是去执行VirtualFilterChain，先完成聚合的filters
	 * 	2、然后当filters执行完后，才能够去执行FilterChain
	 *
	 * 	大致如下：
	 * 	|
	 * 	|
	 * 	| -> 假设为CompositeFilter，同时里面有3个Filter，就会如下先执行这三个Fitler
	 * 	  |
	 * 	  |
	 * 	  |
	 * 	|
	 * 	|
	 *
	 */
	private List<? extends Filter> filters = new ArrayList<>();


	public void setFilters(List<? extends Filter> filters) {
		this.filters = new ArrayList<>(filters);
	}


	/**
	 * Initialize all the filters, calling each one's init method in turn in the order supplied.
	 * @see Filter#init(FilterConfig)
	 */
	@Override
	public void init(FilterConfig config) throws ServletException {
		// 触发所有Filters的init
		for (Filter filter : this.filters) {
			filter.init(config);
		}
	}

	/**
	 * Forms a temporary chain from the list of delegate filters supplied ({@link #setFilters})
	 * and executes them in order. Each filter delegates to the next one in the list, achieving
	 * the normal behavior of a {@link FilterChain}, despite the fact that this is a {@link Filter}.
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		new VirtualFilterChain(chain, this.filters).doFilter(request, response);
	}

	/**
	 * Clean up all the filters supplied, calling each one's destroy method in turn, but in reverse order.
	 * @see Filter#init(FilterConfig)
	 */
	@Override
	public void destroy() {
		// 触发filters的销毁操作
		for (int i = this.filters.size(); i-- > 0;) {
			Filter filter = this.filters.get(i);
			filter.destroy();
		}
	}


	private static class VirtualFilterChain implements FilterChain {
		/**
		 * 虚拟的过滤器链：
		 * 由于CompositeFilter的复合作用，其实质的doFilter过程，就是在借鉴FilterChain过滤器链的设计模式
		 *
		 * FilterChain 与 Filter 的 doFilter 区别是 前者没有FilterChain参数，只有request和response两个参数
		 */

		// 原始的过滤器链
		private final FilterChain originalChain;

		// 扩展的过滤器链表
		private final List<? extends Filter> additionalFilters;

		private int currentPosition = 0;

		public VirtualFilterChain(FilterChain chain, List<? extends Filter> additionalFilters) {
			this.originalChain = chain;
			this.additionalFilters = additionalFilters;
		}

		@Override
		public void doFilter(final ServletRequest request, final ServletResponse response)
				throws IOException, ServletException {
			// 首选完成扩展的过滤器链表additionalFilters
			if (this.currentPosition == this.additionalFilters.size()) {
				// 扩展的过滤器执行结束后，就执行原始的过滤器链FilterChain
				this.originalChain.doFilter(request, response);
			}
			else {
				this.currentPosition++;
				Filter nextFilter = this.additionalFilters.get(this.currentPosition - 1);
				nextFilter.doFilter(request, response, this);
			}
		}
	}

}
