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

package org.springframework.beans.factory;

/**
 * A marker superinterface indicating that a bean is eligible to be notified by the
 * Spring container of a particular framework object through a callback-style method.
 * The actual method signature is determined by individual subinterfaces but should
 * typically consist of just one void-returning method that accepts a single argument.
 *
 * <p>Note that merely implementing {@link Aware} provides no default functionality.
 * Rather, processing must be done explicitly, for example in a
 * {@link org.springframework.beans.factory.config.BeanPostProcessor}.
 * Refer to {@link org.springframework.context.support.ApplicationContextAwareProcessor}
 * for an example of processing specific {@code *Aware} interface callbacks.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
public interface Aware {
	/**
	 * Spring提供Aware接口能让Bean感知Spring容器的存在，即让Bean可以使用Spring容器所提供的资源，
	 * 下面是Spring提供的感知接口：
	 * 		ApplicationContextAware：获取容器上下文
	 * 		BeanClassLoaderAware：获取加载当前Bean的类加载器
	 * 		BeanNameAware：获取当前Bean的名称
	 * 		LoadTimeWeaverAware：可以接收一个指向载入时（编译时）时织入实例的引用，实现编译时代理，属于比较高端的。可参见AspectJWeavingEnabler
	 * 		BootstrapContextAware：拿到资源适配器BootstrapContext上下文，如JCA,CCI
	 * 		ServletConfigAware：获取到ServletConfig
	 * 		ImportAware：获取到AnnotationMetadata等信息。这个挺重要的，比如AbstractCachingConfiguration、AbstractTransactionManagementConfiguration都通过实现这个接口来获取到了注解的属性们。比如@EnableAsync、EnableCaching等注解上的属性值 参考：Spring的@Import注解与ImportAware接口
	 * 		EmbeddedValueResolverAware：能让我们拿到StringValueResolver这个处理器，这样我们就可以很好的处理配置文件的值了。我们可以做个性化处理（比如我们自己要书写一个属性获取的工具类之类的。。。）
	 * 		EnvironmentAware：拿到环境Environment
	 * 		BeanFactoryAware：获取Bean Factory
	 * 		NotificationPublisherAware：和JMX有关
	 * 		ResourceLoaderAware：获取资源加载器ResourceLoader可以获得外部资源文件 比如它的：ResourceLoader#getResource方法
	 * 		MessageSourceAware：获取国际化文本信息
	 * 		ServletContextAware：获取ServletContext
	 * 		ApplicationEventPublisher：拿到事件发布器
	 * ————————————————
	 * 原文链接：https://blog.csdn.net/f641385712/article/details/88418195
	 */

}
