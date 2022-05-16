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

package org.springframework.web.servlet.config.annotation;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * A subclass of {@code WebMvcConfigurationSupport} that detects and delegates
 * to all beans of type {@link WebMvcConfigurer} allowing them to customize the
 * configuration provided by {@code WebMvcConfigurationSupport}. This is the
 * class actually imported by {@link EnableWebMvc @EnableWebMvc}.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
@Configuration(proxyBeanMethods = false)
public class DelegatingWebMvcConfiguration extends WebMvcConfigurationSupport {
	// WebMvcConfigurationSupport的子类，它检测并委托给所有类型为WebMvcConfigurer的 bean，允许它们自定义WebMvcConfigurationSupport提供的配置。这是@EnableWebMvc实际导入的类。
	// 配置 proxyBeanMethods 属性后，配置类不会被代理了。主要是为了提高性能，
	// 如果你的 @Bean 方法之间没有调用关系的话可以把 proxyBeanMethods 设置为 false。否则，方法内部引用的类生产的类和 Spring 容器中类是两个类。

	// 同时需要注意到: DelegatingWebMvcConfiguration 是继承了 WebMvcConfigurationSupport 的
	// 重写了 WebMvcConfigurationSupport 留给用户的扩展接口 -- 比如重写在WebMvcConfigurationSupport默认的拦截器上添加拦截的方法addInterceptors()

	private final WebMvcConfigurerComposite configurers = new WebMvcConfigurerComposite(); // WebMvc的配置组合模式


	@Autowired(required = false)
	public void setConfigurers(List<WebMvcConfigurer> configurers) {
		// DelegatingWebMvcConfiguration是一个@Configuration配置类，
		// 自动注入 WebMvcConfigurer 的用户自定义的配置实现类
		if (!CollectionUtils.isEmpty(configurers)) {
			this.configurers.addWebMvcConfigurers(configurers); // 将所有监测到的configurers注册到复合的WebMvcConfigurer中
		}
	}


	@Override
	protected void configurePathMatch(PathMatchConfigurer configurer) {
		this.configurers.configurePathMatch(configurer);
	}

	@Override
	protected void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		this.configurers.configureContentNegotiation(configurer);
	}

	@Override
	protected void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		this.configurers.configureAsyncSupport(configurer);
	}

	@Override
	protected void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		this.configurers.configureDefaultServletHandling(configurer);
	}

	@Override
	protected void addFormatters(FormatterRegistry registry) {
		this.configurers.addFormatters(registry);
	}

	@Override
	protected void addInterceptors(InterceptorRegistry registry) {
		this.configurers.addInterceptors(registry);
	}

	@Override
	protected void addResourceHandlers(ResourceHandlerRegistry registry) {
		this.configurers.addResourceHandlers(registry);
	}

	@Override
	protected void addCorsMappings(CorsRegistry registry) {
		this.configurers.addCorsMappings(registry);
	}

	@Override
	protected void addViewControllers(ViewControllerRegistry registry) {
		this.configurers.addViewControllers(registry);
	}

	@Override
	protected void configureViewResolvers(ViewResolverRegistry registry) {
		this.configurers.configureViewResolvers(registry);
	}

	@Override
	protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.configurers.addArgumentResolvers(argumentResolvers);
	}

	@Override
	protected void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.configurers.addReturnValueHandlers(returnValueHandlers);
	}

	@Override
	protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		this.configurers.configureMessageConverters(converters);
	}

	@Override
	protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		this.configurers.extendMessageConverters(converters);
	}

	@Override
	protected void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
		this.configurers.configureHandlerExceptionResolvers(exceptionResolvers);
	}

	@Override
	protected void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
		this.configurers.extendHandlerExceptionResolvers(exceptionResolvers);
	}

	@Override
	@Nullable
	protected Validator getValidator() {
		return this.configurers.getValidator();
	}

	@Override
	@Nullable
	protected MessageCodesResolver getMessageCodesResolver() {
		return this.configurers.getMessageCodesResolver();
	}

}
