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

package org.springframework.scheduling.annotation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Bean post-processor that registers methods annotated with @{@link Scheduled}
 * to be invoked by a {@link org.springframework.scheduling.TaskScheduler} according
 * to the "fixedRate", "fixedDelay", or "cron" expression provided via the annotation.
 *
 * <p>This post-processor is automatically registered by Spring's
 * {@code <task:annotation-driven>} XML element, and also by the
 * {@link EnableScheduling @EnableScheduling} annotation.
 *
 * <p>Autodetects any {@link SchedulingConfigurer} instances in the container,
 * allowing for customization of the scheduler to be used or for fine-grained
 * control over task registration (e.g. registration of {@link Trigger} tasks.
 * See the @{@link EnableScheduling} javadocs for complete usage details.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Elizabeth Chatman
 * @since 3.0
 * @see Scheduled
 * @see EnableScheduling
 * @see SchedulingConfigurer
 * @see org.springframework.scheduling.TaskScheduler
 * @see org.springframework.scheduling.config.ScheduledTaskRegistrar
 * @see AsyncAnnotationBeanPostProcessor
 */
public class ScheduledAnnotationBeanPostProcessor
		implements ScheduledTaskHolder, MergedBeanDefinitionPostProcessor, DestructionAwareBeanPostProcessor,
		Ordered, EmbeddedValueResolverAware, BeanNameAware, BeanFactoryAware, ApplicationContextAware,
		SmartInitializingSingleton, ApplicationListener<ContextRefreshedEvent>, DisposableBean {
	// ScheduledAnnotationBeanPostProcessor = Scheduled Annotation BeanPostProcessor

	// 首先：非常震撼的是，它实现的接口非常的多。还好的是，大部分接口我们都很熟悉了
	// MergedBeanDefinitionPostProcessor：它是个BeanPostProcessor
	// DestructionAwareBeanPostProcessor：在销毁此Bean的时候，会调用对应方法
	// SmartInitializingSingleton：它会在所有的单例Bean都完成了初始化后，调用这个接口的方法
	// EmbeddedValueResolverAware, BeanNameAware, BeanFactoryAware, ApplicationContextAware：都是些感知接口
	// DisposableBean：该Bean销毁的时候会调用
	// ApplicationListener<ContextRefreshedEvent>：监听容器的`ContextRefreshedEvent`事件
	// ScheduledTaskHolder：维护本地的ScheduledTask实例(暴露本地的scheduled tasks的通用接口)


	// 类似:AsyncExecutionAspectSupport中当@Async没有指定executor，并且用户没有配置AsyncConfigurer时，出现多个taskExecutor将寻找bean名字为"taskExecutor"
	// 先找唯一类型的TaskScheduler，有多个时，回退到名称为"taskScheduler"且类型为Executor的
	// 二者不同的是: 一个是"taskExecutor",一个是"taskScheduler"
	public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";


	protected final Log logger = LogFactory.getLog(getClass());

	// ScheduledTaskRegistrar：全局的ScheduledTask注册中心，
	// ScheduledTaskHolder接口的一个重要的实现类，维护了程序中所有配置的ScheduledTask
	// 内部会处理调取器如何工作，因此我建议先移步，看看这个类得具体分析
	private final ScheduledTaskRegistrar registrar;

	// 调度器（若我们没有配置，它是null的）
	@Nullable
	private Object scheduler;

	// 下面都是Aware感知接口注入进来的~~
	
	@Nullable
	private StringValueResolver embeddedValueResolver; // 解析${}资源占位符

	@Nullable
	private String beanName; // 当前bean的名字

	@Nullable
	private BeanFactory beanFactory; // bean工厂

	@Nullable
	private ApplicationContext applicationContext; // ApplicationContext上下文

	// nonAnnotatedClasses属性用来缓存
	// 没有标注@Scheduled注解的类，防止从父去扫描（毕竟可能有多个容器，可能有重复扫描的现象）
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	// 缓存对应的Bean上 ~~ 里面对应的 ScheduledTask任务。可议有多个哦~~
	// 注意：此处使用了IdentityHashMap
	private final Map<Object, Set<ScheduledTask>> scheduledTasks = new IdentityHashMap<>(16);


	/**
	 * Create a default {@code ScheduledAnnotationBeanPostProcessor}.
	 */
	public ScheduledAnnotationBeanPostProcessor() {
		this.registrar = new ScheduledTaskRegistrar();
	}

	/**
	 * Create a {@code ScheduledAnnotationBeanPostProcessor} delegating to the
	 * specified {@link ScheduledTaskRegistrar}.
	 * @param registrar the ScheduledTaskRegistrar to register @Scheduled tasks on
	 * @since 5.1
	 */
	public ScheduledAnnotationBeanPostProcessor(ScheduledTaskRegistrar registrar) {
		Assert.notNull(registrar, "ScheduledTaskRegistrar is required");
		this.registrar = registrar;
	}


	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE;
	}

	/**
	 * Set the {@link org.springframework.scheduling.TaskScheduler} that will invoke
	 * the scheduled methods, or a {@link java.util.concurrent.ScheduledExecutorService}
	 * to be wrapped as a TaskScheduler.
	 * <p>If not specified, default scheduler resolution will apply: searching for a
	 * unique {@link TaskScheduler} bean in the context, or for a {@link TaskScheduler}
	 * bean named "taskScheduler" otherwise; the same lookup will also be performed for
	 * a {@link ScheduledExecutorService} bean. If neither of the two is resolvable,
	 * a local single-threaded default scheduler will be created within the registrar.
	 * @see #DEFAULT_TASK_SCHEDULER_BEAN_NAME
	 */
	public void setScheduler(Object scheduler) {
		this.scheduler = scheduler;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	/**
	 * Making a {@link BeanFactory} available is optional; if not set,
	 * {@link SchedulingConfigurer} beans won't get autodetected and
	 * a {@link #setScheduler scheduler} has to be explicitly configured.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Setting an {@link ApplicationContext} is optional: If set, registered
	 * tasks will be activated in the {@link ContextRefreshedEvent} phase;
	 * if not set, it will happen at {@link #afterSingletonsInstantiated} time.
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		if (this.beanFactory == null) {
			this.beanFactory = applicationContext;
		}
	}


	@Override
	public void afterSingletonsInstantiated() {
		// 注意: 另一个核心方法: postProcessAfterInitialization()的执行实际会比afterSingletonsInstantiated()早的多
		// 因为: 在ApplicationContext中加载完所有的BeanDefinition后,会去初始化单例的Bean,这个期间BeanPostProcessor#afterSingletonsInstantiated()
		// 就会在Bean的初始化之后生效,而afterSingletonsInstantiated()需要等到所有的单例bean被注册完后生效
		
		// afterSingletonsInstantiated() -> 检查是否有使用@Schduled注解启动周期异步任务,有的话,就将其注册到ScheduledTaskRegistrar中
		// 				note: 不同于@Async需要构建代理对象,因为@Scheduled注解标注的方法,是自动的周期性的触发,不需要人为触发
		// afterSingletonsInstantiated() ->  在注册完所有的周期异步Task后,就需要开始处理ScheduledTaskRegistrar,将已经注册的Task都加入到task调度器中 -> 执行run起来哦
		
		// 执行时机: 所有单例bean都被实例化之后调用，那么说明已经将所有的定时任务给扫描结束
		
		// 1. 已经扫描完所有的@Scheduled标注的周期方法
		// nonAnnotatedClasses就可以清空
		this.nonAnnotatedClasses.clear();

		// 2. 在容器内运行，ApplicationContext都不会为null
		if (this.applicationContext == null) {
			// 3. 定时任务扫描完了，且已经注册到ScheduledTaskRegistrar
			// 但是任务Task还不一定被运行起来啦,因为注册到ScheduledTaskRegistrar中时可能没有TaskScheduler调度器
			// 调用 ScheduledTaskRegistrar.afterPropertiesSet()方法来检查有没有注册但没有加入到调度器中运行的Task,有的话,使用默认的调度器开始调度哦
			finishRegistration();
		}
	}

	// 兼容容器刷新的时间（此时候容器硬启动完成了）   它还在`afterSingletonsInstantiated`的后面执行
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		// 这个动作务必要做：因为Spring可能有多个容器，所以可能会发出多个ContextRefreshedEvent 事件
		// 显然我们只处理自己容器发出来得事件，别的容器发出来我不管~~
		if (event.getApplicationContext() == this.applicationContext) {
			// Running in an ApplicationContext -> register tasks this late...
			// giving other ContextRefreshedEvent listeners a chance to perform
			// their work at the same time (e.g. Spring Batch's job registration).
			// 为其他ContextRefreshedEvent侦听器提供同时执行其工作的机会（例如，Spring批量工作注册）
			// ❗️❗️❗️
			finishRegistration();
		}
	}

	private void finishRegistration() {
		// 1. 如果setScheduler了，就以调用者指定的为准~~~
		// 一般没有
		if (this.scheduler != null) {
			this.registrar.setScheduler(this.scheduler);
		}

		// 2. 这里继续厉害了：从容器中找到所有的接口`SchedulingConfigurer`的实现类（我们可议通过实现它定制化scheduler）
		if (this.beanFactory instanceof ListableBeanFactory) {
			Map<String, SchedulingConfigurer> beans = ((ListableBeanFactory) this.beanFactory).getBeansOfType(SchedulingConfigurer.class);
			List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
			AnnotationAwareOrderComparator.sort(configurers);
			// 2.1 不同于@Async只允许设置一个AsyncConfigurer
			// @Scheduled但是平时使用，我们一般一个类足矣~~
			for (SchedulingConfigurer configurer : configurers) {
				// 2.1.1 遍历所有的SchedulingConfigurer，将registrar传递过去允许用户进行定制化操作
				configurer.configureTasks(this.registrar);
			}
		}

		// 3. 至于task是怎么注册进registrar的，请带回看`postProcessAfterInitialization`这个方法的实现
		// 有task但是registrar.getScheduler()的调度器为空，那就尝试去容器里找一个调度器使用试试~~~ 【说明前面用户没有setScheduler，就需要从ioc容器中获取】
		if (this.registrar.hasTasks() && this.registrar.getScheduler() == null) {
			Assert.state(this.beanFactory != null, "BeanFactory must be set to find scheduler by type");
			try {
				// 3.1 查找TaskScheduler类型的调度器
				this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, false));
			}
			catch (NoUniqueBeanDefinitionException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Could not find unique TaskScheduler bean - attempting to resolve by name: " +
							ex.getMessage());
				}
				try {
					// 3.2 有多个BTaskScheduler类型的bean时，就需要通过name进行一下唯一性确定
					this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, true));
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (logger.isInfoEnabled()) {
						logger.info("More than one TaskScheduler bean exists within the context, and " +
								"none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
								"(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
								"ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
								ex.getBeanNamesFound());
					}
				}
			}
			catch (NoSuchBeanDefinitionException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Could not find default TaskScheduler bean - attempting to find ScheduledExecutorService: " +
							ex.getMessage());
				}
				// Search for ScheduledExecutorService bean next...
				try {
					// 没有找到任何一个TaskExecutor的Bean
					// 3.3 那么就降级去查找ScheduledExecutorService类型的调度器
					this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, false));
				}
				catch (NoUniqueBeanDefinitionException ex2) {
					if (logger.isTraceEnabled()) {
						logger.trace("Could not find unique ScheduledExecutorService bean - attempting to resolve by name: " +
								ex2.getMessage());
					}
					try {
						// 查找ScheduledExecutorService类型的调度器不是唯一的
						// 3.3再降级，去查找ScheduledExecutorService类并且名字唯一的调度器
						this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, true));
					}
					catch (NoSuchBeanDefinitionException ex3) {
						if (logger.isInfoEnabled()) {
							logger.info("More than one ScheduledExecutorService bean exists within the context, and " +
									"none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
									"(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
									"ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
									ex2.getBeanNamesFound());
						}
					}
				}
				catch (NoSuchBeanDefinitionException ex2) {
					if (logger.isTraceEnabled()) {
						logger.trace("Could not find default ScheduledExecutorService bean - falling back to default: " +
								ex2.getMessage());
					}
					// Giving up -> falling back to default scheduler within the registrar...
					logger.info("No TaskScheduler/ScheduledExecutorService bean found for scheduled processing");
				}
			}
		}

		// ❗️❗️❗️
		// 调用registrar的afterPropertiesSet()方法
		// 当经过上述步骤,还没有TaskScheduler被注册,下面就会自动创建一个默认的使用哦
		this.registrar.afterPropertiesSet();
	}

	// 从容器中去找一个Scheduler调度器
	private <T> T resolveSchedulerBean(BeanFactory beanFactory, Class<T> schedulerType, boolean byName) {
		// 1. 若按名字和类型去查找
		if (byName) {
			// 1.1 根据类型schedulerType和beanName="taskScheduler"去查找
			T scheduler = beanFactory.getBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, schedulerType);
			// 1.2 注册依赖关系：beanName 依赖调度器 DEFAULT_TASK_SCHEDULER_BEAN_NAME
			if (this.beanName != null && this.beanFactory instanceof ConfigurableBeanFactory) {
				((ConfigurableBeanFactory) this.beanFactory).registerDependentBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, this.beanName);
			}
			return scheduler;
		}
		// 2. 仅仅按照schedulerType类型匹配
		else if (beanFactory instanceof AutowireCapableBeanFactory) {
			NamedBeanHolder<T> holder = ((AutowireCapableBeanFactory) beanFactory).resolveNamedBean(schedulerType);
			// 2.1 注册依赖关系：
			if (this.beanName != null && beanFactory instanceof ConfigurableBeanFactory) {
				((ConfigurableBeanFactory) beanFactory).registerDependentBean(holder.getBeanName(), this.beanName);
			}
			return holder.getBeanInstance();
		}
		// 3. 按照类型找
		else {
			return beanFactory.getBean(schedulerType);
		}
	}


	// 忽略 ~~
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
	}

	// 忽略 ~~
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	// ❗️❗️❗️ -> 
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		// 初始化之后回调

		// 1. 忽略aop框架中的bean、TaskScheduler的bean、ScheduledExecutorService的bean
		// 这种bean是不能注册周期任务的
		if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler || bean instanceof ScheduledExecutorService) {
			return bean;
		}

		// 2. 拿到目标类型（因为此类有可能已经被代理过）
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
		
		// 2.1 
		// nonAnnotatedClasses属性对没有标注@Scheduled注解的类做了一个缓存，防止从父去扫描（毕竟可能有多个容器，可能有重复扫描的现象）
		// isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class)) 检查targetClass中类上、方法上、字段上是否有@Scheduled注解
		if (!this.nonAnnotatedClasses.contains(targetClass) && AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class))) {
			// 2.2 已经确定这个beanClass上有对应的@Scheduled注解和@Schedules注解
			// 接下来需要确定哪些method上有这个个注解哦
			Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					(MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
						// 2.2.1 找到方法上有@Scheduled的
						Set<Scheduled> scheduledMethods = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class);
						return (!scheduledMethods.isEmpty() ? scheduledMethods : null);
					});
			// 2.3 beanClass中所有的方法都没有使用@Scheduled注解 -> 即没有周期性异步任务
			if (annotatedMethods.isEmpty()) {
				// 2.3.1 没有指定注解的方法，将其加入缓存中nonAnnotatedClasses -> 标记为没有@Scheduled注解
				this.nonAnnotatedClasses.add(targetClass);
				if (logger.isTraceEnabled()) {
					logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
				}
			}
			// 2.4 beanClass中有部分方法使用@Scheduled注解 -> 即有周期性异步任务
			else {
				// 这里有一个双重遍历。因为一个方法上，可能重复标注多个@Scheduled注解 ~~~~~
				// 所以最终遍历出来后，就交给processScheduled(scheduled, method, bean)去处理了 -- 核心，注意每个Method上可以有多个@Scheduled注解因此需要进行遍历哈
				annotatedMethods.forEach((method, scheduledMethods) -> scheduledMethods.forEach(scheduled -> processScheduled(scheduled, method, bean)));
				if (logger.isTraceEnabled()) {
					logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName +
							"': " + annotatedMethods);
				}
			}
		}
		// 3. 不会对bean做代理
		return bean;
	}

	/**
	 * Process the given {@code @Scheduled} method declaration on the given bean.
	 * @param scheduled the @Scheduled annotation
	 * @param method the method that the annotation has been declared on
	 * @param bean the target bean instance
	 * @see #createRunnable(Object, Method)
	 */
	protected void processScheduled(Scheduled scheduled, Method method, Object bean) {
		// ❗️❗️❗️ 该方法 -- 用来处理指定bean上的指定method的@Scheduled
		// 如何处理?
		
		try {
			// 1. 创建可执行runnable方法
			Runnable runnable = createRunnable(bean, method);
			boolean processedSchedule = false;
			String errorMessage = "Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";

			// 2. 装载任务，这里长度定为4
			// @Scheduled注解有4种指定定时任务的方式: 包括 cron\fixedRate\fixedDelay
			Set<ScheduledTask> tasks = new LinkedHashSet<>(4);
			
			// 3. 计算出延时多长时间执行 initialDelayString 支持占位符如：@Scheduled(fixedDelayString = "${time.fixedDelay}")
			
			// 3.1 拿到initialDelay的long值
			long initialDelay = scheduled.initialDelay();
			// 3.2 拿到initialDelayString的string值
			String initialDelayString = scheduled.initialDelayString();
			if (StringUtils.hasText(initialDelayString)) {
				Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
				// 3.3 先解析initialDelayString的占位符
				if (this.embeddedValueResolver != null) {
					initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
				}
				// 3.4 解析完后initialDelayString不为空,调用parseDelayAsLong(initialDelayString)解析为long值,且替换initialDelay的属性值
				// ❗️❗️❗️ initialDelayString 优先级比 initialDelay 高，因为这里一旦initialDelayString解析成功就会导致initialDelay被占用
				if (StringUtils.hasLength(initialDelayString)) {
					try {
						initialDelay = parseDelayAsLong(initialDelayString);
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
					}
				}
			}

			// 4. 用同样的手法解析cron
			String cron = scheduled.cron();
			if (StringUtils.hasText(cron)) {
				String zone = scheduled.zone();
				if (this.embeddedValueResolver != null) {
					cron = this.embeddedValueResolver.resolveStringValue(cron);
					zone = this.embeddedValueResolver.resolveStringValue(zone);
				}
				if (StringUtils.hasLength(cron)) {
					Assert.isTrue(initialDelay == -1, "'initialDelay' not supported for cron triggers");
					processedSchedule = true;
					if (!Scheduled.CRON_DISABLED.equals(cron)) {
						TimeZone timeZone;
						if (StringUtils.hasText(zone)) {
							timeZone = StringUtils.parseTimeZoneString(zone);
						}
						else {
							timeZone = TimeZone.getDefault();
						}
						// 4.1 如果配置了cron，就会生成一个CronTask了，就可以把任务注册进registrar里面了
						// 这里面的处理是 -- 如果已经有调度器taskScheduler了，那就可以立马准备执行了
						tasks.add(this.registrar.scheduleCronTask(new CronTask(runnable, new CronTrigger(cron, timeZone))));
					}
				}
			}

			// 在这一点上，我们不再需要区分初始延迟与否
			// 5. note: initialDelay/initialDelayString 仅仅对Cron属性对应的CronTask有影响哦
			if (initialDelay < 0) {
				initialDelay = 0;
			}
			
			// 下面注意:
			// 注意:
			// a:同时使用 fixedDelay 和 fixedDelayString 将会创建两个 FixedDelayTask
			// b:同时使用 fixedRate 和 fixedRateString 将会创建两个 FixedDelayTask

			// 6. 检查 fixedDelay
			long fixedDelay = scheduled.fixedDelay();
			if (fixedDelay >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
				// 6.1 fixedDelay 属性将创建 FixedDelayTask
				tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
			}
			String fixedDelayString = scheduled.fixedDelayString();
			if (StringUtils.hasText(fixedDelayString)) {
				if (this.embeddedValueResolver != null) {
					fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
				}
				if (StringUtils.hasLength(fixedDelayString)) {
					Assert.isTrue(!processedSchedule, errorMessage);
					processedSchedule = true;
					try {
						fixedDelay = parseDelayAsLong(fixedDelayString);
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedDelayString value \"" + fixedDelayString + "\" - cannot parse into long");
					}
					// 6.2  fixedDelayString 属性将创建 FixedDelayTask
					tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
				}
			}

			// 7. 检查 fixedRate 
			long fixedRate = scheduled.fixedRate();
			if (fixedRate >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
				tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
			}
			String fixedRateString = scheduled.fixedRateString();
			if (StringUtils.hasText(fixedRateString)) {
				if (this.embeddedValueResolver != null) {
					fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
				}
				if (StringUtils.hasLength(fixedRateString)) {
					Assert.isTrue(!processedSchedule, errorMessage);
					processedSchedule = true;
					try {
						fixedRate = parseDelayAsLong(fixedRateString);
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid fixedRateString value \"" + fixedRateString + "\" - cannot parse into long");
					}
					tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
				}
			}

			// 8. 检查我们是否设置了任何属性 忽略~~
			Assert.isTrue(processedSchedule, errorMessage);

			// 9. 最后注册当前类中的计划任务scheduledTasks
			synchronized (this.scheduledTasks) {
				// 以bean作为key，以bean中所有的定时方法作为value
				Set<ScheduledTask> regTasks = this.scheduledTasks.computeIfAbsent(bean, key -> new LinkedHashSet<>(4));
				regTasks.addAll(tasks);
			}
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Encountered invalid @Scheduled method '" + method.getName() + "': " + ex.getMessage());
		}
	}

	/**
	 * Create a {@link Runnable} for the given bean instance,
	 * calling the specified scheduled method.
	 * <p>The default implementation creates a {@link ScheduledMethodRunnable}.
	 * @param target the target bean instance
	 * @param method the scheduled method to call
	 * @since 5.1
	 * @see ScheduledMethodRunnable#ScheduledMethodRunnable(Object, Method)
	 */
	protected Runnable createRunnable(Object target, Method method) {
		// 为指定target的method创建Runnable方法
		
		// 1. 标注@Scheduled注解的方法必须是无参的方法
		Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
		Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
		// 2. ScheduledMethodRunnable的run()方法就是简单的利用反射执行方法
		return new ScheduledMethodRunnable(target, invocableMethod);
	}

	private static long parseDelayAsLong(String value) throws RuntimeException {
		// ❗️❗️❗️
		// 支持: 当value带有第一个字符是P或者p时 -> 会被Duration.parse(value).toMillis()解析哦
		// 将从诸如PnDTnHnMn.nS类的文本字符串中获取Duration [n表示数字]
		// 规则:
		// a:字符串以可选符号开头，由ASCII负号或正号表示。如果为负，则整个周期都被否定。
		// b:接下来是大写或小写的ASCII 字母“P”。
		// c:然后有四个部分，每个部分由一个数字和一个后缀组成。这些部分具有“D”、“H”、“M”和“S”的 ASCII 后缀，表示天、小时、分钟和秒，接受大写或小写。后缀必须按顺序出现。
		// d:ASCII 字母“T”必须出现在小时、分钟或秒部分的第一次出现（如果有）之前。
		// e:必须存在四个部分中的至少一个，
		// f:如果存在“T”，则必须在“T”之后至少有一个部分。
		// g:每个部分的数字部分必须由一个或多个 ASCII 数字组成。该数字可以以 ASCII 负号或正号为前缀。天数、小时数和分钟数必须解析为long 。
		// 秒数必须解析为带有可选分数的long整数。小数点可以是点或逗号。小数部分可能有 0 到 9 个数字
		// Examples:
		//          "PT20.345S" -- parses as "20.345 seconds"
		//          "PT15M"     -- parses as "15 minutes" (where a minute is 60 seconds)
		//          "PT10H"     -- parses as "10 hours" (where an hour is 3600 seconds)
		//          "P2D"       -- parses as "2 days" (where a day is 24 hours or 86400 seconds)
		//          "P2DT3H4M"  -- parses as "2 days, 3 hours and 4 minutes"
		//          "P-6H3M"    -- parses as "-6 hours and +3 minutes"
		//          "-P6H3M"    -- parses as "-6 hours and -3 minutes"
		//          "-P-6H+3M"  -- parses as "+6 hours and -3 minutes"
		//       
		if (value.length() > 1 && (isP(value.charAt(0)) || isP(value.charAt(1)))) {
			return Duration.parse(value).toMillis();
		}
		return Long.parseLong(value);
	}

	private static boolean isP(char ch) {
		return (ch == 'P' || ch == 'p');
	}


	/**
	 * Return all currently scheduled tasks, from {@link Scheduled} methods
	 * as well as from programmatic {@link SchedulingConfigurer} interaction.
	 * @since 5.0.2
	 */
	@Override
	public Set<ScheduledTask> getScheduledTasks() {
		Set<ScheduledTask> result = new LinkedHashSet<>();
		synchronized (this.scheduledTasks) {
			Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
			for (Set<ScheduledTask> tasks : allTasks) {
				result.addAll(tasks);
			}
		}
		result.addAll(this.registrar.getScheduledTasks());
		return result;
	}

	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) {
		// ❗️销毁之前，将注册到scheduledTasks全部清空，并且取消
		Set<ScheduledTask> tasks;
		synchronized (this.scheduledTasks) {
			tasks = this.scheduledTasks.remove(bean);
		}
		if (tasks != null) {
			for (ScheduledTask task : tasks) {
				task.cancel();
			}
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		synchronized (this.scheduledTasks) {
			return this.scheduledTasks.containsKey(bean);
		}
	}

	@Override
	public void destroy() {
		synchronized (this.scheduledTasks) {
			Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
			for (Set<ScheduledTask> tasks : allTasks) {
				for (ScheduledTask task : tasks) {
					task.cancel();
				}
			}
			this.scheduledTasks.clear();
		}
		this.registrar.destroy();
	}

}
