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

import org.springframework.core.convert.converter.Converter;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Defines callback methods to customize the Java-based configuration for
 * Spring MVC enabled via {@code @EnableWebMvc}.
 *
 * <p>{@code @EnableWebMvc}-annotated configuration classes may implement
 * this interface to be called back and given a chance to customize the
 * default configuration.
 *
 * @author Rossen Stoyanchev
 * @author Keith Donald
 * @author David Syer
 * @since 3.1
 */
public interface WebMvcConfigurer {

	/*
	 * WebMvcConfigurer定义回调方法，以通过@EnableWebMvc定制Spring MVC的基于Java的配置。
	 * @EnableWebMvc注释的@Configuration配置类可以实现此接口，以便回调，并有机会自定义默认配置。
	 *
	 *
	 * 大家从网上及源码注释上查到的解释是，在spring中配置WebMvc时有两种方法，
	 * 		一种是继承WebMvcConfigurationSupport，重写里面相应的方法，
	 * 		还有一种是继承WebMvcConfigurer的子抽象类WebMvcConfigurerAdapter，也是重写里面相应的方法，但是需要在配置类上添加@EnableWebMvc注解。
	 * 那这两个类直接是什么关系呢？
	 *
	 * 细心的开发者会发现，
	 * 		WebMvcConfigurationSupport中那些子类可以重写的空方法在WebMvcConfigurer都有，
	 * 		这说明WebMvcConfigurer只是WebMvcConfigurationSupport的一个扩展类，它并没有扩展新功能，只是为让用户更方便安全的添加自定义配置，为什么说是安全呢？
	 * 		因为如果直接继承WebMvcConfigurationSupport，那么用户可以重写默认的配置，如果对原理不是很清楚地开发者不小心重写了默认的配置，
	 * 		springmvc可能相关功能就无法生效，是一种不安全的行为。但如果是继承WebMvcConfigurerAdapter，
	 * 		那么开发者是在默认配置的基础上添加自定义配置，相对来说更安全一些，只不过要多加一个@EnableWebMvc注解。从这个角度来说，最佳实践还是继承WebMvcConfigurerAdapter，如下
	 *
	 * 为什么WebMvcConfigurer实现要加@EnableWebMvc
	 *
	 * @EnableWebMvc注解类上导入了DelegatingWebMvcConfiguration类，该类是WebMvcConfigurationSupport的子类，该类除了实例化WebMvcConfigurationSupport实例以外，另一个作用就是收集BeanFactory中所有WebMvcConfigurer的实现，汇集到WebMvcConfigurerComposite中，在WebMvcConfigurationSupport实例化过程中会分别调用这些实现，将相应的实例传入这些实现中，供开发者在此基础上添加自定义的配置。这也就是在WebMvcConfigurerAdapter子类上要加@EnableWebMvc的原因，因为要先实例化WebMvcConfigurationSupport。
	 * 为什么可以存在多个WebMvcConfigurer的实现？
	 * 一般来讲一个应用中一个WebMvcConfigurer的已经足够，设计成收集多个是不是有些多余？从springboot的autoconfigure机制来看并不多余，反而更灵活，比如我要写一个mybatis的AutoConfiguration和JPA的AutoConfiguration，我就可以在不同的AutoConfiguration里面定义一个WebMvcConfigurer的实现，里面只配置与mybatis或JPA相关的配置，这样需要那个启用那个，不需要人工通过注释代码来转换mybatis和JPA，注意：在springboot下自定义的WebMvcConfigurer实现配置类上是不需要添加@EnableWebMvc的，因为springboot已经实例化了WebMvcConfigurationSupport，如果添加了该注解，默认的WebMvcConfigurationSupport配置类是不会生效的，也就是以用户定义的为主，一般建议还是不覆盖默认的好。
	 */


	/**
	 * Helps with configuring HandlerMappings path matching options such as trailing slash match,
	 * suffix registration, path matcher and path helper.
	 * Configured path matcher and path helper instances are shared for:
	 * <ul>
	 * <li>RequestMappings</li>
	 * <li>ViewControllerMappings</li>
	 * <li>ResourcesMappings</li>
	 * </ul>
	 * @since 4.0.3
	 */
	default void configurePathMatch(PathMatchConfigurer configurer) { // 路径匹配配置
	}

	/**
	 * Configure content negotiation options.
	 */
	default void configureContentNegotiation(ContentNegotiationConfigurer configurer) { // 内容配置
	}

	/**
	 * Configure asynchronous request handling options.
	 */
	default void configureAsyncSupport(AsyncSupportConfigurer configurer) { // 异步配置
	}

