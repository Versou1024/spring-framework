/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Parser for the @{@link ComponentScan} annotation.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @see ClassPathBeanDefinitionScanner#scan(String...)
 * @see ComponentScanBeanDefinitionParser
 */
class ComponentScanAnnotationParser {

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanNameGenerator beanNameGenerator;

	private final BeanDefinitionRegistry registry;


	public ComponentScanAnnotationParser(Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator beanNameGenerator, BeanDefinitionRegistry registry) {

		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.beanNameGenerator = beanNameGenerator;
		this.registry = registry;
	}


	public Set<BeanDefinitionHolder> parse(AnnotationAttributes componentScan, String declaringClass) {
		// 开始从@ComponentScane中解析出Set<BeanDefinitionHolder>
		
		// 1. 类路径下的BeanDefinition扫描器：需要注入registry、默认过滤器、environment、resourceLoader
		// ❗️❗️❗️ 注意：useDefaultFilters这个值特别的重要，能够解释伙伴们为何要配置的原因~~~下面讲解它的时候会有详解
		// useDefaultFilters 允许使用默认的过滤器 -> 而默认的过滤器就是用来过滤 @Controller @Service 等 @Component注解的 衍生注解
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.registry, componentScan.getBoolean("useDefaultFilters"), this.environment, this.resourceLoader);

		// 2. BeanName的生成器，我们可以单独制定。若不指定（大部分情况下都不指定），那就是默认的AnnotationBeanNameGenerator
		// 它的处理方式是：类名首字母小写
		Class<? extends BeanNameGenerator> generatorClass = componentScan.getClass("nameGenerator");
		boolean useInheritedGenerator = (BeanNameGenerator.class == generatorClass);
		scanner.setBeanNameGenerator(useInheritedGenerator ? this.beanNameGenerator : BeanUtils.instantiateClass(generatorClass));

		// 3. 这两个属性和scope代理相关的，这里略过，使用较少
		ScopedProxyMode scopedProxyMode = componentScan.getEnum("scopedProxy");
		if (scopedProxyMode != ScopedProxyMode.DEFAULT) {
			// 3.1 用户设置了ScopedProxyMode
			scanner.setScopedProxyMode(scopedProxyMode);
		}
		else {
			// 3.2 默认的ScopedProxyMode -- 使用ScopeMetadataResolver
			Class<? extends ScopeMetadataResolver> resolverClass = componentScan.getClass("scopeResolver");
			scanner.setScopeMetadataResolver(BeanUtils.instantiateClass(resolverClass));
		}

		// 4. 控制scanner在basePackage下扫描时class文件的名字必须满足哪种格式。
		// 一般不设置 -- 默认值为：**/*.class  全扫嘛
		scanner.setResourcePattern(componentScan.getString("resourcePattern"));

