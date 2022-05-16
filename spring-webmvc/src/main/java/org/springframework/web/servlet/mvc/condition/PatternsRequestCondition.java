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

package org.springframework.web.servlet.mvc.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * A logical disjunction (' || ') request condition that matches a request
 * against a set of URL path patterns.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class PatternsRequestCondition extends AbstractRequestCondition<PatternsRequestCondition> {
	/*
	 * Spring MVC请求URL带后缀匹配的情况，如/hello.json也能匹配/hello
	 * RequestMappingInfoHandlerMapping 在处理http请求的时候， 如果 请求url 有后缀，如果找不到精确匹配的那个@RequestMapping方法。
	 * 那么，就把后缀去掉，然后.*去匹配，这样，一般都可以匹配，默认这个行为是被开启的。
	 *
	 * 比如有一个@RequestMapping("/rest"), 那么精确匹配的情况下， 只会匹配/rest请求。 但如果我前端发来一个 /rest.abcdef 这样的请求， 又没有配置 @RequestMapping("/rest.abcdef") 这样映射的情况下， 那么@RequestMapping("/rest") 就会生效。
	 *
	 * 这样会带来什么问题呢？绝大多数情况下是没有问题的，但是如果你是一个对权限要求非常严格的系统，强烈关闭此项功能，否则你会有意想不到的"收获"。
	 *
	 * 究其原因咱们可以接着上面的分析，其实就到了PatternsRequestCondition这个类上，具体实现是它的匹配逻辑来决定的。
	 */

	private final static Set<String> EMPTY_PATH_PATTERN = Collections.singleton("");


	private final Set<String> patterns;

	private final UrlPathHelper pathHelper;

	private final PathMatcher pathMatcher;

	private final boolean useSuffixPatternMatch;

	private final boolean useTrailingSlashMatch;

	private final List<String> fileExtensions = new ArrayList<>();


	/**
	 * Creates a new instance with the given URL patterns. Each pattern that is
	 * not empty and does not start with "/" is prepended with "/".
	 * @param patterns 0 or more URL patterns; if 0 the condition will match to
	 * every request.
	 */
	public PatternsRequestCondition(String... patterns) {
		this(patterns, null, null, true, true, null);
	}

	/**
	 * Alternative constructor with additional, optional {@link UrlPathHelper},
	 * {@link PathMatcher}, and whether to automatically match trailing slashes.
	 * @param patterns the URL patterns to use; if 0, the condition will match to every request.
	 * @param urlPathHelper a {@link UrlPathHelper} for determining the lookup path for a request
	 * @param pathMatcher a {@link PathMatcher} for pattern path matching
	 * @param useTrailingSlashMatch whether to match irrespective of a trailing slash
	 * @since 5.2.4
	 */
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useTrailingSlashMatch) {

		this(patterns, urlPathHelper, pathMatcher, false, useTrailingSlashMatch, null);
	}

	/**
	 * Alternative constructor with additional optional parameters.
	 * @param patterns the URL patterns to use; if 0, the condition will match to every request.
	 * @param urlPathHelper for determining the lookup path of a request
	 * @param pathMatcher for path matching with patterns
	 * @param useSuffixPatternMatch whether to enable matching by suffix (".*")
	 * @param useTrailingSlashMatch whether to match irrespective of a trailing slash
	 * @deprecated as of 5.2.4. See class-level note in
	 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
	 * on the deprecation of path extension config options.
	 */
	@Deprecated
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useSuffixPatternMatch, boolean useTrailingSlashMatch) {

		this(patterns, urlPathHelper, pathMatcher, useSuffixPatternMatch, useTrailingSlashMatch, null);
	}

	/**
	 * Alternative constructor with additional optional parameters.
	 * @param patterns the URL patterns to use; if 0, the condition will match to every request.
	 * @param urlPathHelper a {@link UrlPathHelper} for determining the lookup path for a request
	 * @param pathMatcher a {@link PathMatcher} for pattern path matching
	 * @param useSuffixPatternMatch whether to enable matching by suffix (".*")
	 * @param useTrailingSlashMatch whether to match irrespective of a trailing slash
	 * @param fileExtensions a list of file extensions to consider for path matching
	 * @deprecated as of 5.2.4. See class-level note in
	 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
	 * on the deprecation of path extension config options.
	 */
	@Deprecated
	public PatternsRequestCondition(String[] patterns, @Nullable UrlPathHelper urlPathHelper,
			@Nullable PathMatcher pathMatcher, boolean useSuffixPatternMatch,
			boolean useTrailingSlashMatch, @Nullable List<String> fileExtensions) {
		// 常用构造器 == 被RequestMappingInfoBuilder的构造器所使用
		this.patterns = initPatterns(patterns);
		this.pathHelper = urlPathHelper != null ? urlPathHelper : UrlPathHelper.defaultInstance;
		this.pathMatcher = pathMatcher != null ? pathMatcher : new AntPathMatcher();
		this.useSuffixPatternMatch = useSuffixPatternMatch;
		this.useTrailingSlashMatch = useTrailingSlashMatch;

		if (fileExtensions != null) {
			for (String fileExtension : fileExtensions) {
				if (fileExtension.charAt(0) != '.') {
					fileExtension = "." + fileExtension;
				}
				this.fileExtensions.add(fileExtension);
			}
		}
	}

	private static Set<String> initPatterns(String[] patterns) {
		//作用:对patterns进行检查和修正

		// 1. 是否有文本
		if (!hasPattern(patterns)) {
			return EMPTY_PATH_PATTERN;
		}
		// 2. 修正pattern, 对于没有"/"的就需要添加斜线
		Set<String> result = new LinkedHashSet<>(patterns.length);
		for (String pattern : patterns) {
			if (StringUtils.hasLength(pattern) && !pattern.startsWith("/")) {
				pattern = "/" + pattern;
			}
			result.add(pattern);
		}
		// 3. return
		return result;
	}

	private static boolean hasPattern(String[] patterns) {
		if (!ObjectUtils.isEmpty(patterns)) {
			for (String pattern : patterns) {
				if (StringUtils.hasText(pattern)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Private constructor for use when combining and matching.
	 */
	private PatternsRequestCondition(Set<String> patterns, PatternsRequestCondition other) {
		// 构造函数 2
		this.patterns = patterns;
		this.pathHelper = other.pathHelper;
		this.pathMatcher = other.pathMatcher;
		this.useSuffixPatternMatch = other.useSuffixPatternMatch;
		this.useTrailingSlashMatch = other.useTrailingSlashMatch;
		this.fileExtensions.addAll(other.fileExtensions);
	}


	public Set<String> getPatterns() {
		return this.patterns;
	}

	@Override
	protected Collection<String> getContent() {
		return this.patterns;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * Returns a new instance with URL patterns from the current instance ("this") and
	 * the "other" instance as follows:
	 * <ul>
	 * <li>If there are patterns in both instances, combine the patterns in "this" with
	 * the patterns in "other" using {@link PathMatcher#combine(String, String)}.
	 * <li>If only one instance has patterns, use them.
	 * <li>If neither instance has patterns, use an empty String (i.e. "").
	 * </ul>
	 */
	@Override
	public PatternsRequestCondition combine(PatternsRequestCondition other) {
		// 合并另一个 路径匹配条件器

		if (isEmptyPathPattern() && other.isEmptyPathPattern()) {
			return this;
		}
		else if (other.isEmptyPathPattern()) {
			return this;
		}
		else if (isEmptyPathPattern()) {
			return other;
		}
		Set<String> result = new LinkedHashSet<>();
		if (!this.patterns.isEmpty() && !other.patterns.isEmpty()) {
			for (String pattern1 : this.patterns) {
				for (String pattern2 : other.patterns) {
					result.add(this.pathMatcher.combine(pattern1, pattern2));
				}
			}
		}
		return new PatternsRequestCondition(result, this);
	}

	private boolean isEmptyPathPattern() {
		return this.patterns == EMPTY_PATH_PATTERN;
	}

	/**
	 * Checks if any of the patterns match the given request and returns an instance
	 * that is guaranteed to contain matching patterns, sorted via
	 * {@link PathMatcher#getPatternComparator(String)}.
	 * <p>A matching pattern is obtained by making checks in the following order:
	 * <ul>
	 * <li>Direct match
	 * <li>Pattern match with ".*" appended if the pattern doesn't already contain a "."
	 * <li>Pattern match
	 * <li>Pattern match with "/" appended if the pattern doesn't already end in "/"
	 * </ul>
	 * @param request the current request
	 * @return the same instance if the condition contains no patterns;
	 * or a new condition with sorted matching patterns;
	 * or {@code null} if no patterns match.
	 */
	@Override
	@Nullable
	public PatternsRequestCondition getMatchingCondition(HttpServletRequest request) {
		// 1. 获取lookupPath
		String lookupPath = this.pathHelper.getLookupPathForRequest(request, HandlerMapping.LOOKUP_PATH);
		// 2. 核心 -- 获取匹配的Pattern
		List<String> matches = getMatchingPatterns(lookupPath);
		// 3. 如果为empty就返回null,否则new PatternsRequestCondition
		return !matches.isEmpty() ? new PatternsRequestCondition(new LinkedHashSet<>(matches), this) : null;
	}

	/**
	 * Find the patterns matching the given lookup path. Invoking this method should
	 * yield results equivalent to those of calling {@link #getMatchingCondition}.
	 * This method is provided as an alternative to be used if no request is available
	 * (e.g. introspection, tooling, etc).
	 * @param lookupPath the lookup path to match to existing patterns
	 * @return a collection of matching patterns sorted with the closest match at the top
	 */
	public List<String> getMatchingPatterns(String lookupPath) {
		List<String> matches = null;
		// 1. 遍历patterns,查看是否匹配lookupPath,并添加到matches中
		for (String pattern : this.patterns) {
			String match = getMatchingPattern(pattern, lookupPath);
			if (match != null) {
				matches = (matches != null ? matches : new ArrayList<>());
				matches.add(match);
			}
		}
		// 2. 没有任何匹配,返回空集合
		if (matches == null) {
			return Collections.emptyList();
		}
		// 3. 超过多个match,就需要进行排序啦
		// 解释一下为何匹配的可能是多个。因为url匹配上了，但是还有可能@RequestMapping的其余属性匹配不上啊，所以此处需要注意  是可能匹配上多个的  最终是唯一匹配就成~
		// PatternsRequestCondition 就是对Url对匹配的
		if (matches.size() > 1) {
			matches.sort(this.pathMatcher.getPatternComparator(lookupPath));
		}
		return matches;
	}

	// // ===============url的真正匹配规则  非常重要~~~===============
	// 注意这个方法的取名，上面是负数，这里是单数~~~~命名规范也是有艺术感的
	@Nullable
	private String getMatchingPattern(String pattern, String lookupPath) {
		// 核心 - 对每个pattern尝试匹配lookupPath值

		// 1.精准匹配
		if (pattern.equals(lookupPath)) {
			return pattern;
		}
		// 2.使用后缀模式匹配
		// 即如果lookupPath是 /hello.json 可以转换为 /hello 然后去匹配pattern -- 对于高权限系统,建议关闭
		// 这个值默外部给传的true（其实内部默认值是boolean类型为false）
		if (this.useSuffixPatternMatch) {
			// 2.1 fileExtensions默认是空的
			// 这个意思是若useSuffixPatternMatch=true我们支持后缀匹配。我们还可以配置fileExtensions让只支持我们自定义的指定的后缀匹配，而不是下面最终的.*全部支持
			if (!this.fileExtensions.isEmpty() && lookupPath.indexOf('.') != -1) {
				for (String extension : this.fileExtensions) {
					if (this.pathMatcher.match(pattern + extension, lookupPath)) {
						return pattern + extension;
					}
				}
			}
			else {
				// 2.2 直接看pattern是否有"."
				// 若你没有配置指定后缀匹配，并且你的handler也没有.*这样匹配的，那就默认你的pattern就给你添加上后缀".*"，表示匹配所有请求的url的后缀~~~
				boolean hasSuffix = pattern.indexOf('.') != -1;
				if (!hasSuffix && this.pathMatcher.match(pattern + ".*", lookupPath)) {
					return pattern + ".*"; // 返回值加上".*"
				}
			}
		}
		// 3. 直接使用antPathMatcher匹配
		// 若匹配上了 直接返回此patter
		if (this.pathMatcher.match(pattern, lookupPath)) {
			return pattern;
		}
		// 4. 开启使用尾随斜线匹配, 将pattern加上"/"后,尝试进行匹配
		if (this.useTrailingSlashMatch) {
			if (!pattern.endsWith("/") && this.pathMatcher.match(pattern + "/", lookupPath)) {
				return pattern + "/"; // 返回值加上"/"
			}
		}
		return null;
	}

	/**
	 * Compare the two conditions based on the URL patterns they contain.
	 * Patterns are compared one at a time, from top to bottom via
	 * {@link PathMatcher#getPatternComparator(String)}. If all compared
	 * patterns match equally, but one instance has more patterns, it is
	 * considered a closer match.
	 * <p>It is assumed that both instances have been obtained via
	 * {@link #getMatchingCondition(HttpServletRequest)} to ensure they
	 * contain only patterns that match the request and are sorted with
	 * the best matches on top.
	 */
	@Override
	public int compareTo(PatternsRequestCondition other, HttpServletRequest request) {
		String lookupPath = this.pathHelper.getLookupPathForRequest(request, HandlerMapping.LOOKUP_PATH);
		Comparator<String> patternComparator = this.pathMatcher.getPatternComparator(lookupPath);
		Iterator<String> iterator = this.patterns.iterator();
		Iterator<String> iteratorOther = other.patterns.iterator();
		while (iterator.hasNext() && iteratorOther.hasNext()) {
			int result = patternComparator.compare(iterator.next(), iteratorOther.next());
			if (result != 0) {
				return result;
			}
		}
		if (iterator.hasNext()) {
			return -1;
		}
		else if (iteratorOther.hasNext()) {
			return 1;
		}
		else {
			return 0;
		}
	}

}