	/**
	 * Configure a handler to delegate unhandled requests by forwarding to the
	 * Servlet container's "default" servlet. A common use case for this is when
	 * the {@link DispatcherServlet} is mapped to "/" thus overriding the
	 * Servlet container's default handling of static resources.
	 */
	default void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
	}

	/**
	 * Add {@link Converter Converters} and {@link Formatter Formatters} in addition to the ones
	 * registered by default.
	 */
	default void addFormatters(FormatterRegistry registry) { // 添加converters或formatters
	}

	/**
	 * Add Spring MVC lifecycle interceptors for pre- and post-processing of
	 * controller method invocations and resource handler requests.
	 * Interceptors can be registered to apply to all requests or be limited
	 * to a subset of URL patterns.
	 */
	default void addInterceptors(InterceptorRegistry registry) { // 添加拦截器
	}

	/**
	 * Add handlers to serve static resources such as images, js, and, css
	 * files from specific locations under web application root, the classpath,
	 * and others.
	 */
	default void addResourceHandlers(ResourceHandlerRegistry registry) { // 添加资源Handler
	}

	/**
	 * Configure cross origin requests processing.
	 * @since 4.2
	 */
	default void addCorsMappings(CorsRegistry registry) { // 添加跨域陪住
	}

	/**
	 * Configure simple automated controllers pre-configured with the response
	 * status code and/or a view to render the response body. This is useful in
	 * cases where there is no need for custom controller logic -- e.g. render a
	 * home page, perform simple site URL redirects, return a 404 status with
	 * HTML content, a 204 with no content, and more.
	 */
	default void addViewControllers(ViewControllerRegistry registry) { // 添加视图Controller
	}

	/**
	 * Configure view resolvers to translate String-based view names returned from
	 * controllers into concrete {@link org.springframework.web.servlet.View}
	 * implementations to perform rendering with.
	 * @since 4.1
	 */
	default void configureViewResolvers(ViewResolverRegistry registry) {
		// 配置视图解析器
	}

	/**
	 * Add resolvers to support custom controller method argument types.
	 * <p>This does not override the built-in support for resolving handler
	 * method arguments. To customize the built-in support for argument
	 * resolution, configure {@link RequestMappingHandlerAdapter} directly.
	 * @param resolvers initially an empty list
	 */
	default void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		// 添加参数解析器
	}

	/**
	 * Add handlers to support custom controller method return value types.
	 * <p>Using this option does not override the built-in support for handling
	 * return values. To customize the built-in support for handling return
	 * values, configure RequestMappingHandlerAdapter directly.
	 * @param handlers initially an empty list
	 */
	default void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
		// 添加返回值解析器
	}

	/**
	 * Configure the {@link HttpMessageConverter HttpMessageConverters} to use for reading or writing
	 * to the body of the request or response. If no converters are added, a
	 * default list of converters is registered.
	 * <p><strong>Note</strong> that adding converters to the list, turns off
	 * default converter registration. To simply add a converter without impacting
	 * default registration, consider using the method
	 * {@link #extendMessageConverters(java.util.List)} instead.
	 * @param converters initially an empty list of converters
	 */
	default void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * A hook for extending or modifying the list of converters after it has been
	 * configured. This may be useful for example to allow default converters to
	 * be registered and then insert a custom converter through this method.
	 * @param converters the list of configured converters to extend.
	 * @since 4.1.3
	 */
	default void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		// 扩展或修改converters
	}

	/**
	 * Configure exception resolvers.
	 * <p>The given list starts out empty. If it is left empty, the framework
	 * configures a default set of resolvers, see
	 * {@link WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)}.
	 * Or if any exception resolvers are added to the list, then the application
	 * effectively takes over and must provide, fully initialized, exception
	 * resolvers.
	 * <p>Alternatively you can use
	 * {@link #extendHandlerExceptionResolvers(List)} which allows you to extend
	 * or modify the list of exception resolvers configured by default.
	 * @param resolvers initially an empty list
	 * @see #extendHandlerExceptionResolvers(List)
	 * @see WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)
	 */
	default void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
	}

	/**
	 * Extending or modify the list of exception resolvers configured by default.
	 * This can be useful for inserting a custom exception resolver without
	 * interfering with default ones.
	 * @param resolvers the list of configured resolvers to extend
	 * @since 4.3
	 * @see WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)
	 */
	default void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) { // 扩展定制的异常处理器
	}

	/**
	 * Provide a custom {@link Validator} instead of the one created by default.
	 * The default implementation, assuming JSR-303 is on the classpath, is:
	 * {@link org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean}.
	 * Leave the return value as {@code null} to keep the default.
	 */
	@Nullable
	default Validator getValidator() {
		return null;
	}

	/**
	 * Provide a custom {@link MessageCodesResolver} for building message codes
	 * from data binding and validation error codes. Leave the return value as
	 * {@code null} to keep the default.
	 */
	@Nullable
	default MessageCodesResolver getMessageCodesResolver() {
		return null;
	}

}
