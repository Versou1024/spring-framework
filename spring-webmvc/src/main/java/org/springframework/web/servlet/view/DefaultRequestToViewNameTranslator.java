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

import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.RequestToViewNameTranslator;
import org.springframework.web.util.UrlPathHelper;

/**
 * {@link RequestToViewNameTranslator} that simply transforms the URI of
 * the incoming request into a view name.
 *
 * <p>Can be explicitly defined as the {@code viewNameTranslator} bean in a
 * {@link org.springframework.web.servlet.DispatcherServlet} context.
 * Otherwise, a plain default instance will be used.
 *
 * <p>The default transformation simply strips leading and trailing slashes
 * as well as the file extension of the URI, and returns the result as the
 * view name with the configured {@link #setPrefix prefix} and a
 * {@link #setSuffix suffix} added as appropriate.
 *
 * <p>The stripping of the leading slash and file extension can be disabled
 * using the {@link #setStripLeadingSlash stripLeadingSlash} and
 * {@link #setStripExtension stripExtension} properties, respectively.
 *
 * <p>Find below some examples of request to view name translation.
 * <ul>
 * <li>{@code http://localhost:8080/gamecast/display.html} &raquo; {@code display}</li>
 * <li>{@code http://localhost:8080/gamecast/displayShoppingCart.html} &raquo; {@code displayShoppingCart}</li>
 * <li>{@code http://localhost:8080/gamecast/admin/index.html} &raquo; {@code admin/index}</li>
 * </ul>
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.web.servlet.RequestToViewNameTranslator
 * @see org.springframework.web.servlet.ViewResolver
 */
public class DefaultRequestToViewNameTranslator implements RequestToViewNameTranslator {
	// RequestToViewNameTranslator 的唯一实现
	// 在下面找到一些请求查看名称翻译的示例。
	// http://localhost:8080/gamecast/display.html » display
	// http://localhost:8080/gamecast/displayShoppingCart.html » displayShoppingCart
	// http://localhost:8080/gamecast/admin/index.html » admin/index

	// RequestToViewNameTranslator简单地将传入请求的 URI 转换为视图名称。
	// 可以在DispatcherServlet上下文中显式定义为 viewNameTranslator bean。否则，将使用普通的默认实例。
	// 默认转换只是去掉前导和尾随斜杠以及 URI 的文件扩展名，并将结果作为视图名称返回，并根据需要添加配置的prefix和suffix 。
	// 可以分别使用stripLeadingSlash和stripExtension属性来禁用前导斜杠和文件扩展名的剥离。

	private static final String SLASH = "/";


	private String prefix = ""; // 定制前缀

	private String suffix = ""; // 定制后缀

	private String separator = SLASH;

	private boolean stripLeadingSlash = true; // 是否去掉前导

	private boolean stripTrailingSlash = true; // 是否去掉后缀斜线

	private boolean stripExtension = true; // 是否去掉文件后缀

	private UrlPathHelper urlPathHelper = new UrlPathHelper(); // 解析器


	/**
	 * Set the prefix to prepend to generated view names.
	 * @param prefix the prefix to prepend to generated view names
	 */
	public void setPrefix(@Nullable String prefix) {
		this.prefix = (prefix != null ? prefix : "");
	}

	/**
	 * Set the suffix to append to generated view names.
	 * @param suffix the suffix to append to generated view names
	 */
	public void setSuffix(@Nullable String suffix) {
		this.suffix = (suffix != null ? suffix : "");
	}

	/**
	 * Set the value that will replace '{@code /}' as the separator
	 * in the view name. The default behavior simply leaves '{@code /}'
	 * as the separator.
	 */
	public void setSeparator(String separator) {
		this.separator = separator;
	}

	/**
	 * Set whether or not leading slashes should be stripped from the URI when
	 * generating the view name. Default is "true".
	 */
	public void setStripLeadingSlash(boolean stripLeadingSlash) {
		this.stripLeadingSlash = stripLeadingSlash;
	}

	/**
	 * Set whether or not trailing slashes should be stripped from the URI when
	 * generating the view name. Default is "true".
	 */
	public void setStripTrailingSlash(boolean stripTrailingSlash) {
		this.stripTrailingSlash = stripTrailingSlash;
	}

	/**
	 * Set whether or not file extensions should be stripped from the URI when
	 * generating the view name. Default is "true".
	 */
	public void setStripExtension(boolean stripExtension) {
		this.stripExtension = stripExtension;
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setAlwaysUseFullPath
	 */
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
	}

	/**
	 * Shortcut to same property on underlying {@link #setUrlPathHelper UrlPathHelper}.
	 * @see org.springframework.web.util.UrlPathHelper#setUrlDecode
	 */
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
	}

	/**
	 * Set if ";" (semicolon) content should be stripped from the request URI.
	 * @see org.springframework.web.util.UrlPathHelper#setRemoveSemicolonContent(boolean)
	 */
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
	}

	/**
	 * Set the {@link org.springframework.web.util.UrlPathHelper} to use for
	 * the resolution of lookup paths.
	 * <p>Use this to override the default UrlPathHelper with a custom subclass,
	 * or to share common UrlPathHelper settings across multiple web components.
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
	}


	/**
	 * Translates the request URI of the incoming {@link HttpServletRequest}
	 * into the view name based on the configured parameters.
	 * @see org.springframework.web.util.UrlPathHelper#getLookupPathForRequest
	 * @see #transformPath
	 */
	@Override
	public String getViewName(HttpServletRequest request) {
		// 1. 获取lookupPath
		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request, HandlerMapping.LOOKUP_PATH);
		// 2. 转换并添加前缀/后缀
		return (this.prefix + transformPath(lookupPath) + this.suffix);
	}

	/**
	 * Transform the request URI (in the context of the webapp) stripping
	 * slashes and extensions, and replacing the separator as required.
	 * @param lookupPath the lookup path for the current request,
	 * as determined by the UrlPathHelper
	 * @return the transformed path, with slashes and extensions stripped
	 * if desired
	 */
	@Nullable
	protected String transformPath(String lookupPath) {
		String path = lookupPath;
		// 1. 去掉前导
		if (this.stripLeadingSlash && path.startsWith(SLASH)) {
			path = path.substring(1);
		}
		// 2. 去掉后缀斜线
		if (this.stripTrailingSlash && path.endsWith(SLASH)) {
			path = path.substring(0, path.length() - 1);
		}
		// 3. 去掉文件后缀
		if (this.stripExtension) {
			path = StringUtils.stripFilenameExtension(path);
		}
		if (!SLASH.equals(this.separator)) {
			path = StringUtils.replace(path, SLASH, this.separator);
		}
		return path;
	}

}
