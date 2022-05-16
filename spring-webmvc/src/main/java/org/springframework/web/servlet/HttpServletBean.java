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

package org.springframework.web.servlet;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Simple extension of {@link javax.servlet.http.HttpServlet} which treats
 * its config parameters ({@code init-param} entries within the
 * {@code servlet} tag in {@code web.xml}) as bean properties.
 *
 * <p>A handy superclass for any type of servlet. Type conversion of config
 * parameters is automatic, with the corresponding setter method getting
 * invoked with the converted value. It is also possible for subclasses to
 * specify required properties. Parameters without matching bean property
 * setter will simply be ignored.
 *
 * <p>This servlet leaves request handling to subclasses, inheriting the default
 * behavior of HttpServlet ({@code doGet}, {@code doPost}, etc).
 *
 * <p>This generic servlet base class has no dependency on the Spring
 * {@link org.springframework.context.ApplicationContext} concept. Simple
 * servlets usually don't load their own context but rather access service
 * beans from the Spring root application context, accessible via the
 * filter's {@link #getServletContext() ServletContext} (see
 * {@link org.springframework.web.context.support.WebApplicationContextUtils}).
 *
 * <p>The {@link FrameworkServlet} class is a more specific servlet base
 * class which loads its own application context. FrameworkServlet serves
 * as direct base class of Spring's full-fledged {@link DispatcherServlet}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #addRequiredProperty
 * @see #initServletBean
 * @see #doGet
 * @see #doPost
 */
