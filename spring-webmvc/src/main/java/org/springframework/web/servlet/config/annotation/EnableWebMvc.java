/*
 * Copyright 2002-2017 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Adding this annotation to an {@code @Configuration} class imports the Spring MVC
 * configuration from {@link WebMvcConfigurationSupport}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = MyConfiguration.class)
 * public class MyConfiguration {
 *
 * }
 * </pre>
 *
 * <p>To customize the imported configuration, implement the interface
 * {@link WebMvcConfigurer} and override individual methods, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = MyConfiguration.class)
 * public class MyConfiguration implements WebMvcConfigurer {
 *
 * 	   &#064;Override
 * 	   public void addFormatters(FormatterRegistry formatterRegistry) {
 *         formatterRegistry.addConverter(new MyConverter());
 * 	   }
 *
 * 	   &#064;Override
 * 	   public void configureMessageConverters(List&lt;HttpMessageConverter&lt;?&gt;&gt; converters) {
 *         converters.add(new MyHttpMessageConverter());
 * 	   }
 *
 * }
 * </pre>
 *
 * <p><strong>Note:</strong> only one {@code @Configuration} class may have the
 * {@code @EnableWebMvc} annotation to import the Spring Web MVC
 * configuration. There can however be multiple {@code @Configuration} classes
 * implementing {@code WebMvcConfigurer} in order to customize the provided
 * configuration.
 *
 * <p>If {@link WebMvcConfigurer} does not expose some more advanced setting that
 * needs to be configured consider removing the {@code @EnableWebMvc}
 * annotation and extending directly from {@link WebMvcConfigurationSupport}
 * or {@link DelegatingWebMvcConfiguration}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;ComponentScan(basePackageClasses = { MyConfiguration.class })
 * public class MyConfiguration extends WebMvcConfigurationSupport {
 *
 * 	   &#064;Override
 *	   public void addFormatters(FormatterRegistry formatterRegistry) {
 *         formatterRegistry.addConverter(new MyConverter());
 *	   }
 *
 *	   &#064;Bean
 *	   public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
 *         // Create or delegate to "super" to create and
 *         // customize properties of RequestMappingHandlerAdapter
 *	   }
 * }
 * </pre>
 *
 * @author Dave Syer
 * @author Rossen Stoyanchev
 * @since 3.1
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
 * @see org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(DelegatingWebMvcConfiguration.class)
public @interface EnableWebMvc {
	// 为什么WebMvcConfigurer实现要加@EnableWebMvc
	// @EnableWebMvc注解类上导入了DelegatingWebMvcConfiguration类，该类是WebMvcConfigurationSupport的子类，
	// 该类除了实例化WebMvcConfigurationSupport实例以外，另一个作用就是收集BeanFactory中所有WebMvcConfigurer的实现，
	// 汇集到WebMvcConfigurerComposite中，在WebMvcConfigurationSupport实例化过程中会分别调用这些实现，将
	// 相应的实例传入这些实现中，供开发者在此基础上添加自定义的配置。这也就是在WebMvcConfigurerAdapter子类上要加@EnableWebMvc的原因，因为要先实例化WebMvcConfigurationSupport。

	// 为什么可以存在多个WebMvcConfigurer的实现？
	// 一般来讲一个应用中一个WebMvcConfigurer的已经足够，设计成收集多个是不是有些多余？
	// 从springboot的autoconfigure机制来看并不多余，反而更灵活，
	// 比如我要写一个mybatis的AutoConfiguration和JPA的AutoConfiguration，我就可以在不同的AutoConfiguration里面定义一个WebMvcConfigurer的实现，
	// 里面只配置与mybatis或JPA相关的配置，这样需要那个启用那个，不需要人工通过注释代码来转换mybatis和JPA，
	// 注意：在springboot下自定义的WebMvcConfigurer实现配置类上是不需要添加@EnableWebMvc的，
	// 因为springboot已经实例化了WebMvcConfigurationSupport，如果添加了该注解，默认的WebMvcConfigurationSupport配置类是不会生效的，
	// 也就是以用户定义的为主，一般建议还是不覆盖默认的好。
}
