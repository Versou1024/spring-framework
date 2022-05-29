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

package org.springframework.core.env;

/**
 * Interface representing the environment in which the current application is running.
 * Models two key aspects of the application environment: <em>profiles</em> and
 * <em>properties</em>. Methods related to property access are exposed via the
 * {@link PropertyResolver} superinterface.
 *
 * <p>A <em>profile</em> is a named, logical group of bean definitions to be registered
 * with the container only if the given profile is <em>active</em>. Beans may be assigned
 * to a profile whether defined in XML or via annotations; see the spring-beans 3.1 schema
 * or the {@link org.springframework.context.annotation.Profile @Profile} annotation for
 * syntax details. The role of the {@code Environment} object with relation to profiles is
 * in determining which profiles (if any) are currently {@linkplain #getActiveProfiles
 * active}, and which profiles (if any) should be {@linkplain #getDefaultProfiles active
 * by default}.
 *
 * <p><em>Properties</em> play an important role in almost all applications, and may
 * originate from a variety of sources: properties files, JVM system properties, system
 * environment variables, JNDI, servlet context parameters, ad-hoc Properties objects,
 * Maps, and so on. The role of the environment object with relation to properties is to
 * provide the user with a convenient service interface for configuring property sources
 * and resolving properties from them.
 *
 * <p>Beans managed within an {@code ApplicationContext} may register to be {@link
 * org.springframework.context.EnvironmentAware EnvironmentAware} or {@code @Inject} the
 * {@code Environment} in order to query profile state or resolve properties directly.
 *
 * <p>In most cases, however, application-level beans should not need to interact with the
 * {@code Environment} directly but instead may have to have {@code ${...}} property
 * values replaced by a property placeholder configurer such as
 * {@link org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * PropertySourcesPlaceholderConfigurer}, which itself is {@code EnvironmentAware} and
 * as of Spring 3.1 is registered by default when using
 * {@code <context:property-placeholder/>}.
 *
 * <p>Configuration of the environment object must be done through the
 * {@code ConfigurableEnvironment} interface, returned from all
 * {@code AbstractApplicationContext} subclass {@code getEnvironment()} methods. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples demonstrating manipulation
 * of property sources prior to application context {@code refresh()}.
 *
 * @author Chris Beams
 * @since 3.1
 * @see PropertyResolver
 * @see EnvironmentCapable
 * @see ConfigurableEnvironment
 * @see AbstractEnvironment
 * @see StandardEnvironment
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#setEnvironment
 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
 */
public interface Environment extends PropertyResolver {
	/*
	 * 1、该接口表示当前应用程序运行的环境。
	 * 应用程序环境的两个关键方面：profiles和properties。与属性访问相关的方法通过 PropertyResolver super interface 公开。
	 * 2、profiles配置文件是一个命名的、逻辑的bean，只有在给定的配置文件处于活动状态时，才会向容器注册。
	 * bean可以被分配给一个profiles文件，无论是用XML定义的还是通过注释定义的；
	 * 3、与profiles文件相关的环境对象的作用是确定哪些profiles文件（如果有）当前处于活动状态，以及默认情况下哪些概要文件（如果有）应处于活动状态。
	 * 4、属性在几乎所有的应用程序中都扮演着重要的角色，可能来自各种来源：属性文件、JVM系统属性、系统环境变量、JNDI、servlet上下文参数、特殊属性对象、映射等等。
	 * 与属性相关的环境对象的作用是为用户提供一个方便的服务，用于配置属性源并从中解析属性。
	 * ApplicationContext中管理的bean可以注册为环境感知，或者@Inject环境，以便直接查询配置文件状态或解析属性。
	 * 5、在大多数情况下，应用程序级bean不需要直接与环境交互，而是可能必须具有${…}使用<context:property placeholder/>时，默认情况下会注册由属性占位符配置器（如PropertySourcesplacePlaceholderConfigurer）替换的属性值，该配置器本身是环境感知的，从Spring 3.1开始。
	 * 6、环境对象的配置必须通过ConfigurableEnvironment接口完成，该接口由所有AbstractApplicationContext子类getEnvironment（）方法返回
	 *
	 * 可以发现
	 * Environment 注重 profiles 的获取
	 * PropertyResolver 注重 environment 的获取
	 *
	 * 这个接口代表了当前应用正在运行的环境，为应用的两个重要方面建立抽象模型 【profiles】和【properties】。关于属性访问的方法通过父接口PropertyResolver暴露给客户端使用，本接口主要是扩展出访问【profiles】相关的接口。
	 * 对于他俩，我愿意这么来翻译：
	 * 		profiles：配置。它代表应用在一启动时注册到context中bean definitions的命名的逻辑分组。
	 * 		properties：属性。几乎在所有应用中都扮演着重要角色，他可能源自多种源头。例如属性文件，JVM系统属性，系统环境变量，JNDI，servlet上下文参数，Map等等，Environment对象和其相关的对象一起提供给用户一个方便用来配置和解析属性的服务。
	 */

	/**
	 * Return the set of profiles explicitly made active for this environment. Profiles
	 * are used for creating logical groupings of bean definitions to be registered
	 * conditionally, for example based on deployment environment. Profiles can be
	 * activated by setting {@linkplain AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 * "spring.profiles.active"} as a system property or by calling
	 * {@link ConfigurableEnvironment#setActiveProfiles(String...)}.
	 * <p>If no profiles have explicitly been specified as active, then any
	 * {@linkplain #getDefaultProfiles() default profiles} will automatically be activated.
	 * @see #getDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	String[] getActiveProfiles();
	//返回为此环境显式激活的profiles。profiles用于创建有条件注册的bean definitions的逻辑分组，
	//例如基于部署环境,通过将“spring.Profiles.active”设置为系统属性或调用 ConfigurableEnvironment.setActiveProfiles(String...).来激活配置文件
	//如果没有明确指定为活动的配置文件，则任何默认配置文件都将自动激活。

	/**
	 * Return the set of profiles to be active by default when no active profiles have
	 * been set explicitly.
	 * @see #getActiveProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	String[] getDefaultProfiles();

	/**
	 * Return whether one or more of the given profiles is active or, in the case of no
	 * explicit active profiles, whether one or more of the given profiles is included in
	 * the set of default profiles. If a profile begins with '!' the logic is inverted,
	 * i.e. the method will return {@code true} if the given profile is <em>not</em> active.
	 * For example, {@code env.acceptsProfiles("p1", "!p2")} will return {@code true} if
	 * profile 'p1' is active or 'p2' is not active.
	 * @throws IllegalArgumentException if called with zero arguments
	 * or if any profile is {@code null}, empty, or whitespace only
	 * @see #getActiveProfiles
	 * @see #getDefaultProfiles
	 * @see #acceptsProfiles(Profiles)
	 * @deprecated as of 5.1 in favor of {@link #acceptsProfiles(Profiles)}
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);
	//返回一个或多给定profiles是否处于active状态，
	//或者当如果没有明确的profiles文件，则返回一个或多个给定形参profiles是否包含在默认配置profiles集中。
	//如果形参的profile以“！”开头逻辑是反向的，即如果给定的profile未激活，该方法将返回true。
	// 例如，env.acceptsProfiles("p1", "!p2") 会返回true，如果配置文件“p1”处于活动状态或“p2”未处于活动状态

	/**
	 * Return whether the {@linkplain #getActiveProfiles() active profiles}
	 * match the given {@link Profiles} predicate.
	 */
	boolean acceptsProfiles(Profiles profiles);

}
