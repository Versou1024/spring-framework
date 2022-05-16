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

	// 首先：非常震撼的是，它实现的接口非常的多。还好的是，大部分接口我们都很熟悉了。
	// MergedBeanDefinitionPostProcessor：它是个BeanPostProcessor
	// DestructionAwareBeanPostProcessor：在销毁此Bean的时候，会调用对应方法
	// SmartInitializingSingleton：它会在所有的单例Bean都完成了初始化后，调用这个接口的方法
	// EmbeddedValueResolverAware, BeanNameAware, BeanFactoryAware, ApplicationContextAware：都是些感知接口
	// DisposableBean：该Bean销毁的时候会调用
	// ApplicationListener<ContextRefreshedEvent>：监听容器的`ContextRefreshedEvent`事件
	// ScheduledTaskHolder：维护本地的ScheduledTask实例

	/**
	 * The default name of the {@link TaskScheduler} bean to pick up: {@value}.
	 * <p>Note that the initial lookup happens by type; this is just the fallback
	 * in case of multiple scheduler beans found in the context.
	 * @since 4.2
	 */
	public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";
	// 看着注释就知道，和@Async的默认处理一样~~~~先找唯一类型的TaskScheduler，有多个时，回退到名称为"taskScheduler"。且类型为Executor的


	protected final Log logger = LogFactory.getLog(getClass());

	// ScheduledTaskRegistrar：ScheduledTask注册中心，ScheduledTaskHolder接口的一个重要的实现类，维护了程序中所有配置的ScheduledTask
	// 内部会处理调取器得工作，因此我建议先移步，看看这个类得具体分析
	private final ScheduledTaskRegistrar registrar;

	// 调度器（若我们没有配置，它是null的）
	@Nullable
	private Object scheduler;

	// 这些都是Awire感知接口注入进来的~~
	@Nullable
	private StringValueResolver embeddedValueResolver;

	@Nullable
	private String beanName;

	@Nullable
	private BeanFactory beanFactory;

	@Nullable
	private ApplicationContext applicationContext;

	// 缓存，没有被标注注解的class们
	// 这有个技巧，使用了newSetFromMap，自然而然的这个set也就成了一个线程安全的set
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

	// 缓存对应的Bean上  里面对应的 ScheduledTask任务。可议有多个哦~~
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
		// Remove resolved singleton classes from cache
		// 所有单例都被实例化之后调用，那么说明已经将所有的定时任务给扫描结束了
		// 因此将nonAnnotatedClasses可以清空了
		this.nonAnnotatedClasses.clear();

		// 在容器内运行，ApplicationContext都不会为null
		if (this.applicationContext == null) {
			// Not running in an ApplicationContext -> register tasks early...
			// 定时任务扫描完了，就需要将任务注册起来了
			// 如果不是在ApplicationContext下运行的，那么就应该提前注册这些任务
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
			finishRegistration();
		}
	}

	private void finishRegistration() {
		if (this.scheduler != null) {
			// 如果setScheduler了，就以调用者指定的为准~~~
			this.registrar.setScheduler(this.scheduler);
		}

		// 这里继续厉害了：从容器中找到所有的接口`SchedulingConfigurer`的实现类（我们可议通过实现它定制化scheduler）
		if (this.beanFactory instanceof ListableBeanFactory) {
			Map<String, SchedulingConfigurer> beans = ((ListableBeanFactory) this.beanFactory).getBeansOfType(SchedulingConfigurer.class);
			List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
			AnnotationAwareOrderComparator.sort(configurers);
			// 同@Async只允许设置一个不一样的是，这里每个都会让它生效
			// 但是平时使用，我们自顶一个类足矣~~
			for (SchedulingConfigurer configurer : configurers) {
				// 遍历所有的SchedulingConfigurer，将registrar传递过去允许用户进行定制化操作
				configurer.configureTasks(this.registrar);
			}
		}

		// 至于task是怎么注册进registor的，请带回看`postProcessAfterInitialization`这个方法的实现
		// 有任务并且registrar.getScheduler() == null，那就去容器里找来试试~~~ 【说明前面用户没有setScheduler，就需要从ioc容器中获取】
		if (this.registrar.hasTasks() && this.registrar.getScheduler() == null) {
			// 注册中心有定时任务tasks，并且由调用度scheduler，就可以进行提交定时任务了
			Assert.state(this.beanFactory != null, "BeanFactory must be set to find scheduler by type");
			try {
				// Search for TaskScheduler bean...
				// 查找TaskScheduler类型的调度器
				this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, false));
			}
			catch (NoUniqueBeanDefinitionException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Could not find unique TaskScheduler bean - attempting to resolve by name: " +
							ex.getMessage());
				}
				try {
					// 有多个Bean时，就需要通过name进行一下唯一性确定
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
					// 那么就降级去查找ScheduledExecutorService类型的调度器
					this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, false));
				}
				catch (NoUniqueBeanDefinitionException ex2) {
					if (logger.isTraceEnabled()) {
						logger.trace("Could not find unique ScheduledExecutorService bean - attempting to resolve by name: " +
								ex2.getMessage());
					}
					try {
						// 查找ScheduledExecutorService类型的调度器不是唯一的
						// 再降级，去查找ScheduledExecutorService类并且名字唯一的调度器
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

		// 调用registrar的afterPropertiesSet()方法
		this.registrar.afterPropertiesSet();
	}

	// 从容器中去找一个Scheduler调度器
	private <T> T resolveSchedulerBean(BeanFactory beanFactory, Class<T> schedulerType, boolean byName) {
		// 若按名字去查找，那就按照名字找
		if (byName) {
			T scheduler = beanFactory.getBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, schedulerType);
			// 这个处理非常非常有意思，就是说倘若找到了你可以在任意地方直接@Autowired这个Bean了，可以拿这个共用Scheduler来调度我们自己的任务啦~~
			if (this.beanName != null && this.beanFactory instanceof ConfigurableBeanFactory) {
				// 注册依赖关系：beanName 依赖调度器 DEFAULT_TASK_SCHEDULER_BEAN_NAME
				((ConfigurableBeanFactory) this.beanFactory).registerDependentBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, this.beanName);
			}
			return scheduler;
		}
		// 按照schedulerType该类型的名字匹配resolveNamedBean  底层依赖：getBeanNamesForType
		else if (beanFactory instanceof AutowireCapableBeanFactory) {
			NamedBeanHolder<T> holder = ((AutowireCapableBeanFactory) beanFactory).resolveNamedBean(schedulerType);
			if (this.beanName != null && beanFactory instanceof ConfigurableBeanFactory) {
				// 注册依赖关系：
				((ConfigurableBeanFactory) beanFactory).registerDependentBean(holder.getBeanName(), this.beanName);
			}
			return holder.getBeanInstance();
		}
		// 按照类型找
		else {
			return beanFactory.getBean(schedulerType);
		}
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		// 初始化之后回调

		// 忽略aop框架中的bean、taskSchduler的bean、ScheduledExecutorService的bean
		if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler || bean instanceof ScheduledExecutorService) {
			// Ignore AOP infrastructure such as scoped proxies.
			return bean;
		}

		// 拿到目标类型（因为此类有可能已经被代理过）
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
		// 这里对没有标注注解的类做了一个缓存，防止从父去扫描（毕竟可能有多个容器，可能有重复扫描的现象）
		// AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class)) 检查targetClass中类上、方法上、字段上是否有@Scheduled注解
		if (!this.nonAnnotatedClasses.contains(targetClass) && AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class))) {
			// 如下：主要用到了MethodIntrospector.selectMethods  这个内省方法工具类的这个g工具方法，去找指定Class里面，符合条件的方法们
			Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
					(MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
						// 找到方法上有@Scheduled的认可
						Set<Scheduled> scheduledMethods = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class);
						return (!scheduledMethods.isEmpty() ? scheduledMethods : null);
					});
			if (annotatedMethods.isEmpty()) {
				// 没有指定注解的方法，将其加入缓存中nonAnnotatedClasses
				this.nonAnnotatedClasses.add(targetClass);
				if (logger.isTraceEnabled()) {
					logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
				}
			}
			// 此处相当于已经找到了对应的注解方法~~~
			else {
				// Non-empty set of methods
				// 这里有一个双重遍历。因为一个方法上，可能重复标注多个这样的注解~~~~~
				// 所以最终遍历出来后，就交给processScheduled(scheduled, method, bean)去处理了 -- 核心，注意每个Method上可以有多个@Scheduled注解因此需要进行遍历哈
				annotatedMethods.forEach((method, scheduledMethods) -> scheduledMethods.forEach(scheduled -> processScheduled(scheduled, method, bean)));
				if (logger.isTraceEnabled()) {
					logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName +
							"': " + annotatedMethods);
				}
			}
		}
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
		try {
			// 创建可执行runnable方法
			Runnable runnable = createRunnable(bean, method);
			boolean processedSchedule = false;
			String errorMessage = "Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";

			// 装载任务，这里长度定为4，因为Spring认为标注4个注解还不够你用的？
			Set<ScheduledTask> tasks = new LinkedHashSet<>(4);

			// Determine initial delay
			// 计算出延时多长时间执行 initialDelayString 支持占位符如：@Scheduled(fixedDelayString = "${time.fixedDelay}")
			// 这段话得意思是，最终拿到一个initialDelay值~~~~~Long型的
			long initialDelay = scheduled.initialDelay();
			String initialDelayString = scheduled.initialDelayString();
			if (StringUtils.hasText(initialDelayString)) {
				Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
				if (this.embeddedValueResolver != null) {
					// 先解析initialDelayString中的#{}
					initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
				}
				if (StringUtils.hasLength(initialDelayString)) {
					try {
						// 然后解析为Long值
						initialDelay = parseDelayAsLong(initialDelayString);
						// initialDelayString 优先级比 initialDelay 高，因为这里一旦initialDelayString解析成功就会导致initialDelay被占用
					}
					catch (RuntimeException ex) {
						throw new IllegalArgumentException(
								"Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
					}
				}
			}

			// Check cron expression
			// 解析cron
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
						// 这个相当于，如果配置了cron，它就是一个task了，就可以吧任务注册进registrar里面了
						// 这里面的处理是。如果已经有调度器taskScheduler了，那就立马准备执行了
						tasks.add(this.registrar.scheduleCronTask(new CronTask(runnable, new CronTrigger(cron, timeZone))));
					}
				}
			}

			// At this point we don't need to differentiate between initial delay set or not anymore
			if (initialDelay < 0) {
				initialDelay = 0;
			}

			// Check fixed delay
			long fixedDelay = scheduled.fixedDelay();
			if (fixedDelay >= 0) {
				Assert.isTrue(!processedSchedule, errorMessage);
				processedSchedule = true;
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
					tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
				}
			}

			// Check fixed rate
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

			// Check whether we had any attribute set
			Assert.isTrue(processedSchedule, errorMessage);

			// Finally register the scheduled tasks
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
		// 标注此注解的方法必须是无参的方法
		Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
		Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
		// ScheduledMethodRunnable的run()方法就是简单的利用反射执行方法
		return new ScheduledMethodRunnable(target, invocableMethod);
	}

	private static long parseDelayAsLong(String value) throws RuntimeException {
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
		// 销毁之前，将注册到scheduledTasks全部清空，并且取消
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