		// 5. includeFilters和excludeFilters算是内部处理最复杂的逻辑了，但还好，对使用者是十分友好的
		for (AnnotationAttributes filter : componentScan.getAnnotationArray("includeFilters")) {
			// 5.1 为@ComponentScan的includeFilters属性转为TypeFilter
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addIncludeFilter(typeFilter);
			}
		}
		// 6. 设置excludeFilters
		for (AnnotationAttributes filter : componentScan.getAnnotationArray("excludeFilters")) {
			// 6.1 为@ComponentScan的excludeFilters属性转为TypeFilter
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addExcludeFilter(typeFilter);
			}
		}

		// 7. Spring4.1后出现的,@ComponentScan扫描的bean是支持的懒加载的哦
		boolean lazyInit = componentScan.getBoolean("lazyInit");
		if (lazyInit) {
			scanner.getBeanDefinitionDefaults().setLazyInit(true);
		}

		// 设置basePackages -- 前提是必须手动指定扫描的basePackages才可以哦
		// @ComponentScan的basePackages\basePackageClasses都是同时存在的哦
		// 8. 这里属于核心逻辑，核心逻辑，核心逻辑
		Set<String> basePackages = new LinkedHashSet<>();
		String[] basePackagesArray = componentScan.getStringArray("basePackages");
		// 8.1 Spring在此处有强大的容错处理，虽他是支持数组的，但是它这里也容错处理：支持,;换行等的符号分隔处理
		// 并且，并且更强大的地方在于：它支持${...}这种占位符的形式，非常的强大。我们可以动态的进行扫包了~~~~~厉害了我的哥
		for (String pkg : basePackagesArray) {
			String[] tokenized = StringUtils.tokenizeToStringArray(this.environment.resolvePlaceholders(pkg), ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
			Collections.addAll(basePackages, tokenized);
		}
		// 8.2 basePackageClasses有时候也挺好使的。它可以指定class，然后这个class所在的包（含子包）都会被扫描
		for (Class<?> clazz : componentScan.getClassArray("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}
		// 8.3 如果我们没有指定此值，它会取当前配置类所在的包 -- 比如SpringBoot就是这么来干的
		// declaringClass 是声明有@ComponentScan注解的类 
		// ❗️❗️❗️ -> 因此即使啥也不干,不指定basePackage或basePackageClass
		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(declaringClass));
		}

		// 9. 最后, 把@ComponentScan的属性都解析到scanner上后就doScan()
		// 在这之前添加一个默认的排除过滤器 -> 扫描basePackage下的class时忽略当前这个declaringClass哦
		scanner.addExcludeFilter(new AbstractTypeHierarchyTraversingFilter(false, false) {
			@Override
			protected boolean matchClassName(String className) {
				return declaringClass.equals(className);
			}
		});
		// 10. doScan() -> 默认情况会将整个项目中所有的@Component以及派生注解@Controller等的类的BeanDefinition加载到BeanDefinitionRegistry中去啦
		// 但是需要注意一点: doScan()仅仅是将@Component标注的组件类的加入到BeanDefinitionRegistry,当时里面其实是可能有LITE/FULL模式的配置类啊
		// 因此还需要将scanner.doScan()的结果返回给ConfigurationClassParser继续去从里面找到配置类继续解析哦
		// ❗️❗️❗️ -> 注意虽然这里是返回了Set<BeanDefinitionHolder>
		return scanner.doScan(StringUtils.toStringArray(basePackages));
	}

	private List<TypeFilter> typeFiltersFor(AnnotationAttributes filterAttributes) {
		
		// 1. filterAttributes 是 @ComponentScan中的includeFilters或excludeFilters的Filter数组属性哦
		List<TypeFilter> typeFilters = new ArrayList<>();
		// 2. 获取@Filter中的过滤的类型
		FilterType filterType = filterAttributes.getEnum("type");

		// 3. 获取classes属性数组 -- 非空可遍历 -- 意味着@Filter的type只能是ANNOTATION/ASSIGNABLE_TYPE/CUSTOM
		for (Class<?> filterClass : filterAttributes.getClassArray("classes")) {
			switch (filterType) {
				case ANNOTATION:
					// 3.1 扫描注解
					Assert.isAssignable(Annotation.class, filterClass,
							"@ComponentScan ANNOTATION type filter requires an annotation type");
					@SuppressWarnings("unchecked")
					Class<Annotation> annotationType = (Class<Annotation>) filterClass;
					typeFilters.add(new AnnotationTypeFilter(annotationType));
					break;
				case ASSIGNABLE_TYPE:
					// 3.2 扫描指定的Class
					typeFilters.add(new AssignableTypeFilter(filterClass));
					break;
				case CUSTOM:
					// 3.3 自定义扫描
					Assert.isAssignable(TypeFilter.class, filterClass,
							"@ComponentScan CUSTOM type filter requires a TypeFilter implementation");

					TypeFilter filter = ParserStrategyUtils.instantiateClass(filterClass, TypeFilter.class,
							this.environment, this.resourceLoader, this.registry);
					typeFilters.add(filter);
					break;
				default:
					throw new IllegalArgumentException("Filter type not supported with Class value: " + filterType);
			}
		}

		// 4. 获取pattern模式 -- 非空即可遍历-- 意味着@Filter的type只能是ASPECTJ/REGEX
		for (String expression : filterAttributes.getStringArray("pattern")) {
			switch (filterType) {
				case ASPECTJ:
					typeFilters.add(new AspectJTypeFilter(expression, this.resourceLoader.getClassLoader()));
					break;
				case REGEX:
					typeFilters.add(new RegexPatternTypeFilter(Pattern.compile(expression)));
					break;
				default:
					throw new IllegalArgumentException("Filter type not supported with String pattern: " + filterType);
			}
		}

		return typeFilters;
	}

}
