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

package org.springframework.web.servlet.mvc.method.annotation;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.AbstractCookieValueMethodArgumentResolver;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;

/**
 * An {@link org.springframework.web.method.annotation.AbstractCookieValueMethodArgumentResolver}
 * that resolves cookie values from an {@link HttpServletRequest}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ServletCookieValueMethodArgumentResolver extends AbstractCookieValueMethodArgumentResolver {
	// 主要就是完成 @CookieValue 的解析工作
	// resolveName(String cookieName, MethodParameter parameter, NativeWebRequest webRequest)

	private UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance;


	public ServletCookieValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		super(beanFactory);
	}


	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		this.urlPathHelper = urlPathHelper;
	}


	@Override
	@Nullable
	protected Object resolveName(String cookieName, MethodParameter parameter,
			NativeWebRequest webRequest) throws Exception {


		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		Assert.state(servletRequest != null, "No HttpServletRequest");

		// 工具方法，底层是：request.getCookies()
		Cookie cookieValue = WebUtils.getCookie(servletRequest, cookieName);
		// 如果用javax.servlet.http.Cookie接受值，就直接返回了
		if (Cookie.class.isAssignableFrom(parameter.getNestedParameterType())) {
			return cookieValue;
		}
		// 否则返回cookieValue
		else if (cookieValue != null) {
			return this.urlPathHelper.decodeRequestString(servletRequest, cookieValue.getValue());
		}
		else {
			return null;
		}
	}

}
