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

package org.springframework.beans.factory.annotation;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessor}
 * implementation that autowires annotated fields, setter methods, and arbitrary
 * config methods. Such members to be injected are detected through annotations:
 * by default, Spring's {@link Autowired @Autowired} and {@link Value @Value}
 * annotations.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
 * if available, as a direct alternative to Spring's own {@code @Autowired}.
 *
 * <h3>Autowired Constructors</h3>
 * <p>Only one constructor of any given bean class may declare this annotation with
 * the 'required' attribute set to {@code true}, indicating <i>the</i> constructor
 * to autowire when used as a Spring bean. Furthermore, if the 'required' attribute
 * is set to {@code true}, only a single constructor may be annotated with
 * {@code @Autowired}. If multiple <i>non-required</i> constructors declare the
 * annotation, they will be considered as candidates for autowiring. The constructor
 * with the greatest number of dependencies that can be satisfied by matching beans
 * in the Spring container will be chosen. If none of the candidates can be satisfied,
 * then a primary/default constructor (if present) will be used. If a class only
 * declares a single constructor to begin with, it will always be used, even if not
 * annotated. An annotated constructor does not have to be public.
 *
 * <h3>Autowired Fields</h3>
 * <p>Fields are injected right after construction of a bean, before any
 * config methods are invoked. Such a config field does not have to be public.
 *
 * <h3>Autowired Methods</h3>
 * <p>Config methods may have an arbitrary name and any number of arguments; each of
 * those arguments will be autowired with a matching bean in the Spring container.
 * Bean property setter methods are effectively just a special case of such a
 * general config method. Config methods do not have to be public.
 *
 * <h3>Annotation Config vs. XML Config</h3>
 * <p>A default {@code AutowiredAnnotationBeanPostProcessor} will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom {@code AutowiredAnnotationBeanPostProcessor} bean definition.
 *
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection;
 * thus the latter configuration will override the former for properties wired through
 * both approaches.
 *
 * <h3>{@literal @}Lookup Methods</h3>
 * <p>In addition to regular injection points as discussed above, this post-processor
 * also handles Spring's {@link Lookup @Lookup} annotation which identifies lookup
 * methods to be replaced by the container at runtime. This is essentially a type-safe
 * version of {@code getBean(Class, args)} and {@code getBean(String, args)}.
 * See {@link Lookup @Lookup's javadoc} for details.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.5
 * @see #setAutowiredAnnotationType
 * @see Autowired
 * @see Value
 */
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {
	// 当Bean进行初始化完成之后会populateBean()对它的属性进行赋值，这个时候AutowiredAnnotationBeanPostProcessor这个后置处理器生效，从而对属性进行依赖注入赋值。
	//
	// AutowiredAnnotationBeanPostProcessor它能够处理@Autowired和@Value注解~
	//
	// 注意：因为@Value是BeanPostProcessor来解析的，所以具有容器隔离性（本容器内的Bean使用@Value只能引用到本容器内的值哦~，因为BeanPostProcessor是具有隔离性的）
	// 推荐：所有的@Value都写在根容器（也就是我们常说的Service容器）内，请不要放在web容器里。也就是说，请尽量不要在controller从使用@Value注解，因为业务我们都要求放在service层
	// 三层架构：Controller、Service、Repository务必做到职责分离和松耦合~
	// 但SpringBoot中只有一个容器 -- 可忽略不计

	protected final Log logger = LogFactory.getLog(getClass());

	// 关注的候选注解：主要就是关注@Autowrited与@Value注解，后续用于过滤
	// 该处理器支持解析的注解们~~~（这里长度设置为4）  默认支持的是3个（当然你可以自己添加自定义的依赖注入的注解   这点非常强大）
	private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

	// @Autowired(required = false)这个注解的属性值名称
	private String requiredParameterName = "required";

	private boolean requiredParameterValue = true;

	private int order = Ordered.LOWEST_PRECEDENCE - 2;

	@Nullable
	private ConfigurableListableBeanFactory beanFactory; // BeanFactoryAware引入

	//
	private final Set<String> lookupMethodsChecked = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	//
	private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);

	// 缓存处理过的：beanName->InjectionMetadata
	// 方法注入、字段filed注入  本文的重中之重
	// 此处InjectionMetadata这个类非常重要，到了此处@Autowired注解含义已经没有了，完全被准备成这个元数据了  所以方便我们自定义注解的支持~~~优秀
	// InjectionMetadata持有targetClass、Collection<InjectedElement> injectedElements等两个重要属性
	// 其中InjectedElement这个抽象类最重要的两个实现为：AutowiredFieldElement和AutowiredMethodElement
	private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * Create a new {@code AutowiredAnnotationBeanPostProcessor} for Spring's
	 * standard {@link Autowired @Autowired} and {@link Value @Value} annotations.
	 * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
	 * if available.
	 */
	@SuppressWarnings("unchecked")
	public AutowiredAnnotationBeanPostProcessor() {
		// 为 Spring 的标准@Autowired和@Value注解创建一个新的AutowiredAnnotationBeanPostProcessor
		// autowiredAnnotationTypes 用来存储需要处理的注解class
		// AutowiredAnnotationBeanPostProcessor 用来处理Autowrited、value、以及Inject注解、
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}


	/**
	 * Set the 'autowired' annotation type, to be used on constructors, fields,
	 * setter methods, and arbitrary config methods.
	 * <p>The default autowired annotation types are the Spring-provided
	 * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
	 * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a member is supposed
	 * to be autowired.
	 */
	public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
		// 设置 'autowired' 注释类型，用于构造函数、字段、setter 方法和任意配置方法。
		// 默认的自动装配注释类型是 Spring 提供的@Autowired和@Value注释以及 JSR-330 的@Inject注释（如果可用）。

		Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.add(autowiredAnnotationType);
	}

	/**
	 * Set the 'autowired' annotation types, to be used on constructors, fields,
	 * setter methods, and arbitrary config methods.
	 * <p>The default autowired annotation types are the Spring-provided
	 * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
	 * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation types to indicate that a member is supposed
	 * to be autowired.
	 */
	public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
		Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
	}

	/**
	 * Set the name of an attribute of the annotation that specifies whether it is required.
	 * @see #setRequiredParameterValue(boolean)
	 */
	public void setRequiredParameterName(String requiredParameterName) {
		this.requiredParameterName = requiredParameterName;
	}

	/**
	 * Set the boolean value that marks a dependency as required.
	 * <p>For example if using 'required=true' (the default), this value should be
	 * {@code true}; but if using 'optional=false', this value should be {@code false}.
	 * @see #setRequiredParameterName(String)
	 */
	public void setRequiredParameterValue(boolean requiredParameterValue) {
		this.requiredParameterValue = requiredParameterValue;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	// bean工厂必须是ConfigurableListableBeanFactory的（此处放心使用，唯独只有SimpleJndiBeanFactory不是它的子类而已~）
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}


	// 第一个非常重要的核心方法~~~
	//它负责1、解析@Autowired等注解然后转换
	// 2、把注解信息转换为InjectionMetadata然后缓存到上面的injectionMetadataCache里面
	// postProcessMergedBeanDefinition的执行时机非常早，在doCreateBean()前部分完成bean定义信息的合并
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		// MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
		// 这里会提前使用findAutowiringMetadata()方法，使得injectionMetadataCache有缓存
		// 因此 postProcessProperties 就会从缓存中获取，意义不大
		InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
		metadata.checkConfigMembers(beanDefinition);
	}

	@Override
	public void resetBeanDefinition(String beanName) {
		this.lookupMethodsChecked.remove(beanName);
		this.injectionMetadataCache.remove(beanName);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName)
			throws BeanCreationException {

		// Let's check for lookup methods here..
		// 检测@Lookup注解，这个注解的注入方式，已经不推荐使用了===========
		if (!this.lookupMethodsChecked.contains(beanName)) {
			try {
				ReflectionUtils.doWithMethods(beanClass, method -> {
					Lookup lookup = method.getAnnotation(Lookup.class);
					if (lookup != null) {
						Assert.state(beanFactory != null, "No BeanFactory available");
						LookupOverride override = new LookupOverride(method, lookup.value());
						try {
							RootBeanDefinition mbd = (RootBeanDefinition) beanFactory.getMergedBeanDefinition(beanName);
							mbd.getMethodOverrides().addOverride(override);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(beanName, "Cannot apply @Lookup to beans without corresponding bean definition");
						}
					}
				});
			}
			catch (IllegalStateException ex) {
				throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
			}
			this.lookupMethodsChecked.add(beanName);
		}

		// Quick check on the concurrent map first, with minimal locking.
		// 先从缓存里去看，有没有解析过此类的构造函数~~~
		// 对每个类的构造函数只解析一次，解析完会存储结果，以备下次复用
		Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
		if (candidateConstructors == null) {
			// Fully synchronized resolution now...
			synchronized (this.candidateConstructorsCache) {

				// 为了线程安全，这里继续校验一次
				candidateConstructors = this.candidateConstructorsCache.get(beanClass);
				if (candidateConstructors == null) {
					Constructor<?>[] rawCandidates;
					try {
						// 拿到此Class所有的构造函数们（一般的类都只有一个空的构造函数）
						// 当然我们也可以写多个
						rawCandidates = beanClass.getDeclaredConstructors();
					} catch (Throwable ex) {
						throw new BeanCreationException(beanName, "Resolution of declared constructors on bean Class [" + beanClass.getName() + "] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
					}
					// 所有的
					List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
					// @Autowrited且required=true的
					Constructor<?> requiredConstructor = null;
					//  默认构造函数，即空构造
					Constructor<?> defaultConstructor = null;
					// 兼容Kotlin类型做的处理~~~~~~~~~~~~
					Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass); //todo
					int nonSyntheticConstructors = 0;
					// 遍历处理每个每个构造器~~~~~~~~~~~~~~~~~~
					for (Constructor<?> candidate : rawCandidates) {
						if (!candidate.isSynthetic()) {
							// 非合成的，nonSyntheticConstructors++
							nonSyntheticConstructors++;
						}else if (primaryConstructor != null) {
							// 有kotlin的构造器
							continue;
						}

						// 找到当前构造器candidate里有@Aotowaired或者@Value注解的形参
						MergedAnnotation<?> ann = findAutowiredAnnotation(candidate); // todo

						// 1、虽然本身构造器没有注解，但是不妨碍其父构可能存在@Autowrited主耳机

						if (ann == null) {
							// 此方法的目的是拿到目标类：比如若是被cglib代理过的，那就拿到父类（因为cglib是通过子类的形式加强的）
							Class<?> userClass = ClassUtils.getUserClass(beanClass);
							// 说明确实是被CGLIB代理过的，那就再解析一次
							// 看看父类是否有@Autowaired这种构造器
							if (userClass != beanClass) {
								try {
									Constructor<?> superCtor = userClass.getDeclaredConstructor(candidate.getParameterTypes());
									ann = findAutowiredAnnotation(superCtor);
								} catch (NoSuchMethodException ex) {
									// Simply proceed, no equivalent superclass constructor found...
								}
							}
						}

						// 2、本身和父类构造器中检查后，有带@Autowrited注解标注的构造器

						if (ann != null) {

							// 2.1 required必须是唯一，这个判断很有必要，表示要求的构造器最多只能有一个
							// 画外音：@Autowired标注的构造器数量最多只能有一个（当然，@Autowired的属性required=true的只能有一个，=false的可以有多个）
							// 含义：requiredConstructor != null ，说明在遍历rawCandidates就已经在前面发现有 @Autowrite且required=true的 构造器
							// 现在当前的candidate，也是@Autowrited，且required是true，就报错吧
							if (requiredConstructor != null) {
								throw new BeanCreationException(beanName, "Invalid autowire-marked constructor: " + candidate + ". Found constructor with 'required' Autowired annotation already: " + requiredConstructor);
							}

							// 获取autowire注解中required属性值
							boolean required = determineRequiredStatus(ann);
							// 2.2 只有是true，就往下走（默认值为true） --
							if (required) {
								if (!candidates.isEmpty()) {
									throw new BeanCreationException(beanName, "Invalid autowire-marked constructors: " + candidates + ". Found constructor with 'required' Autowired annotation: " + candidate);
								}
								// 这样子，这个构造器就是必须的了，记录下来
								requiredConstructor = candidate;
							}
							// 2.3 把标注有@Autowired注解的构造器，记录下来，作为候选的构造器
							candidates.add(candidate);
						}

						// 3、空构造函数

						// 这个就重要了，处理精妙
						// 若该构造器没有被标注@Autowired注解，但是它是无参构造器，那就当然候选的构造器（当然是以标注了@Autowired的为准）
						else if (candidate.getParameterCount() == 0) {
							// 这里注意：虽然把默认的构造函数记录下来了，但是并没有加进candidates里
							defaultConstructor = candidate;
						}
					}

					// 4、所有的构造器遍历结束
					// requiredConstructor\defaultConstructor\candidates
					// 下面是一串的if-else if 需要注意

					// 若能找到候选的构造器,这里注意，如果仅仅只有一个构造器的情况（没有标注@Autowired注解），这个亏胡原始股fakse，下面的elseif会处理的。。。。
					// 5、candidates 非空表示一定有@Autowrite标注的构造函数，因为不管required为true还是false都会加入到candidates
					if (!candidates.isEmpty()) {
						// Add default constructor to list of optional constructors, as fallback.
						// 6、虽然 candidates里面有值了，并且还没有requiredConstructor （相当于有一个或多个构造器标注了注解@Autowired，但是required都是false）的情况下，会吧默认的构造函数即空构造函数加进candidates
						if (requiredConstructor == null) {
							if (defaultConstructor != null) {
								candidates.add(defaultConstructor);
							}
							//如果没有默认的无参构造函数，且有@Autowired（required = false）的构造函数，则发出警告信
							else if (candidates.size() == 1 && logger.isWarnEnabled()) {
								logger.warn("Inconsistent constructor declaration on bean with name '" + beanName +
										"': single autowire-marked constructor flagged as optional - " +
										"this constructor is effectively required since there is no " +
										"default constructor to fall back to: " + candidates.get(0));
							}
						}
						candidateConstructors = candidates.toArray(new Constructor<?>[0]);
					}

					// 6、这个意思是：执行到这，说明没有@Autowrite标注的构造器，有且仅有一个构造器，并且该构造器的参数大于0个，那就是我们要找的构造器了
					// 这种情况，也是平时我们使用得比较多的情况
					else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
						candidateConstructors = new Constructor<?>[] {rawCandidates[0]};
					}

					// 7、执行到这：说明没有@Autowrite标注的构造器，且类里面有多个构造器，尝试去兼容kotlin吧
					// 处理primaryConstructor以及nonSyntheticConstructors    兼容Kotlin一般都达不到
					else if (nonSyntheticConstructors == 2 && primaryConstructor != null && defaultConstructor != null && !primaryConstructor.equals(defaultConstructor)) {
						// 将kotlin的主构造器和默认构造器作为候选者
						candidateConstructors = new Constructor<?>[] {primaryConstructor, defaultConstructor};
					}
					// 8、
					else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
						// 只有一个
						candidateConstructors = new Constructor<?>[] {primaryConstructor};
					}
					// 9、执行到这：说明啥构造器都没找到，那就是空数组
					else {
						candidateConstructors = new Constructor<?>[0];
					}
					this.candidateConstructorsCache.put(beanClass, candidateConstructors);
				}
			}
		}
		// 若有多个构造函数，但是没有一个标记了@Autowired,此处不会报错，但是返回null，交给后面的策略处理
		return (candidateConstructors.length > 0 ? candidateConstructors : null);
	}


	// 这个方法是InstantiationAwareBeanPostProcessor的，它在给属性赋值的时候会被调用~~
	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		// InjectionMetadata 里包含private final Collection<InjectedElement> injectedElements;表示所有需要注入处理的属性们~~~
		// 所以最终都是InjectionMetadata去处理~
		InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
		try {
			//（生路异常处理部分代码）所以事情都委托给了InjectionMetadata 的inject方法
			// 此处注意InjectionMetadata是会包含多个那啥的~~~(当然可能啥都没有  没有依赖注入的东东)
			//InjectionMetadata.inject内部查看也十分简单：最终都还是委托给了InjectedElement.inject实例去处理的
			metadata.inject(bean, beanName, pvs);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
		}
		return pvs;
	}

	@Deprecated
	@Override
	public PropertyValues postProcessPropertyValues(
			PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) {

		return postProcessProperties(pvs, bean, beanName);
	}

	/**
	 * 'Native' processing method for direct calls with an arbitrary target instance,
	 * resolving all of its fields and methods which are annotated with one of the
	 * configured 'autowired' annotation types.
	 * @param bean the target instance to process
	 * @throws BeanCreationException if autowiring failed
	 * @see #setAutowiredAnnotationTypes(Set)
	 */
	public void processInjection(Object bean) throws BeanCreationException {
		Class<?> clazz = bean.getClass();
		InjectionMetadata metadata = findAutowiringMetadata(clazz.getName(), clazz, null);
		try {
			// 从缓存获取到metada，或者构建了metada之后
			// 调用inject进行注入属性吧
			metadata.inject(bean, null, null);
		}
		catch (BeanCreationException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					"Injection of autowired dependencies failed for class [" + clazz + "]", ex);
		}
	}


	private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
		// Fall back to class name as cache key, for backwards compatibility with custom callers.
		String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
		// Quick check on the concurrent map first, with minimal locking.
		// 1. injectionMetadataCache 缓存中是否包含需要注入的metadata
		InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
		// 是否需要重新刷新：
		// 进入InjectionMetadata：可以发现只有
		// 1、当metadata为null，也就是说缓存未命中
		// 2、或者 clazz和metadata中的class不一样，也就是说缓存数据无效
		// 以上两种情况都需要重新计算InjectionMetadata
		if (InjectionMetadata.needsRefresh(metadata, clazz)) {
			synchronized (this.injectionMetadataCache) {
				// 双重检查
				metadata = this.injectionMetadataCache.get(cacheKey);
				if (InjectionMetadata.needsRefresh(metadata, clazz)) {
					// 进入下面if代码块：
					// 3. 表明属于上面说的缓存数据无效，需要将失效的metadata清空
					if (metadata != null) {
						metadata.clear(pvs);
					}
					// 核心：重新构建Autowrite的元数据
					metadata = buildAutowiringMetadata(clazz);
					// 缓存起来
					this.injectionMetadataCache.put(cacheKey, metadata);
				}
			}
		}
		return metadata;
	}

	// 这里我认为是整个依赖注入前期工作的精髓所在，简单粗暴的可以理解为：它把以依赖注入都转换为InjectionMetadata元信息，待后续使用
	// 这里会处理字段注入、方法注入~~~
	// 注意：Autowired使用在static字段/方法上、0个入参的方法上（不会报错 只是无效）
	// 显然方法的访问级别、是否final都是可以正常被注入进来的~~~
	private InjectionMetadata buildAutowiringMetadata(Class<?> clazz) {
		// 1. clazz类中是否存在@Autowrited和@Value的注解，没有就返回空，有的话就再去查
		if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
			return InjectionMetadata.EMPTY; // 不存在，返回空
		}

		// 2. 被注入的元素集合 InjectedElement - 封装有 是否为字段/成员值/是否可以跳过检查/属性描述符
		List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
		Class<?> targetClass = clazz;

		// 小细节：这里是个do while循环，所以即使在父类，父父类上依赖注入依旧是好使的（直到Object后停止）
		do {
			// 当前元素
			final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();

			// 3. 对本类所有的field遍历
			ReflectionUtils.doWithLocalFields(targetClass, field -> {
				// 找到满足有@Value或@Autowrited注解的字段 -- @Autowrited 和 @Value 注解不会同时使用哦
				MergedAnnotation<?> ann = findAutowiredAnnotation(field);
				if (ann != null) {
					// 注意：@Autowrited 不应该修饰静态变量
					if (Modifier.isStatic(field.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static fields: " + field);
						}
						return;
					}
					// 从注解中解析required属性值
					boolean required = determineRequiredStatus(ann);
					// 转为AutowiredFieldElement，添加到currElements中
					currElements.add(new AutowiredFieldElement(field, required));
				}
			});

			// 对本类的所有的methods遍历，类似的操作
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
				if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
					return;
				}
				MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
				if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
					if (Modifier.isStatic(method.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static methods: " + method);
						}
						return;
					}
					// @Autowrited注入的方法是形参数量不能为0
					if (method.getParameterCount() == 0) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation should only be used on methods with parameters: " + method);
						}
					}
					boolean required = determineRequiredStatus(ann);
					// 找出形参
					PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
					// 转为AutowiredMethodElement，然后存入currElements
					currElements.add(new AutowiredMethodElement(method, required, pd));
				}
			});

			// 最终添加到elements中
			// 小细节：父类的都放在第一位，所以父类是最先完成依赖注入的
			elements.addAll(0, currElements);
			// 一直递归处理，包括其superClass
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);

		// 转为 InjectionMetadata 的数据
		// 可见InjectionMetadata就是对clazz和elements的一个包装而已
		return InjectionMetadata.forElements(elements, clazz);
	}

	@Nullable
	private MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
		// 通过工具MergedAnnotations
		MergedAnnotations annotations = MergedAnnotations.from(ao);
		for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
			// 查找ao上最近的type类型的注解
			MergedAnnotation<?> annotation = annotations.get(type);
			// 如果存在，就直接返回 -- 因为annotation可能返回EMPTY，即表示没有这个注解
			if (annotation.isPresent()) {
				return annotation;
			}
		}
		return null;
	}

	/**
	 * Determine if the annotated field or method requires its dependency.
	 * <p>A 'required' dependency means that autowiring should fail when no beans
	 * are found. Otherwise, the autowiring process will simply bypass the field
	 * or method when no beans are found.
	 * @param ann the Autowired annotation
	 * @return whether the annotation indicates that a dependency is required
	 */
	@SuppressWarnings({"deprecation", "cast"})
	protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
		// The following (AnnotationAttributes) cast is required on JDK 9+.
		// 
		return determineRequiredStatus((AnnotationAttributes) ann.asMap(mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType())));
	}

	/**
	 * Determine if the annotated field or method requires its dependency.
	 * <p>A 'required' dependency means that autowiring should fail when no beans
	 * are found. Otherwise, the autowiring process will simply bypass the field
	 * or method when no beans are found.
	 * @param ann the Autowired annotation
	 * @return whether the annotation indicates that a dependency is required
	 * @deprecated since 5.2, in favor of {@link #determineRequiredStatus(MergedAnnotation)}
	 */
	@Deprecated
	protected boolean determineRequiredStatus(AnnotationAttributes ann) {
		// @Value 的 注解ann不包含"required"属性，
		// @AutoWrited 注解 的 "required"属性值为true，就可以返回true
		return (!ann.containsKey(this.requiredParameterName) || this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
	}

	/**
	 * Obtain all beans of the given type as autowire candidates.
	 * @param type the type of the bean
	 * @return the target beans, or an empty Collection if no bean of this type is found
	 * @throws BeansException if bean retrieval failed
	 */
	protected <T> Map<String, T> findAutowireCandidates(Class<T> type) throws BeansException {
		if (this.beanFactory == null) {
			throw new IllegalStateException("No BeanFactory configured - " +
					"override the getBeanOfType method or specify the 'beanFactory' property");
		}
		return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
	}

	/**
	 * Register the specified bean as dependent on the autowired beans.
	 */
	private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
		if (beanName != null) {
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
					this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Autowiring by type from bean name '" + beanName +
							"' to bean named '" + autowiredBeanName + "'");
				}
			}
		}
	}

	/**
	 * Resolve the specified cached method argument or field value.
	 */
	@Nullable
	private Object resolvedCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
		if (cachedArgument instanceof DependencyDescriptor) {
			DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
			Assert.state(this.beanFactory != null, "No BeanFactory available");
			// 缓存在BeanFactory中
			return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
		} else {
			return cachedArgument;
		}
	}


	/**
	 * Class representing injection information about an annotated field.
	 */
	private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {
		// 继承 InjectedElement
		// 这个类继承自静态抽象内部类InjectionMetadata.InjectedElement，并且它还是AutowiredAnnotationBeanPostProcessor的private内部类，体现出非常高的内聚性

		private final boolean required; // 是否必须

		private volatile boolean cached; // 是否缓存

		@Nullable
		private volatile Object cachedFieldValue; // 缓存字段值

		public AutowiredFieldElement(Field field, boolean required) {
			super(field, null);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			// 核心 -- 重写inject方法

			// 1. 强转为Field
			Field field = (Field) this.member;
			Object value;
			// 2. 是否已经缓存了
			if (this.cached) {
				try {
					// 2.1 已经缓存的话，从cachedFieldValue
					value = resolvedCachedArgument(beanName, this.cachedFieldValue);
				}
				catch (NoSuchBeanDefinitionException ex) {
					// Unexpected removal of target bean for cached argument -> re-resolve
					// 意外删除缓存参数的目标bean->只能重新重新解析
					value = resolveFieldValue(field, bean, beanName);
				}
			} else {
				// 2.2 核心 -- 如何获取这个Field的值
				value = resolveFieldValue(field, bean, beanName);
			}
			// 3. 拿到需要注入的值后，直接注入
			if (value != null) {
				ReflectionUtils.makeAccessible(field);
				field.set(bean, value);
			}
		}

		@Nullable
		private Object resolveFieldValue(Field field, Object bean, @Nullable String beanName) {
			// 1. 创建依赖描述符 -- desc
			// 每个Field都包装成一个DependencyDescriptor
			// 如果是Method包装成DependencyDescriptor,毕竟一个方法可以有多个入参
			// 此处包装成它后，显然和元数据都无关了，只和Field有关了  完全隔离
			DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
			desc.setContainingClass(bean.getClass());
			Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
			Assert.state(beanFactory != null, "No BeanFactory available");
			// 此处一般为SimpleTypeConverter，它registerDefaultEditors=true，所以普通类型大都能能通过属性编辑器实现转换的
			TypeConverter typeConverter = beanFactory.getTypeConverter();
			Object value;
			try {
				// 最最最根本的原理，其实在resolveDependency这个方法里，它最终返回的就是一个具体的值，这个value是个Object~
				value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
			}
			// 缓存处理
			synchronized (this) {
				if (!this.cached) {
					Object cachedFieldValue = null;
					// value不为null，或者必须要存在required=true
					// 可以看到value！=null并且required=true才会进行缓存的处理
					if (value != null || this.required) {
						// 可以看到缓存的值是上面的DependencyDescriptor对象~~~~
						cachedFieldValue = desc;
						// 注册依赖关系
						registerDependentBeans(beanName, autowiredBeanNames);
						// autowiredBeanNames里可能会有别名的名称~~~所以size可能大于1
						if (autowiredBeanNames.size() == 1) {
							String autowiredBeanName = autowiredBeanNames.iterator().next();
							// 检查 BeanFactory 中是否包含这个自动注入的 autowiredBeanName,且为字段对应的类型哦
							// beanFactory.isTypeMatch挺重要的~~~~因为@Autowired是按照类型注入
							if (beanFactory.containsBean(autowiredBeanName) && beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
								cachedFieldValue = new ShortcutDependencyDescriptor(desc, autowiredBeanName, field.getType());
							}
						}
					}
					// 缓存起来，cached为true，cachedFieldValue缓存值
					this.cachedFieldValue = cachedFieldValue;
					this.cached = true;
				}
			}
			return value;
		}
	}


	/**
	 * Class representing injection information about an annotated method.
	 */
	private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {
		// 继承 InjectedElement

		private final boolean required; // 是否必须

		private volatile boolean cached; // 是否缓存

		@Nullable
		private volatile Object[] cachedMethodArguments; // 方法形参

		public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
			super(method, pd);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			// 重写inject方法
			// 检查是否需要跳过方法
			if (checkPropertySkipping(pvs)) {
				return;
			}
			// 拿到需要指定注入的方法
			Method method = (Method) this.member;
			Object[] arguments;
			// 是否缓存
			if (this.cached) {
				try {
					arguments = resolveCachedArguments(beanName);
				}
				catch (NoSuchBeanDefinitionException ex) {
					// Unexpected removal of target bean for cached argument -> re-resolve
					arguments = resolveMethodArguments(method, bean, beanName);
				}
			}
			else {
				arguments = resolveMethodArguments(method, bean, beanName);
			}
			if (arguments != null) {
				try {
					// 通过反射调用方法，注入参数
					ReflectionUtils.makeAccessible(method);
					method.invoke(bean, arguments);
				}
				catch (InvocationTargetException ex) {
					throw ex.getTargetException();
				}
			}
		}

		@Nullable
		private Object[] resolveCachedArguments(@Nullable String beanName) {
			Object[] cachedMethodArguments = this.cachedMethodArguments;
			if (cachedMethodArguments == null) {
				return null;
			}
			Object[] arguments = new Object[cachedMethodArguments.length];
			for (int i = 0; i < arguments.length; i++) {
				arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i]);
			}
			return arguments;
		}

		@Nullable
		private Object[] resolveMethodArguments(Method method, Object bean, @Nullable String beanName) {
			int argumentCount = method.getParameterCount();
			Object[] arguments = new Object[argumentCount];
			DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
			Set<String> autowiredBeans = new LinkedHashSet<>(argumentCount);
			Assert.state(beanFactory != null, "No BeanFactory available");
			TypeConverter typeConverter = beanFactory.getTypeConverter();
			for (int i = 0; i < arguments.length; i++) {
				MethodParameter methodParam = new MethodParameter(method, i);
				DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
				currDesc.setContainingClass(bean.getClass());
				descriptors[i] = currDesc;
				try {
					Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
					if (arg == null && !this.required) {
						arguments = null;
						break;
					}
					arguments[i] = arg;
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
				}
			}
			synchronized (this) {
				if (!this.cached) {
					if (arguments != null) {
						DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, arguments.length);
						registerDependentBeans(beanName, autowiredBeans);
						if (autowiredBeans.size() == argumentCount) {
							Iterator<String> it = autowiredBeans.iterator();
							Class<?>[] paramTypes = method.getParameterTypes();
							for (int i = 0; i < paramTypes.length; i++) {
								String autowiredBeanName = it.next();
								if (beanFactory.containsBean(autowiredBeanName) &&
										beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
									cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
											descriptors[i], autowiredBeanName, paramTypes[i]);
								}
							}
						}
						this.cachedMethodArguments = cachedMethodArguments;
					}
					else {
						this.cachedMethodArguments = null;
					}
					this.cached = true;
				}
			}
			return arguments;
		}
	}


	/**
	 * DependencyDescriptor variant with a pre-resolved target bean name.
	 */
	@SuppressWarnings("serial")
	private static class ShortcutDependencyDescriptor extends DependencyDescriptor {

		private final String shortcut;

		private final Class<?> requiredType;

		public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut, Class<?> requiredType) {
			super(original);
			this.shortcut = shortcut;
			this.requiredType = requiredType;
		}

		@Override
		public Object resolveShortcut(BeanFactory beanFactory) {
			return beanFactory.getBean(this.shortcut, this.requiredType);
		}
	}

}
