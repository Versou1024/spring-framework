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

package org.springframework.context.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {
	/*
	 * ConfigurationClassBeanDefinitionReader功能：
	 * 读取一组已经被完整解析的配置类ConfigurationClass，基于它们所携带的信息向给定bean容器BeanDefinitionRegistry注册其中所有的bean定义。
	 *
	 * 该内部工具如上，由BeanDefinitionRegistryPostProcessor来使用。Spring中的责任分工是非常明确的：
	 * 		1、ConfigurationClassParser负责去找到所有的配置类。（包括做加强操作）
	 * 		2、然后交给ConfigurationClassBeanDefinitionReader将这些配置类中的bean定义注册到容器
	 *
	 * 该类只提供了一个public方法供外面调用：
	 * 这个方法是根据传的配置们，去解析每个配置文件所标注的@Bean们，一起其余细节~
	 */

	private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

	private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private final BeanDefinitionRegistry registry;

	private final SourceExtractor sourceExtractor;

	private final ResourceLoader resourceLoader;

	private final Environment environment;

	private final BeanNameGenerator importBeanNameGenerator;

	private final ImportRegistry importRegistry;

	private final ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@link ConfigurationClassBeanDefinitionReader} instance
	 * that will be used to populate the given {@link BeanDefinitionRegistry}.
	 */
	ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
			ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
			ImportRegistry importRegistry) {

		this.registry = registry;
		this.sourceExtractor = sourceExtractor;
		this.resourceLoader = resourceLoader;
		this.environment = environment;
		this.importBeanNameGenerator = importBeanNameGenerator;
		this.importRegistry = importRegistry;
		// 创建 @Condition 的运算器
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * Read {@code configurationModel}, registering bean definitions
	 * with the registry based on its contents.
	 */
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		// 对每个解析后的配置类做遍历（传递过来的Config配置文件的顺序还是挺重要的）
		for (ConfigurationClass configClass : configurationModel) {
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}

	/**
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 */
	private void loadBeanDefinitionsForConfigurationClass(ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {
		// ❗️❗️❗️
		// 从指定的一个配置类ConfigurationClass中提取bean定义信息并注册bean定义到bean容器 : 包括以下两点
		//	1. 配置类本身要注册为bean定义
		//	2. 配置类中的@Bean注解方法要注册为配置类

		// 这个方法很重要：它处理了多种方式（@Bean、实现接口类注册等等）完成向容器里注册Bean定义信息
		// 1、最先处理注册被@Import或者被外部配置类导入的配置类，判断依据是：configClass.isImported()。
		// 	 官方解释为：Return whether this configuration class was registered via @{@link Import} or automatically registered due to being nested within another configuration class
		// 	 这句话的意思是说通过@Import导入或者是一个内部类通过别的配置类放进来的都是被认为是导入进来的~~~~
		// 2、第二步开始注册@Bean进来的：
		// 		若是static方法，beanDef.setBeanClassName(configClass.getMetadata().getClassName()) + beanDef.setFactoryMethodName(methodName)；
		// 		若是实例方法：beanDef.setFactoryBeanName(configClass.getBeanName())+ beanDef.setUniqueFactoryMethodName(methodName) 总之对使用者来说 没有太大的区别
		// 3、注册importedResources进来的bean们。就是@ImportResource这里来的Bean定义
		// 4、执行ImportBeanDefinitionRegistrar#registerBeanDefinitions()注册Bean定义信息~（也就是此处执行ImportBeanDefinitionRegistrar的接口方法）
		// 原文链接：https://blog.csdn.net/f641385712/article/details/88095165

		// 1. 若ConfigClass需要被跳过，那就做做一些清理、移除的操作~~~~
		// 被跳过的情况: a-没有通过自身的@Condition系列注解;b-将当前configClass导入的配置类已经被跳过
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			// 1.1 从BeanDefinitionRegistry移除同名的beanName,并且将ImportStack中的beanName也移除出去
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}

		// 2. 稍微注意一下Spring处理这些Bean定义的顺序，在某些判断逻辑中或许能用到
		// isImported是在Spring中指的是，通过@Import导入或者作为一个内部类被别的配置类导入的都是被认为是导入进来的
		if (configClass.isImported()) {
			// 这个处理源码这里不分析了，比较简单 -- 主要是:ConfigurationClassParser解析出的配置类仅仅是加载有哪些加载的配置类 -- 并没有配置类的详细信息哦
			// 因此还需要解析配置类上的支持@Scope、@Lazy、@Primary、@DependsOn、@Role、@Description等等一些通用的基本属性解析到BeanDefinition中
			registerBeanDefinitionForImportedConfigurationClass(configClass);
		}
		// 3. ❗️❗️❗️处理方法上的@Bean的方法,也处理上面提到的那些通用注解@Lazy等等吧
		// 这里面只说一个内部比较重要的方法isOverriddenByExistingDefinition(beanMethod, beanName)
		// 该方法目的主要是去重。其实如果是@Configuration里面Bean重名了，IDEA类似工具发现，但是无法判断xml是否也存在（注意，发现归发现，但并不是编译报错哦~~~）
		// 它的处理策略为：若来自同一个@Configuration配置类，那就保留之前的。若来自不同配置类，那就覆盖
		for (BeanMethod beanMethod : configClass.getBeanMethods()) { // 获取使用@Bean注释的方式
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}
		
		// 4. 处理@ImportResource -- 解析里面的xml就是上面说到的解析xml的XmlBeanDefinitionReader
		// 所以，咱们@Configuration和xml是可以并行使用的
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
		
		// 5. ❗️❗️❗️
		// 解析咱们的ImportBeanDefinitionRegistrars
		// configClass.getImportBeanDefinitionRegistrars()：就是我们上面解析出的@Import的ImportBeanDefinitionRegistrar进去的那些注册器们
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());

		// 就这样通过这个Reader，把所有的Bean定义都加进容器了，后面就可以很方便的获取到了
	}

	/**
	 * Register the {@link Configuration} class itself as a bean definition.
	 */
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
		
		// 1. 创建 AnnotatedGenericBeanDefinition 定义信息
		AnnotationMetadata metadata = configClass.getMetadata();
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		// 2. 解析出@Scope对应的ScopeMetadata,然后设置到configBeanDef的scopeName上
		ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
		configBeanDef.setScope(scopeMetadata.getScopeName());
		
		// 3. 生成BeanName
		String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
		
		// 4. 处理配置类上常见的注解: @Lazy @Primary @DependsOn @Role @Description
		AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

		// 5. 创建BeanDefinitionHolder -- 主要目的是:根据需要去创建代理的BeanDefinition
		// 比如使用了@Scope(proxyMode=ScopedProxyMode.TARGET_CLASS)就需要为当前configBeanDef创建proxyBeanDef
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		
		// 6. 注册到BeanDefinition中
		this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
		
		// 7. 设置beanName
		configClass.setBeanName(configBeanName);

		if (logger.isTraceEnabled()) {
			logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
		}
	}

	/**
	 * Read the given {@link BeanMethod}, registering bean definitions
	 * with the BeanDefinitionRegistry based on its contents.
	 */
	@SuppressWarnings("deprecation")  // for RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		
		// 1. 获取@Bean标注的方法所在的配置类/方法元数据MethodMetadata/方法名methodName
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		MethodMetadata metadata = beanMethod.getMetadata();
		String methodName = metadata.getMethodName();

		// 2. 判断是否需要跳过这个方法，前提是@Bean标注的方法上有对应的@Conditional相关注解，没有的话，就不会进入下面的if块
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			// 缓存到需要跳过的BeanMethod集合中
			configClass.skippedBeanMethods.add(methodName);
			return;
		}
		
		// 3. 检查是否已经缓存在可跳过的BeanMethod集合中
		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}

		// 4. 获取@Bean注解,并获取@Bean注解上的name属性值
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		Assert.state(bean != null, "No @Bean annotation attributes");
		List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
		
		// 5. name属性中，如果有多个值，那么第一个name就是beanName，剩余下的beanName的别名 -- ❗️❗️❗️
		// 同时注意，如果names就是空的，那么就会用方法名作为beanName
		String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

		// 6. 注册别名
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// 7. 检查：beanName是否已经存在 --> 只要返回true,就会是的当前@Bean的方法无法被加载到BEanDefinitionRegistry中
		if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
			// 这里面有很关键的一个步骤，可能会设置 setNotUniqueFactoryMethodName 非唯一的FactoryMethodName，即方法名重复
			// 如果和configClass同名，直接报错吧
			if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
				throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
						beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
						"' clashes with bean name for containing configuration class; please make those names unique!");
			}
			return;
		}

		// 8. @Bean的方法是通过配置类导入的 -- 所以使用的是: ConfigurationClassBeanDefinition
		ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata, beanName);
		
		// 9. 设置source对象 -> sourceExtractor为PassThroughSourceExtractor,返回的就是metadata本身
		beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

		// 10.1 若为静态的@Bean方法
		if (metadata.isStatic()) {
			// 10.1.1 @Bean方法的beanDef设置beanClass或者beanClassName
			if (configClass.getMetadata() instanceof StandardAnnotationMetadata) {
				beanDef.setBeanClass(((StandardAnnotationMetadata) configClass.getMetadata()).getIntrospectedClass());
			}
			// 10.1.2 或者,设置@Bean方法的beanClassName为配置类的全限定类名
			else {
				beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			}
			// 10.1.3 设置独一无二FactoryMethod的名字，即@Bean的方法名
			beanDef.setUniqueFactoryMethodName(methodName); // 注意这里都是设置的唯一FactoryMethod
		}
		// 10.2 非静态的@Bean方法
		else {
			// 10.2.1 设置FactoryBean 名字，就是ConfigClass
			beanDef.setFactoryBeanName(configClass.getBeanName());
			// 10.2.2 设置FactoryMethod的名字，即@Bean的方法名
			beanDef.setUniqueFactoryMethodName(methodName); // 注意这里都是设置的唯一FactoryMethod
		}

		// 11. 设置已解析的factory-method
		if (metadata instanceof StandardMethodMetadata) {
			beanDef.setResolvedFactoryMethod(((StandardMethodMetadata) metadata).getIntrospectedMethod());
		}

		// 12. 设置装配模式，通过构造器
		beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		// 13. 设置属性，跳过@Required注解的检查
		beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

		// 14. 处理@Bean的方法上的常见的注解@Lazy、@Primary等等
		AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

		// 15. 解析@Bean中的属性autowire、autowireCandidate、initMethod、destroyMethod
		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			beanDef.setAutowireMode(autowire.value());
		}

		boolean autowireCandidate = bean.getBoolean("autowireCandidate");
		if (!autowireCandidate) {
			beanDef.setAutowireCandidate(false);
		}

		String initMethodName = bean.getString("initMethod");
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}

		String destroyMethodName = bean.getString("destroyMethod");
		beanDef.setDestroyMethodName(destroyMethodName);

		// 16. 开始考虑@Scope
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;
		AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (attributes != null) {
			beanDef.setScope(attributes.getString("value"));
			proxyMode = attributes.getEnum("proxyMode");
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// 17. 如有必要，将原始 bean 定义替换为目标
		// @Scope(proxyMode=ScopeProxyMode.TARGET_CLASS)
		BeanDefinition beanDefToRegister = beanDef;
		if (proxyMode != ScopedProxyMode.NO) {
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(new BeanDefinitionHolder(beanDef, beanName), this.registry, proxyMode == ScopedProxyMode.TARGET_CLASS);
			beanDefToRegister = new ConfigurationClassBeanDefinition((RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata, beanName);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Registering bean definition for @Bean method %s.%s()", configClass.getMetadata().getClassName(), beanName));
		}
		
		// 18. 最终还是注册到ioc容器中
		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}

	protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
		// 主要作用：如果一个configClass中有两个同名的Bean，例如@Bean method1，@Bean method1，他们的beanName都是method1
		// 再比如:@Bean("beanName1") method1;@Bean("beanName1") method2;他们的beanName都是beanName1

		// 1. 注册中心是否已包含beanName，不包含，就返回false，说明还不重复
		if (!this.registry.containsBeanDefinition(beanName)) {
			return false;
		}
		
		// 2. 取出已注册已存在的同名的existingBeanDef
		BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

		// 3. ConfigurationClassBeanDefinition -- 
		// RootBeanDefinition标记子类，用于表示 bean 定义是从配置类而不是任何其他配置源创建的。用于需要确定 bean 定义是否是在外部创建的 bean 覆盖情况
		// 如果已存在的existingBeanDef也是从配置类中创建的
		if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
			ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
			// 3.1 获取已有同名的BeanDefinition，进行检查
			// 缓存的FactoryMethod的configClass 是否 与当前 BeanMethod的configClass是同一个
			if (ccbd.getMetadata().getClassName().equals(beanMethod.getConfigurationClass().getMetadata().getClassName())) {
				// 3.2 若已注册的BeanDefinition的FactoryMethod的方法名，就是它的factoryMethodName
				// 就将其设置为 非唯一的 FactoryMethodName
				if (ccbd.getFactoryMethodMetadata().getMethodName().equals(ccbd.getFactoryMethodName())) {
					// 设置FactoryMethod为非唯一的
					ccbd.setNonUniqueFactoryMethodName(ccbd.getFactoryMethodMetadata().getMethodName());
				}
				return true;
			}
			// 3.2 只要不是同一个ConfigClass下的，就会返回false，没有重写
			else {
				return false;
			}
		}

		// 4. 是否为@ComponentScan扫描并注册的的BeanDefinition即一个BeanDefinition是通过ComponentScan扫描到的
		// 因为在解析@ComponentScan时会直接向BeanDefinitionRegistry中注册
		// 就允许重写
		if (existingBeanDef instanceof ScannedGenericBeanDefinition) {
			return false;
		}

		// 5. 如果现有的bean定义即existingBeanDef是被标记为框架生成的bean
		// 允许重写
		if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
			return false;
		}

		// 6. 在这一点上，它是一个顶级覆盖（可能是 XML），只是在配置类处理开始之前被解析......
		// registry是否开启 允许BeanDefinition的重写，若不允许就报错
		if (this.registry instanceof DefaultListableBeanFactory &&
				!((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding()) {
			throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
					beanName, "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef);
		}
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
					"already exists. This top-level bean definition is considered as an override.",
					beanMethod, beanName));
		}
		return true;
	}

	private void loadBeanDefinitionsFromImportedResources(Map<String, Class<? extends BeanDefinitionReader>> importedResources) {
		// 目的:处理@ImportResource导入的配置

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();

		// 1. 开始遍历resource，使用对应的readerClass处理
		importedResources.forEach((resource, readerClass) -> {
			// 1.1 检查对应的需要使用的readerClass -- 主要是根据resource后缀名判断的
			if (BeanDefinitionReader.class == readerClass) {
				//  从这里能够看出来，若我们自己没有指定BeanDefinitionReader，那它最终默认会采用XmlBeanDefinitionReader
				//  ~~~~~这就是为什么默认情况下，只支持导入xml文件的原因~~~~~
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else {
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

			// 1.2 该readerClass是否已经被缓存
			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// 1.2.1 没有缓存，就需要获取带有一个形参且为BeanDefinitionRegistry的构造器，并进行实例化
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// 1.2.2 如果reader是AbstractBeanDefinitionReader，还需要需要设置ResourceLoader、Environment
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					// 1.2.3 存入缓存
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Throwable ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}

			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			// 1.3 核心：开始加载这个@importResource加载的.xml中的定义的BeanDefinition
			reader.loadBeanDefinitions(resource);
		});
	}

	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		registrars.forEach((registrar, metadata) ->
				// 没什么多余的代码  所有的注册逻辑（哪些Bean需要注册，哪些不需要之类的，全部交给子类去实现）
				// 用处：上面有提到，比如@MapperScan这种批量扫描的===
				registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
	}


	/**
	 * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
	 * was created from a configuration class as opposed to any other configuration source.
	 * Used in bean overriding cases where it's necessary to determine whether the bean
	 * definition was created externally.
	 */
	@SuppressWarnings("serial")
	private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {
		// ConfigurationClassBeanDefinition是RootBeanDefinition标记子类，
		// 用于表示 bean 定义是从配置类而不是任何其他配置源创建的。用于需要确定 bean 定义是否是在外部创建的 bean 覆盖情况。
		// 比如@Bean引入的beanDefinition就是用的ConfigurationClassBeanDefinition
		
		/*
		 * 它有一些默认的设置处理如下：
		 *
		 * 1、如果@Bean注解没有指定bean的名字，默认会用方法的名字命名bean
		 * 2、@Configuration注解的类会成为一个工厂类，而所有的@Bean注解的方法会成为工厂方法，
		 * 	 通过工厂方法实例化Bean，而不是直接通过构造函数初始化（所以我们方法体里面可以很方便的书写逻辑。。。）
		 * ————————————————
		 * 版权声明：本文为CSDN博主「方向盘(YourBatman)」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
		 * 原文链接：https://blog.csdn.net/f641385712/article/details/88683596
		 */

		// @Bean方法所属的配置类的AnnotationMetadata
		private final AnnotationMetadata annotationMetadata; 

		// @Bean方法的工厂方法的元数据MethodMetadata
		private final MethodMetadata factoryMethodMetadata; 

		// @Bean导入的BeanDefinition的方法名 
		// @Bean的name属性的第一个值 > 默认的方法名
		private final String derivedBeanName;

		public ConfigurationClassBeanDefinition(ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {
			// @Bean方法所属的配置类的AnnotationMetadata
			this.annotationMetadata = configClass.getMetadata();
			// @Bean方法的工厂方法的元数据MethodMetadata
			this.factoryMethodMetadata = beanMethodMetadata;
			// @Bean导入的BeanDefinition的方法名 
			// @Bean的name属性的第一个值 > 默认的方法名 
			this.derivedBeanName = derivedBeanName;
			// 默认就是配置类的resource
			setResource(configClass.getResource());
			// 设置宽松的构造函数解析
			setLenientConstructorResolution(false);
		}

		public ConfigurationClassBeanDefinition(RootBeanDefinition original, ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {
			super(original);
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			this.derivedBeanName = derivedBeanName;
		}

		private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
			super(original);
			this.annotationMetadata = original.annotationMetadata;
			this.factoryMethodMetadata = original.factoryMethodMetadata;
			this.derivedBeanName = original.derivedBeanName;
		}

		@Override
		public AnnotationMetadata getMetadata() {
			return this.annotationMetadata;
		}

		@Override
		@NonNull
		public MethodMetadata getFactoryMethodMetadata() {
			return this.factoryMethodMetadata;
		}

		@Override
		public boolean isFactoryMethod(Method candidate) {
			return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate) &&
					BeanAnnotationHelper.determineBeanNameFor(candidate).equals(this.derivedBeanName));
		}

		@Override
		public ConfigurationClassBeanDefinition cloneBeanDefinition() {
			return new ConfigurationClassBeanDefinition(this);
		}
	}


	/**
	 * Evaluate {@code @Conditional} annotations, tracking results and taking into
	 * account 'imported by'.
	 */
	private class TrackedConditionEvaluator {

		// 缓存 - 配置类是否需要加载到BeanDefinitionRegistry的信息
		private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

		public boolean shouldSkip(ConfigurationClass configClass) {
			// 1. 缓存未命中
			Boolean skip = this.skipped.get(configClass);
			if (skip == null) {
				// 2. 配置类如果是导入的[a:被@Import导入;b:作为内部类被外部配置类导入]
				if (configClass.isImported()) {
					boolean allSkipped = true;
					// 3. 假设当前配置类A是通过配置类B导入的,那么configClass.getImportedBy返回配置类B
					// 如果说经过检查配置B能被加载到BeanDefinitionRegistry中,那么就当前配置类A就不需要跳过,也就是说allSkipped改为false
					for (ConfigurationClass importedBy : configClass.getImportedBy()) {
						if (!shouldSkip(importedBy)) {
							allSkipped = false;
							break;
						}
					}
					if (allSkipped) {
						// 配置类A被配置类B导入,现在配置类B被跳过,那么我们配置A也只能被跳过啦
						skip = true;
					}
				}
				if (skip == null) {
					// 若配置类B没有被跳过,就需要使用@Condition来判断是否需要跳过哦
					skip = conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN);
				}
				// 加入缓存
				this.skipped.put(configClass, skip);
			}
			return skip;
		}
	}

}
