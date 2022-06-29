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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other {@link BeanFactoryPostProcessor}.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean @Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@code BeanFactoryPostProcessor} executes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {
	/*
	 * ❗️❗️❗️
	 * ConfigurationClassPostProcessor可以看出它是一个BeanFactoryPostProcessor，
	 * 并且它是个功能更强大些的BeanDefinitionRegistryPostProcessor，有能力去处理一些Bean的定义信息~
	 *
	 * ConfigurationClassPostProcessor是Spring内部对BeanDefinitionRegistryPostProcessor接口的唯一实现。
	 */

	/**
	 * A {@code BeanNameGenerator} using fully qualified class names as default bean names.
	 * <p>This default for configuration-level import purposes may be overridden through
	 * {@link #setBeanNameGenerator}. Note that the default for component scanning purposes
	 * is a plain {@link AnnotationBeanNameGenerator#INSTANCE}, unless overridden through
	 * {@link #setBeanNameGenerator} with a unified user-level bean name generator.
	 * @since 5.2
	 * @see #setBeanNameGenerator
	 */
	public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR = new FullyQualifiedAnnotationBeanNameGenerator();

	private static final String IMPORT_REGISTRY_BEAN_NAME = ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	private boolean localBeanNameGeneratorSet = false;

	/* Using short class names as default bean names by default. */
	private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	/* Using fully qualified class names as default bean names by default. */
	private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * Set the {@link BeanNameGenerator} to be used when triggering component scanning
	 * from {@link Configuration} classes and when registering {@link Import}'ed
	 * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
	 * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
	 * and a variant thereof for imported configuration classes (using unique fully-qualified
	 * class names instead of standard component overriding).
	 * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
	 * <p>This setter is typically only appropriate when configuring the post-processor as a
	 * standalone bean definition in XML, e.g. not using the dedicated {@code AnnotationConfig*}
	 * application contexts or the {@code <context:annotation-config>} element. Any bean name
	 * generator specified against the application context will take precedence over any set here.
	 * @since 3.1.1
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}


	/**
	 * Derive further bean definitions from the configuration classes in the registry.
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		// 根据BeanDefinitionRegistry,生成registryId 是全局唯一的。
		int registryId = System.identityHashCode(registry);
		//  判断，如果这个 registryId 已经被执行过了，就不能够再执行了，否则抛出异常
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		// 加入已经执行过的registry集合，防止重复执行
		this.registriesPostProcessed.add(registryId);

		// 调用processConfigBeanDefinitions 进行Bean定义的加载.代码如下
		processConfigBeanDefinitions(registry);
	}

	/**
	 * Prepare the Configuration classes for servicing bean requests at runtime
	 * by replacing them with CGLIB-enhanced subclasses.
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		int factoryId = System.identityHashCode(beanFactory);
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		if (!this.registriesPostProcessed.contains(factoryId)) {
			// BeanDefinitionRegistryPostProcessor hook apparently not supported...
			// Simply call processConfigurationClasses lazily at this point then.
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}
		// 增强配置类
		enhanceConfigurationClasses(beanFactory);
		// ❗️❗️❗️有一个点: 导入了 ImportAwareBeanPostProcessor
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
	}

	/**
	 * Build and validate a configuration model based on the registry of
	 * {@link Configuration} classes.
	 */

	// 核心: 处理配置类的bean定义信息
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		// 获取已经注册种子配置bean名称 -- 里面通常含有 启动类
		String[] candidateNames = registry.getBeanDefinitionNames();
		
		// 1. 根据注册的种子配置bean名称作为起点

		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			// 1.1 这个判断很有意思~~~ 如果你的beanDef现在就已经确定了是full或者lite，说明你肯定已经被解析过了，，所以再来的话输出个debug即可（其实我觉得输出warn也行~~~）
			if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			// 1.2 检查是否是配置类，不是配置类就返回false
			// 然后标记模型，full与lite模式
			// beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL/LITE)
			// 加入到configCandidates里保存配置文件类的定义
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName)); // BeanDefinitionHolder 持有BeanDefinition、BeanName以及alias别名数组
			}
		}

		// 2. 如果一个配置类都没找到，现在就不需要再继续下去了
		// 一般情况,candidateNames中的Bean都是启动类都是配置类
		if (configCandidates.isEmpty()) {
			return;
		}

		// 3. 把配置文件们：按照Order信息解析注解进行排序,这个意思是，我们配置类是支持order排序的。（备注：普通bean不行的~~~）
		// ❗️❗️❗️但是无法控制器加载顺序,这里仅仅是定义过下去配置类中进行处理的顺序哦
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		// 至此找到所有的配置类

		// Detect any custom bean name generation strategy supplied through the enclosing application context
		// 此处registry是DefaultListableBeanFactory这里会进去
		// 4. 确定给Bean扫描的命名方式，以及import方法的BeanNameGenerator赋值(若我们都没指定，那就是默认的AnnotationBeanNameGenerator：扫描为首字母小写，import为全类名)
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			// 4.1 如果局部没有设置过BeanNameGenerator
			if (!this.localBeanNameGeneratorSet) {
				// 4.1.1 就从SingletonBeanRegistry中获取命名生成器
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator; // 用于ComponentScan扫描时使用generator的beanName
					this.importBeanNameGenerator = generator; // 从外部导入的Generator的baneName
				}
			}
		}

		// 5. web环境，这里都设置了StandardServletEnvironment
		// 一般来说到此处，env环境不可能为null了~~~ 此处做一个容错处理~~~
		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// Parse each @Configuration class
		// 6. 这是重点：真正解析配置类的，其实是 ConfigurationClassParser 这个解析器来做的
		// parser 后面用于解析每一个配置类~~~~ -- 关键点
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates); // 从list转set去重
		// 装载已经处理过的配置类，最大长度为：configCandidates.size()
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
			// 6.1 核心方法：具体详解如下 -- 其中包括解析DeferredImportSelector
			parser.parse(candidates); // 核心到爆炸的方法❗️❗️❗️❗️❗️❗️❗️❗️❗️
			// 校验 配置类不能使final的，因为需要使用CGLIB生成代理对象，见postProcessBeanFactory方法
			parser.validate();
			
			// 上面完成的工作简单概述:
			// a: 配置类我们已经解析完毕,且全部存储到parser的configurationClasses -- 注意这里解析出的所有的配置类还没有被加载到BeanDefinitionRegistry,并且没有被@Condition做过判断哦
			// b: 配置类中的@Bean方法都已经存在在ConfigurationClass的beanMethods集合中
			// c: 配置类上的@ComponentScan也是去扫描符合的配置类继续递归处理完啦 -- @ComponentScan扫描的配置类都会直接入到BeanDefinitionRegistry中哦
			// e: 配置类上的@Import的ImportSelect执行完,然后再解析完所有配置类后[还未加入到BeanDefinitionRegistry]将@Import的DeferredImportSelector指定啦
			// f: 配置类上的@Import的ImportBeanDefinitionRegistrar没有执行,被存放在配置类ConfigurationClass的importBeanDefinitionRegistrars中
			// g: 配置类的内部类也作为配置类被解析,配置类的超类和接口中的@Bean方法的元数据也会放入ConfigurationClass的beanMethods集合中
			// h: 配置类中关于@ImportResource的信息存储 - @ImportResource

			// 6.3 上面解析过后：就有许多被解析出来的ConfigurationClass，只是没有注入到BeanDefinition中
			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			configClasses.removeAll(alreadyParsed); // 移出已经解析过的

			// 6.4 如果Reader为null，那就实例化ConfigurationClassBeanDefinitionReader来加载Bean，并加入到alreadyParsed中,用于去重（避免譬如@ComponentScan直接互扫）
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}

			// 此处注意：调用了ConfigurationClassBeanDefinitionReader的loadBeanDefinitionsd的加载配置文件里面的@Bean/@Import们，具体讲解请参见下面
			// 6.5 这个方法是非常重要的，因为它决定了向容器注册Bean定义信息的顺序问题~~~
			// 而且它能够触发 @Bean的方法的注入 哦 -- ❗️❗️❗️ ❗️❗️❗️ ❗️❗️❗️
			// 认识到@Bean的方法对应BeanDefinition是如何的
			this.reader.loadBeanDefinitions(configClasses);
			alreadyParsed.addAll(configClasses);

			candidates.clear();

			// 6.6 如果registry中注册的bean的数量 大于 之前获得的数量,则意味着在解析过程中又新加入了很多,那么就需要对其进行继续解析
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				String[] newCandidateNames = registry.getBeanDefinitionNames(); // 获取所有注入的类
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames)); // 已经解析过的一批
				Set<String> alreadyParsedClasses = new HashSet<>(); // 已经解析过的配置类
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				for (String candidateName : newCandidateNames) {

					// 这一步挺有意思：若老的oldCandidateNames不包含。也就是说你是新进来的候选的Bean定义们，那就进一步的进行一个处理
					// 比如这里的DelegatingWebMvcConfiguration，他就是新进的，因此它继续往下走
					// 这个@Import进来的配置类最终会被ConfigurationClassPostProcessor这个后置处理器的postProcessBeanFactory 方法，进行处理和cglib增强
					if (!oldCandidateNames.contains(candidateName)) {
						BeanDefinition bd = registry.getBeanDefinition(candidateName);

						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty()); // 直到候选队列为空

		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		// 如果SingletonBeanRegistry 不包含org.springframework.context.annotation.ConfigurationClassPostProcessor.importRegistry,
		// 这里会尝试注册一个bean 为 ImportRegistry. 一般都会进行注册的
		// 这里注册的目的是：为例后续实现@ImportAware接口而产生的
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		// 元数据阅读工厂属于缓存类型的，就清除元数据缓存
		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
	 * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
	 * Candidate status is determined by BeanDefinition attribute metadata.
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		// 对 BeanFactory 进行后处理以搜索配置类 BeanDefinitions；然后通过ConfigurationClassEnhancer增强任何候选者。候选状态由 BeanDefinition 属性元数据确定
		
		// 1. 首先需要知道一点: 那就是AbstractApplicationContext中执行BeanFactoryPostProcessor时
		// BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry执行的优先级比BeanFactoryPostProcessor#postProcessBeanFactory高
		// 也就意味着: processConfigBeanDefinitions 比 enhanceConfigurationClasses 执行更早
		// 也就意味着项目中所有的组件类/配置类/@Import/@ImportResource/@Bean等都已经被加入到BeanDefinitionRegistry中
		// 因此: 通过去BeanFactory中遍历所有BeanDefinition,查看是否有ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE即可确认当前BeanDefinition是否为配置类哦
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			// 1.1 遍历到配置类 -- 及持有属性ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE可以是FULL/LITE模式
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			AnnotationMetadata annotationMetadata = null;
			MethodMetadata methodMetadata = null;
			// 1.2 是否属于AnnotatedBeanDefinition -- 一般只有其实现类只有
			// 	a:ScannedGenericBeanDefinition -- @ComponentScan扫描到的 -- 同时属于AbstractBeanDefinition
			// 	b:ConfigurationClassBeanDefinition -- 配置类使用的BeanDefinition
			// 	c:AnnotatedGenericBeanDefinition -- @Bean的方法 -- 同时属于AbstractBeanDefinition
			if (beanDef instanceof AnnotatedBeanDefinition) {
				AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDef;
				annotationMetadata = annotatedBeanDefinition.getMetadata();
				methodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
			}
			// 1.3 触发情况
			//  a: beanDef属于配置类并且beanDef是AbstractBeanDefinition -- 配置类上面锁的b也会进入的
			//	b: beanDef属于AnnotatedBeanDefinition,同时属于AbstractBeanDefinition -- 上面说的1.2中a和c都是正确的
			if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
				// 配置类（FULL或LITE）或配置类派生的@Bean方法 -> 此时急切解析bean类，除非它是没有@Bean方法的简单组件类或者是“Lite”配置类。
				AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
				// 不具备BeanClass -- 源码: this.beanClass instanceof Class
				// 即BeanDefinition中的beanClass是String还是Class的 -> 是String就会进入下面这里
				if (!abd.hasBeanClass()) {
					// 是否为lite配置类,并且不包含@Bean的方法
					boolean liteConfigurationCandidateWithoutBeanMethods =
							(ConfigurationClassUtils.CONFIGURATION_CLASS_LITE.equals(configClassAttr) &&
								annotationMetadata != null && !ConfigurationClassUtils.hasBeanMethods(annotationMetadata));
					if (!liteConfigurationCandidateWithoutBeanMethods) {
						try {
							// a:不是Lite配置类
							// b:是Lite配置类,但是有@Bean修饰的方法
							// c:是Lite配置类,但是annotationMetadata不为空
							// resolveBeanClass()源码主要是:  ClassUtils.forName(className, classLoader);
							// 将对应的Class加载到内存中,并设置到beanClass上
							abd.resolveBeanClass(this.beanClassLoader);
						}
						catch (Throwable ex) {
							throw new IllegalStateException(
									"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
						}
					}
				}
			}
			// 1.4 FULL配置类,存入configBeanDefs中
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				}
				else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		// 没有FULL配置类,不需要做Enhance强制操作
		if (configBeanDefs.isEmpty()) {
			return;
		}

		// 有FULL配置类,拿到FULL配置类的BeanDefinition
		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// 如果@Configuration类希望被代理，那么总是代理目标类
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			// 获取用户指定FULL配置类的Class -- 即被代理类
			Class<?> configClass = beanDef.getBeanClass();
			// 开始代理对FULL配置类configClass进行代理增强
			Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
			if (configClass != enhancedClass) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
							"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
				}
				beanDef.setBeanClass(enhancedClass);
			}
		}
	}


	private static class ImportAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {
		/**
		 * 处理 ImportAware 接口
		 */

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessProperties method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			// 初始化之前进行处理
			// 实例化的bean接口是否实现了ImportAware
			if (bean instanceof ImportAware) {
				// 获取 ImportRegistry 注册表
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				// 然后获取这个 bean 导入的元数据
				AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
				// importingClass 不为null
				if (importingClass != null) {
					// 就直接注入
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