@SuppressWarnings("serial")
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {
	/**
	 * Servlet接口：定义Servlet的生命周期init、destroy，以及执行service，以及获取servletContext和servletConfig的方法
	 *
	 * GenericServlet抽象类：实现Servlet接口，主要是聚合ServletConfig，提供servletConfig
	 * 		同时实现ServletConfig接口，提供Servlet的相关参数，比如servletName、initParameters、servletContext等
	 *
	 * HttpServlet 抽象类：继承GenericServlet抽象类，主要目的是将无协议的Servlet作为有协议的Http的Servlet，
	 * 		即将原有的service的形参转为HttpServletRequest和HttpServletResponse的service方法，并根据requestMethod解耦为doGet、doPost等具体方法
	 *
	 * HttpServletBean 继承HttpServlet
	 * 作用：依赖注入 ConfigurableEnvironment
	 * 重点：关注init
	 *
	 */

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private ConfigurableEnvironment environment;

	private final Set<String> requiredProperties = new HashSet<>(4);


	/**
	 * Subclasses can invoke this method to specify that this property
	 * (which must match a JavaBean property they expose) is mandatory,
	 * and must be supplied as a config parameter. This should be called
	 * from the constructor of a subclass.
	 * <p>This method is only relevant in case of traditional initialization
	 * driven by a ServletConfig instance.
	 * @param property name of the required property
	 */
	protected final void addRequiredProperty(String property) {
		this.requiredProperties.add(property);
	}

	/**
	 * Set the {@code Environment} that this servlet runs in.
	 * <p>Any environment set here overrides the {@link StandardServletEnvironment}
	 * provided by default.
	 * @throws IllegalArgumentException if environment is not assignable to
	 * {@code ConfigurableEnvironment}
	 */
	@Override
	public void setEnvironment(Environment environment) {
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment, "ConfigurableEnvironment required");
		this.environment = (ConfigurableEnvironment) environment;
	}

	/**
	 * Return the {@link Environment} associated with this servlet.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardServletEnvironment}.
	 * <p>Subclasses may override this in order to configure the environment or
	 * specialize the environment type returned.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardServletEnvironment();
	}

	/**
	 * Map config parameters onto bean properties of this servlet, and
	 * invoke subclass initialization.
	 * @throws ServletException if bean properties are invalid (or required
	 * properties are missing), or if subclass initialization fails.
	 */
	@Override
	public final void init() throws ServletException {
		/**
		 * Servlet容器在启动后，就会加载Servlet的Class信息到内存上，但不一定立即执行Servlet#init()的初始化方法；
		 * 如果Servlet被配置为loadStartUp=1，即表示立即初始化，将会在类加载之后马上调用Servlet#init()方法 -- 即当前所重写的方法
		 */

		// HttpServletBean的初始化init() - 主要将Servlet的InitParameters转为PropertyValues,然后设置到当前对象中
		PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);
		if (!pvs.isEmpty()) {
			try {
				// BeanWrapper是一个实体包装类，简单地说，BeanWrapper提供分析和操作JavaBean的方案，如值的set/get方法、描述的set/get方法以及属性的可读可写性

				//这里面我们并没有给此Servlet初始化的一些参数，所以此处为空，为false
				//若进来了，可以看到里面会做一些处理：将这个DispatcherServlet转换成一个BeanWrapper对象，从而能够以spring的方式来对初始化参数的值进行注入。这些属性如contextConfigLocation、namespace等等。
				//同时注册一个属性编辑器，一旦在属性注入的时候遇到Resource类型的属性就会使用ResourceEditor去解析。再留一个initBeanWrapper(bw)方法给子类覆盖，让子类处真正执行BeanWrapper的属性注入工作。
				//但是HttpServletBean的子类FrameworkServlet和DispatcherServlet都没有覆盖其initBeanWrapper(bw)方法，所以创建的BeanWrapper对象没有任何作用。

				//备注：此部分把当前Servlet封装成一个BeanWrapper在把它交给Spring管理部分非常重要，比如后续我们讲到SpringBoot源码的时候，会看出来这部分代码的重要性了。。。
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this); // 包装的target对象就是当前对象
				ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext()); //
				bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment())); // BeanWrapper需要一个属性编辑者
				initBeanWrapper(bw); // 【抽象方法】目前没有任何子类实现
				bw.setPropertyValues(pvs, true); // target对象的属性来自pvs中
			}
			catch (BeansException ex) {
				if (logger.isErrorEnabled()) {
					logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
				}
				throw ex;
			}
		}

		// 让具体子类实现他们需要的初始化操作
		initServletBean();
	}

	/**
	 * Initialize the BeanWrapper for this HttpServletBean,
	 * possibly with custom editors.
	 * <p>This default implementation is empty.
	 * @param bw the BeanWrapper to initialize
	 * @throws BeansException if thrown by BeanWrapper methods
	 * @see org.springframework.beans.BeanWrapper#registerCustomEditor
	 */
	protected void initBeanWrapper(BeanWrapper bw) throws BeansException {
	}

	/**
	 * Subclasses may override this to perform custom initialization.
	 * All bean properties of this servlet will have been set before this
	 * method is invoked.
	 * <p>This default implementation is empty.
	 * @throws ServletException if subclass initialization fails
	 */
	protected void initServletBean() throws ServletException {
	}

	/**
	 * Overridden method that simply returns {@code null} when no
	 * ServletConfig set yet.
	 * @see #getServletConfig()
	 */
	@Override
	@Nullable
	public String getServletName() {
		return (getServletConfig() != null ? getServletConfig().getServletName() : null);
	}


	/**
	 * PropertyValues implementation created from ServletConfig init parameters.
	 */
	private static class ServletConfigPropertyValues extends MutablePropertyValues {
		// ServletConfigPropertyValues 目的：将ServletConfig的initParameter信息转为Spring管理的PropertyValues
		// 作为一个适配器

		/**
		 * Create new ServletConfigPropertyValues.
		 * @param config the ServletConfig we'll use to take PropertyValues from
		 * @param requiredProperties set of property names we need, where
		 * we can't accept default values
		 * @throws ServletException if any required properties are missing
		 */
		public ServletConfigPropertyValues(ServletConfig config, Set<String> requiredProperties) throws ServletException {
			// ServletConfigPropertyValues 构造函数()

			Set<String> missingProps = (!CollectionUtils.isEmpty(requiredProperties) ? new HashSet<>(requiredProperties) : null);

			// 1. 遍历servletConfig中的配置initParameter,提取property和对应value
			Enumeration<String> paramNames = config.getInitParameterNames();
			while (paramNames.hasMoreElements()) {
				String property = paramNames.nextElement();
				Object value = config.getInitParameter(property);
				addPropertyValue(new PropertyValue(property, value));
				if (missingProps != null) {
					missingProps.remove(property);
				}
			}

			// Fail if we are still missing properties.
			if (!CollectionUtils.isEmpty(missingProps)) {
				throw new ServletException(
						"Initialization from ServletConfig for servlet '" + config.getServletName() +
						"' failed; the following required properties were missing: " +
						StringUtils.collectionToDelimitedString(missingProps, ", "));
			}
		}
	}

}
