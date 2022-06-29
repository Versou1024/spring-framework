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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import sun.tools.javac.SourceClass;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	/**
	 * Spring的工具类ConfigurationClassParser用于分析@Configuration注解的配置类，产生一组ConfigurationClass对象。
	 * 它的分析过程会接受一组种子配置类(调用者已知的配置类，通常很可能只有一个)，一般在SpringBoot中这个种子类就是启动类
	 * 从这些种子配置类开始分析所有关联的配置类，分析过程主要是递归分析配置类的注解@Import，配置类内部嵌套类，找出其中所有的配置类，然后返回这组配置类。
	 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/78549773
	 *
	 * 这个工具类自身的逻辑并不注册bean定义，它的主要任务是发现@Configuration注解的所有配置类并将这些配置类交给调用者(调用者会通过其他方式注册其中的bean定义)，
	 * 而对于非@Configuration注解的其他bean定义，比如@Component注解的bean定义，该工具类使用另外一个工具ComponentScanAnnotationParser扫描和注册它们。
	 *
	 * 一般情况下一个@Configuration注解的类只会产生一个ConfigurationClass对象，但是因为@Configuration注解的类可能会使用注解@Import引入其他配置类，也可能内部嵌套定义配置类，所以总的来看，ConfigurationClassParser分析一个@Configuration注解的类，可能产生任意多个ConfigurationClass对象。
	 */

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	// 默认的：对于java和org的元注解进行过滤掉
	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	// 存放已经被解析过的配置类
	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	// @PropertySource加载过的属性文件 -- 存储的就是@PropertySource的name属性
	// 通过@PropertySource加载的资源文件都会封装为PropertySource,然后封装Environment的组合数据源中
	// 因此可以通过${}去解析@PropertySource加载过的属性文件的key
	private final List<String> propertySourceNames = new ArrayList<>();

	// ImportStack 存入导入的类的栈
	private final ImportStack importStack = new ImportStack();

	// 专门用来处理 DeferredImportSelector 的处理器哦
	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		/*
		 * 接收外部提供的参数 configCandidates , 是一组需要被分析的候选配置类的集合，每个元素使用类型BeanDefinitionHolder包装 ;
		 * 注意配置类的定义是广泛的：例如@Configuration标注的fully的bean；类上带有@Component、@ComponentScan、@Import、@ImportResource；类中有@Bean标注的method也是lite模式的配置类
		 * parse() 方法针对每个候选配置类元素BeanDefinitionHolder，执行以下逻辑 :
		 * 		1.将其封装成一个ConfigurationClass
		 * 		2.调用processConfigurationClass(ConfigurationClass configClass)
		 * 	> 分析过的每个配置类都被保存到属性 this.configurationClasses 中。
		 * 然后再 ConfigurationClassPostProcessor#processConfigBeanDefinitions中解析过后，取出来this.configurationClasses然后进行一个递归分析
		 *
		 * 比如：第一批候选的配置类使用@Configuration标注的类a，类a头上有一个@Import，然后@Import导入的类，又是一个@Configuration标注的配置类，因此需要递归查找
		 */
		// 形参是：configCandidates 是已经筛选出来的配置类
		// 被ConfigurationClassPostProcessor#processConfigBeanDefinitions处理
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				// 根据类别做解析 - 最终都是processConfigurationClass
				// 这里根据Bean定义的不同类型走不同的分支，但是最终都会调用到方法
				//  processConfigurationClass(ConfigurationClass configClass)
				if (bd instanceof AnnotatedBeanDefinition) {
					// 但凡是通过注解标注的，都会走这里来解析
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
		// DeferredImportSelector 是 ImportSelector 的一个变种。
		// ImportSelector 被设计成其实和@Import注解的类同样的导入效果，但是实现 ImportSelector 的类可以条件性地决定导入哪些配置。
		// DeferredImportSelector 的设计目的是在所有其他的配置类被处理后才处理。这也正是该语句被放到本函数最后一行的原因。
		// ❗️❗️❗️这里执行 deferredImportSelectorHandler.process() 处理DeferredImportSelector的时候 ->
		// 实际上@Bean的方法还没处理\以及ConfigurationClass中的ImportBeanDefinitionRegistrar也还没执行勒
		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}


	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		/**
		 * processConfigurationClass()对配置的处理并不是真正自己处理，而是基于doProcessConfigurationClass()的处理循环，
		 * 该循环从参数配置类开始遍历其所有需要处理的父类(super)，每个类都使用doProcessConfigurationClass()来处理。
		 * 每处理一个类，processConfigurationClass()将其记录到this.configurationClasses
		 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/78549773
		 */

		// 1. 根据conditionEvaluator做条件判断的跳过操作 @ConditionOnClass 等
		// ConfigurationCondition继承自Condition接口
		// ConfigurationPhase.PARSE_CONFIGURATION 判断阶段为: 配置类解析过程中
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		// 2. 如果这个配置类已经存在了,后面又被间接@Import进来了~~~会走这里 然后做属性合并~
		// ConfigurationClass的hashCode()与equals()方法都是根据配置类的全限定类名做判断的哦
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		if (existingClass != null) {
			// 2.1 configClass、existingClass是都同一个配置类,且被不同的其他配置类导入进来的
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					// 2.2 合并二者的importedBy属性 -- 
					// 比如existingClass被配置类A导入\configClass被配置类B导入
					// 那么existingClass的导入类中合并有配置类A和配置类B
					existingClass.mergeImportedBy(configClass);
				}
				// 不需要继续执行,忽略掉
				return;
			}
			// 2.2 existingClass 不是被其他配置类导入的,本身就是候选的配置类
			// 那么候选的配置类A优先级 > 其他配置类导入的当前配置类A
			else {
				// 当前configClass不是导入的，
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		// Recursively process the configuration class and its superclass hierarchy.
		// 3. 请注意此处：while递归，只要方法不返回null，就会一直do下去~~~~~~~~
		// 从当前配置类configClass开始向上沿着类继承结构逐层执行doProcessConfigurationClass,
		// 直到遇到的父类是由Java提供的类结束循环
		SourceClass sourceClass = asSourceClass(configClass, filter);
		do {
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		// 每处理一个configClass，就会添加到configurationClasses，比如springboot中的应用程序启动类
		// 注意：它是一个LinkedHashMap，所以是有序的  这点还比较重要~~~~和bean定义信息息息相关
		// 需要被处理的配置类configClass已经被分析处理，将它记录到已处理配置类记录
		// 最后解析完，放入到已经解析的configurationClasses中，后面会被加载到BeanDefinition上
		// 一般至少包括: 
		// 0.初始化BeanFactoryRegistry中配置类也会加载到这里
		// 1.ComponentScan扫描的组件包中的配置类也会被加入到这里哦
		// 2.@Import中SelectImport类导入的配置类也会加入到这里哦
		// 3.配置类的内部配置类也会被加入到这里
		// 4.在this.deferredImportSelectorHandler.process()执行之后,通过DeferredImportSelector导入的配置类也会加载进来 -- 但是其所在位置,一定上面三个的后面
		// 因此SpringBoot中通过AutoConfigurationImportSelector这个DeferredImportSelector导入spring.factories都比项目中的优先级低哦
		// 但是note: @Import中的BeanDefinitionRegistryImport外的情况导入的配置类还不会被加入到这里,@Bean导入的配置类也不会被加入到这里
		// 需要在外部继续运算才可以哦
		this.configurationClasses.put(configClass, configClass);
		// configurationClasses 中的配置类的顺序也是极其重要的 -- 影响到后续配置类的BeanDefinition的注册顺序问题
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter) throws IOException {
		/*
		 * https://fangshixiang.blog.csdn.net/article/details/88095165
		 * doProcessConfigurationClass：用于解析配置类，加载到BeanDefinition中
		 * 1、内部配置类：–> 它里面还可以有普通配置类一模一样的功能，但优先级最高，最终会放在configurationClasses这个map的第一位
		 * 2、@PropertySource：这个和Bean定义没啥关系了，属于Spring配置PropertySource的范畴。这个属性优先级相对较低
		 * 3、@ComponentScan：注意，注意，注意重说三。 这里扫描到的Bean定义，就直接register注册了，直接注册了。所以它的时机是非常早的。
		 * 	（另外：如果注册进去的Bean定义信息如果还是配置类，这里会继续parse()，所以最终能被扫描到的组件，最终都会当作一个配置类来处理，所以最终都会放进configurationClasses这个Map里面去）
		 * 4、@Import：相对复杂点，如下：
		 * 		4.1:若就是一个普通类（标注@Configuration与否都无所谓反正会当作一个配置类来处理，也会放进configurationClasses缓存进去）
		 * 		4.2:实现了ImportSelector：递归最终都成为第一步的类。
		 * 			若实现的是DeferredImportSelector接口，它会放在deferredImportSelectors属性里先保存着，
		 * 			等着外部的所有的configCandidates配置类全部解析完成后，统一processDeferredImportSelectors()。它的处理方式一样的，最终也是转为第一步的类
		 * 		4.3:实现了ImportBeanDefinitionRegistrar：放在ConfigurationClass.importBeanDefinitionRegistrars属性里保存着
		 * 5、@ImportResource：一般用来导入xml文件。它是先放在ConfigurationClass.importedResources属性里放着
		 * 6、@Bean：找到所有@Bean的方法，然后保存到ConfigurationClass.beanMethods属性里
		 * 7、processInterfaces：处理该类实现的接口们的default方法且标注@Bean的默认方法
		 * 8、处理父类：拿到父类，每个父类都是作为配置类来处理（比如要有任何注解）。
		 *    备注：!superclass.startsWith("java")全类名不以java打头，且没有被处理过(因为一个父类可议N个子类，但只能被处理一次)
		 * 9、return null：若全部处理完成后就返回null，停止递归。
		 *
		 * 由上可见，这九步中，唯独只有@ComponentScan扫描到的Bean这个时候的Bean定义信息是已经注册上去了的，其余的都还没有真正注册到注册中心。
		 */
		
		// note: 执行完我们可以发现,ConfigurationClass中将有许多待处理的信息
		// 比如 配置类中的@Bean的方法Set<BeanMethod> + 配置类上的元注解@Import上有ImportBeanDefinitionRegistrar的实现类加入到Map<ImportBeanDefinitionRegistrar, AnnotationMetadata>
		// 以及 DeferredImportSelector 放入当前解析器的deferredImportSelectorHandler还准备处理勒

		/*
		 * 一个配置类的成员类(配置类内嵌套定义的类)也可能适配类，先遍历这些成员配置类，调用processConfigurationClass处理它们;
		 * 处理配置类上的注解@PropertySources,@PropertySource
		 * 处理配置类上的注解@ComponentScans,@ComponentScan
		 * 处理配置类上的注解@Import
		 * 处理配置类上的注解@ImportResource
		 * 处理配置类中每个带有@Bean注解的方法
		 * 处理配置类所实现接口的缺省方法
		 * 检查父类是否需要处理，如果父类需要处理返回父类，否则返回null
		 */

		// 先去看看内部类  这个if判断是Spring5.x加上去的，这个我认为还是很有必要的。
		// 因为@Import、@ImportResource这种属于lite模式的配置类，但是我们却不让他支持内部类了
		// ① 第一步，解析@Component中的内部类
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
			// 递归循环的解析内部类的@Component配置类（因此，即使是当前类中内部类是一个配置类，我们Spring也是支持的，很强大有木有）
			// 基本逻辑：内部类也可以有多个（支持lite模式和full模式，也支持order排序）
			// 若不是被import过的，那就顺便直接解析它（processConfigurationClass()）
			// 另外：该内部class可以是private也可以是static~~~(建议用private)
			// 所以可以看到，把@Bean方法定义在配置类的内部类里面，是有助于提升Bean的优先级的~~~~~
			// 因此也就说: 配置类的@Bean方法 没有 配置类的内部类中的@Bean方法 的优先级高的
			processMemberClasses(configClass, sourceClass, filter);
		}

		// Process any @PropertySource annotations
		// ② 处理@PropertySources注解和@PropertySource注解，交给processPropertySource去解析
		// 显然必须是ConfigurableEnvironment的环境采取解析，否则发出警告：会忽略这个不进行解析
		// 主要是将@PropertySource指明的资源文件
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(sourceClass.getMetadata(), PropertySources.class, org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				// propertySource 是 注解@PropertySource 的属性
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// Process any @ComponentScan annotations
		// ❗️❗️❗️
		// ③ 解析@ComponentScans和@ComponentScan注解，进行包扫描。最终交给ComponentScanAnnotationParser#parse方法进行处理
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		// 这里再次使用到conditionEvaluator,不过阶段是不同的,这里是ConfigurationPhase.REGISTER_BEAN在注册bean到ioc容器中进行判断
		// why: 因为@ComponentScan扫描到的配置类都会被直接加入到BeanFactory中,很快哦
		if (!componentScans.isEmpty() && !this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// 扫描@ComponentScan下需要加载的BeanDefinition -> 一般都是@Component注解或者其派生注解标注的组件类
				// note：@ComponentScan下需要加载的BeanDefinition，都会被提前加载到BeanDefinitionRegistry中
				Set<BeanDefinitionHolder> scannedBeanDefinitions = this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// 这一步非常重要：如果被@ComponentScan扫描的Bean，还是属于配置类，那就继续调用本类的parse方法，进行递归解析==============
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					// 注意使用的是: 原始BeanDefinition,因为带有@Component元注解的组件类如果使用@Scope(proxyMode=ScopedProxyMode.TARGET_CLASS),
					// 那么就会为本来的BeanDefinition代理一个RootBeanDefinition
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					// 若属于配置类需要递归parse()处理
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// ④ 如果有配置类上有@Import注解,就需要进行额外处理processImports()
		// processImports()逻辑如下:
		// 1. 检查@Import的是否为SelectImport,是的话直接调用其selectImports()方法,然后继续调用processImports()检查导入的类中是否有继续使用@Import的
		// 2. 检查@Import的是否为DeferredImportSelector,是的话,使用deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector)延迟保存起来,后续再使用
		// 3. 检查@Import的是否ImportBeanDefinitionRegistrar,是的话,就加入到ConfigurationClassParser的importBeanDefinitionRegistrars容器中
		// 4. 没有@Import,那就继续做processConfigurationClass()检查是否为配置类...等系列操作
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// ⑤ 显然，先是处理了@Import，才过来解析@ImportResource的====最终交给environment.resolveRequiredPlaceholders(resource)去处理了
		// 一般用的很少,@ImportResource一般是用来导入相关的xml文件的
		AnnotationAttributes importResource = AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			// 利用environment以及加载指定locations中的资源，作为占位符填充的来源
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				// 解析占位符${}
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				// 向configClass中注入导入的资源
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// ⑥ ❗️❗️❗️❗️❗️❗️处理被标注了@Bean注解的方法们
		// 遍历@Bean注释的方法,添加到configClass中的BeanMethod
		// 这里需要注意的是：最终会实例化的时候是执行此工厂方法来获取到对应实例的
		// if (mbd.getFactoryMethodName() != null) { ... }  这里会是true，从而执行此方法内部逻辑。   原理同XML中的FactoryMethod方式创建Bean
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass)); // 添加到configClass中的BeanMethod集合中
		}

		// Process default methods on interfaces
		// ⑦ 这个特别有意思：处理接口中被@Bean注解默认方法,代码如下
		// 因为JDK8以后接口可以写default方法了，所以接口竟然也能给容器里注册Bean了
		// 但是需要注意：这里的意思并不是说你写个接口然后标注上@Configuration注解，然后@Bean注入就可以了
		// 这个解析的意思是我们的配置类可以实现接口，然后在所实现的接口里面若有@Bean的注解默认方法，是会加入到configClass的beanMethod集合的
		processInterfaces(configClass, sourceClass);

		// Process superclass, if any
		// ⑧ 如果有父类的话,则返回父类进行进一步的解析,否则返回null
		// 这个也是很厉害的，如果有父类，也是能够继续解析的。@EnableWebMvc中的DelegatingWebMvcConfiguration就是这么玩的
		// 它自己标注了@Configuration注解，但是真正@Bean注入，都是它父类去干的
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			// 父类不为空，且父类不能是java的父类，也不能是已知解析过的superClass，就允许继续操作
			if (superclass != null && !superclass.startsWith("java") && !this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// 有父类，就会返回父类，然后调用方法中，继续递归处理，直到没有父类为止
				// 		SourceClass sourceClass = asSourceClass(configClass, filter);
				//		do {
				//			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
				//		}
				//		while (sourceClass != null);
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		// 没有父类，就停止了，处理结束
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter) throws IOException {
		// 注册成员类：注册恰好是配置类本身的成员（嵌套）类

		// 1. 获取成员类 -> 也就是通常讲的内部类
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				// 2. 检查内部成员类，是否有资格作为FULL/LITE的配置类
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) && !memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			// 3. 对属于配置类的内部类排序
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				// 3.1 如果已经在解析栈中，就抛出异常 --> 出现循环的Import问题需要抛出异常
				// 通过problemReporter抛出异常即可
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				// 3.2 加入到importStack栈中
				else {
					this.importStack.push(configClass);
					try {
						// 3.2.1 调用 - processConfigurationClass递归处理内部配置类
						// 递归处理 -- 注意避免: 上面的循环导入问题哦 -> CircularImportProblem
						// ❗️❗️❗️candidate.asConfigClass(configClass) 注意这里的转为configClass的处理
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		// 获取配置类上的所有接口
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			// 获取接口中@Bean的方法的方法元注解
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			// 接口中@Bean只能修饰default默认方法，不支持abstract
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// 处理结果会，直接添加到configClass的beanMethod集合中
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			// 递归处理接口
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		// ❗️❗️❗️ 检索sourceClass中@Bean method转换为MethodMetadata
		
		AnnotationMetadata original = sourceClass.getMetadata();
		
		// 1. 获取带有@Bean的方法
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// 2. 尝试通过ASM读取类文件以确定@Bean方法的声明顺序。。。[note: @Bean方法声明顺序比不意味着加载顺序哦 -- 切记切记]
			// 不幸的是，JVM的标准反射以任意形式返回方法顺序，即使在同一JVM上同一应用程序的不同运行之间也是如此。
			try {
				// 3. 使用元数据Reader工厂,为指定的originalClass生成MetadataReader,调用其AnnotationMetadata获取注解信息
				AnnotationMetadata asm = this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								// 4. 纠正顺序，顺序以asmMethods为主，数据以Java反射的beanMethod为主
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		// 1. 获取@PropertySource的name属性 -- 指定资源名称，如果为空
		// ❗️❗️❗️ 资源名是很重要的,作为资源的唯一标识符
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		// 2. 获取@PropertySource的encoding属性 -- 编码格式
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		// 3. 获取@PropertySource的value属性 -- ❗️❗️❗️ 它指定资源路径
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		// 4. 获取@PropertySource的ignoreResourceNotFound属性 -- 是否忽略Resource不存在
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		// 5. 没有指定factory时，就使用默认的factory
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ? DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		// 6. 解析locations
		for (String location : locations) {
			try {
				// 7. 解析${}
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				// 8. 获取对应Resource
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				// 9. 添加属性源头 -- over
				// EncodedResource 持有 Resource 和 对应的编解码格式
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	// 把属性资源添加进来，最终全部要放进 MutablePropertySources 里  这点非常重要~~~~ 这个时机
	private void addPropertySource(PropertySource<?> propertySource) {
		// 1. 资源名
		String name = propertySource.getName();
		// 2. 多value的Map 存储PropertySources
		// MutablePropertySources它维护着一个List<PropertySource<?>> 并且是有序的~~~
		// 从 environment 中获取
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		// 3. 若propertySourceNames已经包含这个资源,就继续扩展吧PropertySource
		// 此处若发现你的同名PropertySource已经有了，还是要继续处理的~~~而不是直接略过
		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			// 根据此name拿出这个PropertySource~~~~若不为null
			// 3.1 下面就是做一些属性合并的工作~~~~~
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					// 3.2 已经存在的PropertySource已经是组合模式的PropertySource,就将newSource添加到第一个位置
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					// 3.3 复合PropertySource,并进行替换之前的PropertySource
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		// 4. 这段代码处理的意思是：若你是第一个自己导入进来的，那就放在最末尾
		// 若你不是第一个，那就把你放在已经导入过的最后一个的前一个里面~~~
		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		}
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		
		// 装载所有的搜集到的import
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		// 将imports与visited传入
		// 下面递归处理所有注解：会将@Import注解放入imports，递归处理中所有处理过的都放入visited中
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited) throws IOException {
		// 递归收集所有声明的@Import值。与大多数元注释不同，使用不同的值声明多个@Imports是有效的；从类的第一个元注释返回值的常规过程是不够的。
		// 例如，@Configuration类除了声明源自@Enable注释的元导入之外，还经常声明直接@Imports。
		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					// 不是Import注解，需要递归检查元注解
					collectImports(annotation, imports, visited);
				}
			}
			// imports中存储的是配置类上所有的@Import的value值
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass, Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter, boolean checkForCircularImports) {
		// 逻辑如下:
		// 1. 检查@Import的是否为SelectImport,是的话直接调用其selectImports()方法,然后继续调用processImports()检查导入的类中是否有继续使用@Import的
		// 2. 检查@Import的是否为DeferredImportSelector,是的话,使用deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector)延迟保存起来,后续再使用
		// 3. 检查@Import的是否ImportBeanDefinitionRegistrar,是的话,就加入到ConfigurationClassParser的importBeanDefinitionRegistrars容器中
		// 4. 没有@Import,那就继续做processConfigurationClass()检查是否为配置类...等系列操作

		// 1. 如果配置类上没有任何候选@Import，说明没有需要处理的导入，则什么都不用做，直接返回
		// note：获取@Import是递归获取，任意子类父类上标注有都行的
		if (importCandidates.isEmpty()) {
			return;
		}

		// 2. 循环导入检查：如果存在循环导入的话,则直接抛出异常(比如你@Import我，我@Import你这种情况)
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		// 3. 开始处理配置类configClass上所有的@Import的候选类 -> 即importCandidates候选集合
		else {
			this.importStack.push(configClass);
			try {
				// 4. 依次处理每个@Import里面候选的Bean们
				// 循环处理每一个@Import,每个@Import可能导入三种类型的类 :
				//  ImportSelector -> ImportBeanDefinitionRegistrar -> 其他类型，都当作配置类处理，也就是相当于使用了注解@Configuration的配置类
				// 下面的for循环中对这三种情况执行了不同的处理逻辑
				// 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/78549773
				for (SourceClass candidate : importCandidates) {
					// 4.1 如果实现了ImportSelector接口（又分为两种，因为有子接口DeferredImportSelector存在）
					if (candidate.isAssignable(ImportSelector.class)) {
						Class<?> candidateClass = candidate.loadClass();
						// 4.1.1 ImportSelector的实现类必须有空的构造函数，把这个Bean实例化出来，
						// ImportSelector的实现类可以实现包括environment、resourceLoader、registry等等感知接口（实现了DeferredImportSelector也在此处注入了哦）
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class, this.environment, this.resourceLoader, this.registry);
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							// 4.1.2 并联所有的exclusionFilter
							exclusionFilter = exclusionFilter.or(selectorFilter); 
						}
						if (selector instanceof DeferredImportSelector) {
							// 4.1.3 selector 属于 DeferredImportSelector 延迟类型的, 会在所有其他的配置类的BeanDefinition被加载到BeanDefinitionRegistry后,才会去执行DeferredImportSelector的操作
							// 放入集合 deferredImportSelectorHandler，后续再使用，即parse(Set<BeanDefinitionHolder> configCandidates)最后一行代码的process
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							// 4.1.4 普通的ImportSelector就直接运行啦，调用其selectImports
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							// 4.1.5 继续递归处理，ImportSelector#selectImports返回需要加载的全限定类名，并转为SourceClass，继续调用processImports
							// 因为可能存在，a @Import b，b 又去 @Import 其他bean
							// 经过递归处理，最后回到最下面的else块中进行processConfigurationClass() -> 因此会继续处理@Import导入的组件类哦
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
						// ❗️️note❗: DeferredImportSelector中继承的ImportSelector的selectImports()并不会立即执行
					}
					// 4.2 属于 ImportBeanDefinitionRegistrar 类型的
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// 4.2.1 加载Class信息到JVM中,并实例化这个Class对象
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar = ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class, this.environment, this.resourceLoader, this.registry);
						// 注入到 configClass 的 importBeanDefinitionRegistrars 中，目前并不会直接调用其loadBeanDefinition
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					// 4.3 如果@Import中的SelectImport#selectImports()返回的的全限定类名上面不再有@Import就不需要继续递归proceeImport() -- 99.99%的情况都是这样的
					// 即上面的4.1.5执行之后,基本都会进入这里的,继续作为配置类继续解析processConfigurationClass
					else {
						// 4.3.1 注册到importStack中,currentSourceClass.getMetadata()通过@Import导入了candidate.getMetadata().getClassName()
						this.importStack.registerImport(currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						// 如果是@Import直接导入的带有@Configuration的配置类或者普通类
						// 或者是上面递归处理@ImportSelector返回的加载集合，继续作为配置类递归处理即可
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		// 例如 a @Import b，b 是一个配置类，b 中通过@Bean 导入了 a，这就出现了循环链式

		// importStack 是否存在当前configClass是@Import被导入的
		// 存在就表明，这个configClass是被别的@Import导入的
		if (this.importStack.contains(configClass)) {
			// 存在的话，获取configClassName
			String configClassName = configClass.getMetadata().getClassName();
			// 获取configClass对应的@Import的注解信息
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				// 一旦：通过@Import注入的类就是配置类本身，认为是链式导入
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		// 不存在，没有循环Import
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		
		// 1. 获取注解元信息 -> 调用asSourceClass()常见SourceClass
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		// 从Class获取ConfigurationClassParser.SourceClass的工厂方法。
		
		// 1. classType为空,或者满足filter,就直接返回objectSourceClass
		// 即  new ConfigurationClassParser.SourceClass(Object.class)
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		// 类名classNames中获取ConfigurationClassParser.SourceClass集合 -- 通过filter进行过滤
		// 默认的filter就是 DEFAULT_EXCLUSION_FILTER
		
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		// 一般是使用默认的DEFAULT_EXCLUSION_FILTER进行过滤即可
		
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				// 这里会涉及到ClassName的实例化操作哦
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {
		// 实现了：ImportRegistry接口
		// 因此有一个容器：key为导入的importClass，value就是importingClass即@Import注解元数据

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			// importingClass 是通过@Import作为元注解后将 importedClass 加载的ioc容器
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {
		
		// 聚合 DeferredImportSelectorHolder
		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			
			// 1.封装起来：holder
			// configClass就是元注解有@Import的配置类, importSelector就是@Import导入的DeferredImportSelector类型的
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			// 2. 初始化的情况下，deferredImportSelectors不等于null
			if (this.deferredImportSelectors == null) {
				// DeferredImportSelector 有分组属性，因此封装完Holder后交给分组Handler来进行处理
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				// 注册到分组Handler的Map结构
				handler.register(holder);
				// 注册完毕后，开始处理分组导入
				handler.processGroupImports();
			}
			else {
				// 2.2 直接将封装的holder添加到deferredImportSelectors
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			// 处理方式：先用临时变量复制，然后设置为空，最终再替换为一个新的数组
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null; // 一种标志位的处理
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					// 支持排序
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					// 遍历向DeferredImportSelectorGroupingHandler注册每个deferredImports
					deferredImports.forEach(handler::register);
					// 处理分组导入
					handler.processGroupImports();
				}
			}
			finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {
		
		// key为的group，value为DeferredImportSelectorGrouping[封装对象，持有group与对应分组的DeferredImportSelector的list集合]
		// 对于没有group的DeferredImportSelector其key就是本身,
		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		// key为DeferredImportSelector的配置类的AnnotationMetadata,value就是配置类
		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			// 向Handler注册deferredImportSelector操作：
			
			// 1. 获取DeferredImportSelector对应的分组对象
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			// 2. 查看group对应的DeferredImportSelectorGrouping,如果group为null,那就以deferredImport本身作为key
			// 返回对应分组的grouping
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent((group != null ? group : deferredImport), key -> new DeferredImportSelectorGrouping(createGroup(group)));
			// 3. 向grouping分组中添加这个deferredImport
			grouping.add(deferredImport);
			// 4. 存储对应的注解元数据与配置类
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(), deferredImport.getConfigurationClass());
		}

		public void processGroupImports() {
			// 1. 从groupings中获取每一个分组对应的DeferredImportSelectorGrouping [持有该分组下的所有的DeferredImportSelector集合]
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				// 2. 获取分组中的候选者Filter
				// 简单说: 将DEFAULT_EXCLUSION_FILTER和grouping的deferredImportSelector的集合每一个的getExclusionFilter()的过滤器做一个Or联合
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				// 3. 获取当前分组注册的所有的DeferredImportSelector
				// 获取到当前分组grouping想要注入的class封装的entry，进行遍历即可
				// ❗️❗️❗️grouping.getImports() 将触发 DeferredImportSelector.Group#process()和selectImports()两个方法
				// 返回当前分组按照其逻辑处理完当前分组下所有的DeferredImportSelector后，就可以通过其selectImports返回当前分组想要提供给Spring的DeferredImportSelector
				grouping.getImports().forEach(entry -> {
					// 4. 获取导入DeferredImportSelector对应的配置类
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						// ❗️❗️❗️
						// 5. 最终递归回到：processImports的处理哦
						// processImports()的逻辑已经讲过这里不再啰嗦
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {
		// 封装 - DeferredImportSelector

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		// 封装 - 持有group与对应DeferredImportSelectorHolder集合
		// 可以认为是一个entry项，持有key和value

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			// ❗️❗️❗️
			
			// 1. 遍历 deferredImports 数组，即对整个Group中的deferredImport调用process
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				// 2. 调用groupE#process()方法
				// 将配置类的元注解以及当前分组下遍历到的deferredImport进行处理
				// ❗️❗️❗️
				// 可以发现: DeferredImportSelector.Group#process()允许对配置类和DeferredImportSelector进行一下额外的处理
				// 然后再去执行 DeferredImportSelector#selectImports()
				this.group.process(deferredImport.getConfigurationClass().getMetadata(), deferredImport.getImportSelector());
			}
			// 3. 然后调用 group 的 selectImports
			// 当前分组按照其逻辑处理完当前分组下所有的DeferredImportSelector后，就可以通过其selectImports获取当前分组想要提供给Spring的DeferredImportSelector
			// 返回的 Group.Entry 就封装有用户想要提供DeferredImportSelector以及对应的配置类
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
			// 将DEFAULT_EXCLUSION_FILTER和deferredImports中getExclusionFilter()的过滤器做一个Or联合
			
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {
		/**
		 * 实现了Ordered接口
		 * 1、保存一个source，可以是Class，也可以是MetadataReader
		 * 2、保存一个对应的注解元数据 AnnotationMetadata
		 */

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				// 调用 AnnotationMetadata#introspect
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			// 1. 转换为配置类，ConfigurationClass
			// 传递进来的 importedBy 将当前 sourceClass 导入容器的类
			// 注意这个导入:
			// 	a: 可以是配置类A中的有一个内部类B是配置类,那么配置类A导入了内部配置类B
			//  b: 可以是配置类A通过元注解@Import中的导入了配置B,那么也是配置类A导入了内部配置类B
			// 其余情况都不算作是导入哦
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		// 获取source中的内部成员类
		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
