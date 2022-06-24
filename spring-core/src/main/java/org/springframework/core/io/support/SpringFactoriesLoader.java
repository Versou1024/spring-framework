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

package org.springframework.core.io.support;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * General purpose factory loading mechanism for internal use within the framework.
 *
 * <p>{@code SpringFactoriesLoader} {@linkplain #loadFactories loads} and instantiates
 * factories of a given type from {@value #FACTORIES_RESOURCE_LOCATION} files which
 * may be present in multiple JAR files in the classpath. The {@code spring.factories}
 * file must be in {@link Properties} format, where the key is the fully qualified
 * name of the interface or abstract class, and the value is a comma-separated list of
 * implementation class names. For example:
 *
 * <pre class="code">example.MyService=example.MyServiceImpl1,example.MyServiceImpl2</pre>
 *
 * where {@code example.MyService} is the name of the interface, and {@code MyServiceImpl1}
 * and {@code MyServiceImpl2} are two implementations.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.2
 */
public final class SpringFactoriesLoader {
	// 这个类在SpringBoot开启自动化配置中被使用到
	// SpringFactoriesLoader这个类本身是final,不可以继承的
	// 同时所有的方法都是静态方法
	// 内环缓存cache也是静态不可变的


	/**
	 * The location to look for factories.
	 * <p>Can be present in multiple JAR files.
	 */
	public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
	// 默认的加载位置


	private static final Log logger = LogFactory.getLog(SpringFactoriesLoader.class);

	// key为ClassLoader
	// value为ClassLoader加载的spring.factories中key和values
	private static final Map<ClassLoader, MultiValueMap<String, String>> cache = new ConcurrentReferenceHashMap<>();

	private SpringFactoriesLoader() {
	}


	/**
	 * Load and instantiate the factory implementations of the given type from
	 * {@value #FACTORIES_RESOURCE_LOCATION}, using the given class loader.
	 * <p>The returned factories are sorted through {@link AnnotationAwareOrderComparator}.
	 * <p>If a custom instantiation strategy is required, use {@link #loadFactoryNames}
	 * to obtain all registered factory names.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
	 * @throws IllegalArgumentException if any factory implementation class cannot
	 * be loaded or if an error occurs while instantiating any factory
	 * @see #loadFactoryNames
	 */
	public static <T> List<T> loadFactories(Class<T> factoryType, @Nullable ClassLoader classLoader) {
		// 相比于 loadFactoryNames()
		// 该方法会:
		// 使用给定的类加载器从"META-INF/spring.factories"[[加载和实例化]]给定类型的工厂实现。
		// 返回的工厂通过AnnotationAwareOrderComparator进行排序

		Assert.notNull(factoryType, "'factoryType' must not be null");
		ClassLoader classLoaderToUse = classLoader;
		if (classLoaderToUse == null) {
			classLoaderToUse = SpringFactoriesLoader.class.getClassLoader();
		}
		// 1. 去 META-INF/spring.factories 找到 factoryType 全限定名作为key时,
		// 想应的 values集合即 factoryImplementationNames -- [一般这个values集合都是全限定类型 -- 因为需要类加载器加载这个类,因此必须是全限定类名]
		List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
		if (logger.isTraceEnabled()) {
			logger.trace("Loaded [" + factoryType.getName() + "] names: " + factoryImplementationNames);
		}
		List<T> result = new ArrayList<>(factoryImplementationNames.size());
		for (String factoryImplementationName : factoryImplementationNames) {
			// 2. 对 factoryImplementationNames 进行实例化
			// 可以看见,传递的参数 Class<T> factoryType 为泛型T,返回的是 List<T>
			// 所以,我们要使用Spring.factories需要注意,key为全限定名,value为key全限定名的子类或实现类的全限定名
			result.add(instantiateFactory(factoryImplementationName, factoryType, classLoaderToUse));
		}
		// 3. 允许排序
		AnnotationAwareOrderComparator.sort(result);
		// 返回结果
		return result;
	}

	/**
	 * Load the fully qualified class names of factory implementations of the
	 * given type from {@value #FACTORIES_RESOURCE_LOCATION}, using the given
	 * class loader.
	 * @param factoryType the interface or abstract class representing the factory
	 * @param classLoader the ClassLoader to use for loading resources; can be
	 * {@code null} to use the default
	 * @throws IllegalArgumentException if an error occurs while loading factory names
	 * @see #loadFactories
	 */
	public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
		// 1. 获取key
		// 因为在 META-INF/spring.factories 中的key都是某个factoryType的全名
		String factoryTypeName = factoryType.getName();
		// 2. loadSpringFactories(classLoader) -> 就是获取  META-INF/spring.factories 这个文件的 Map<String, List<String>> 结构
		// 然后以 factoryType 为 key,获取这个factoryType对应的实现类的名字
		// 例如在SpringBoot中,有
		// org.springframework.boot.env.PropertySourceLoader=\
		//		org.springframework.boot.env.PropertiesPropertySourceLoader,\
		//		org.springframework.boot.env.YamlPropertySourceLoader
		// key 就是 PropertySourceLoader
		// value 就是 [PropertiesPropertySourceLoader,YamlPropertySourceLoader]
		// PropertiesPropertySourceLoader/YamlPropertySourceLoader是PropertySourceLoader的实现类
		return loadSpringFactories(classLoader).getOrDefault(factoryTypeName, Collections.emptyList());
	}

	private static Map<String, List<String>> loadSpringFactories(@Nullable ClassLoader classLoader) {
		// 1. 缓存是否命中
		MultiValueMap<String, String> result = cache.get(classLoader);
		if (result != null) {
			return result;
		}

		try {
			// 2. 缓存未写命中,利用ClassLoader加载 META-INF/spring.factories 文件
			// ClassLoader加载资源文件 -- 有一个特点:是可以加载出jar中的class或者资源文件
			// 因此SpringFactoriesLoader是专门用来帮助加载jar包中需要注入到ioc容器中的工具 --
			// ❗️❗️❗️
			// 		a: jar包需要通过一个外部注解类似@EnableXxx
			// 		b: @EnableXxx注解上使用@Import({XxxxImportSelector.class})
			// 		C: XxxxImportSelector可以实现DeferredImportSelector,然后再String[] selectImports(AnnotationMetadata annotationMetadata)
			// 		d: 调用SpringFactoriesLoader.loadFactoryNames()加载出jar包中spring.factories下指定key需要加载到ioc容器的全限定类名集合
			Enumeration<URL> urls = (classLoader != null ?
					classLoader.getResources(FACTORIES_RESOURCE_LOCATION) :
					ClassLoader.getSystemResources(FACTORIES_RESOURCE_LOCATION));
			// 3. 这里是加载出整个项目中每个jar包里面的 spring.factories -- 前提是:属于当前ClassLoader可访问的路径
			result = new LinkedMultiValueMap<>();
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				// 3.0 遍历当前的spring.factories文件 -> 转换为 UrlResource
				UrlResource resource = new UrlResource(url);
				// 3.1 将resource转换为我们的properties文件 -- 方便获取属性
				Properties properties = PropertiesLoaderUtils.loadProperties(resource);
				// 3.2 获取META-INF/spring.factories对应的properties文件中的键值对
				for (Map.Entry<?, ?> entry : properties.entrySet()) {
					String factoryTypeName = ((String) entry.getKey()).trim();
					for (String factoryImplementationName : StringUtils.commaDelimitedListToStringArray((String) entry.getValue())) {
						// 3.3 将properties分析,key就是key,value需要做分隔符后,作为数组加入到result的value中
						result.add(factoryTypeName, factoryImplementationName.trim());
					}
				}
			}
			// 4. 最终加入到缓存中去
			cache.put(classLoader, result);
			return result;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Unable to load factories from location [" +
					FACTORIES_RESOURCE_LOCATION + "]", ex);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T instantiateFactory(String factoryImplementationName, Class<T> factoryType, ClassLoader classLoader) {
		try {
			Class<?> factoryImplementationClass = ClassUtils.forName(factoryImplementationName, classLoader);
			if (!factoryType.isAssignableFrom(factoryImplementationClass)) {
				throw new IllegalArgumentException(
						"Class [" + factoryImplementationName + "] is not assignable to factory type [" + factoryType.getName() + "]");
			}
			return (T) ReflectionUtils.accessibleConstructor(factoryImplementationClass).newInstance();
		}
		catch (Throwable ex) {
			throw new IllegalArgumentException(
				"Unable to instantiate factory class [" + factoryImplementationName + "] for factory type [" + factoryType.getName() + "]",
				ex);
		}
	}

}