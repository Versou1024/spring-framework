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

package org.springframework.web.reactive.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.cors.CorsConfiguration;

/**
 * Assists with the registration of global, URL pattern based
 * {@link CorsConfiguration} mappings.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public class CorsRegistry {
	// CorsRegistration 注册表

	// 持有 CorsRegistration 跨域配置注册员的集合
	private final List<CorsRegistration> registrations = new ArrayList<>();


	/**
	 * Enable cross-origin request handling for the specified path pattern.
	 * <p>Exact path mapping URIs (such as {@code "/admin"}) are supported as
	 * well as Ant-style path patterns (such as {@code "/admin/**"}).
	 * <p>By default, the {@code CorsConfiguration} for this mapping is
	 * initialized with default values as described in
	 * {@link CorsConfiguration#applyPermitDefaultValues()}.
	 */
	public CorsRegistration addMapping(String pathPattern) {
		// 为指定的路径模式启用跨域请求处理。
		// 支持精确路径映射 URI（例如"/admin" ）以及 Ant 样式的路径模式（例如"/admin/**" ）

		// 1. 方法中使用了默认的跨域配置 --  new CorsConfiguration().applyPermitDefaultValues()
		CorsRegistration registration = new CorsRegistration(pathPattern);
		// 2. 然后加入到 注册表中
		this.registrations.add(registration);
		return registration;
	}

	/**
	 * Return the registered {@link CorsConfiguration} objects,
	 * keyed by path pattern.
	 */
	protected Map<String, CorsConfiguration> getCorsConfigurations() {
		Map<String, CorsConfiguration> configs = new LinkedHashMap<>(this.registrations.size());
		for (CorsRegistration registration : this.registrations) {
			configs.put(registration.getPathPattern(), registration.getCorsConfiguration());
		}
		return configs;
	}

}
