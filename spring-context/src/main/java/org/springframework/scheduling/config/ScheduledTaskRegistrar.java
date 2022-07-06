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

package org.springframework.scheduling.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Helper bean for registering tasks with a {@link TaskScheduler}, typically using cron
 * expressions.
 *
 * <p>As of Spring 3.1, {@code ScheduledTaskRegistrar} has a more prominent user-facing
 * role when used in conjunction with the {@link
 * org.springframework.scheduling.annotation.EnableAsync @EnableAsync} annotation and its
 * {@link org.springframework.scheduling.annotation.SchedulingConfigurer
 * SchedulingConfigurer} callback interface.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Tobias Montagna-Hay
 * @author Sam Brannen
 * @since 3.0
 * @see org.springframework.scheduling.annotation.EnableAsync
 * @see org.springframework.scheduling.annotation.SchedulingConfigurer
 */
public class ScheduledTaskRegistrar implements ScheduledTaskHolder, InitializingBean, DisposableBean {
	// 位于: org.springframework.scheduling.config
	// 版本: 3.0 
	
	// 实现 ScheduledTaskHolder 接口：
	// 我们可议通过扩展实现此接口，来定制化属于自己的ScheduledTaskRegistrar 下文会有详细介绍
	// 它实现了InitializingBean和DisposableBean，所以我们也可以把它放进容器里面

	/**
	 * A special cron expression value that indicates a disabled trigger: {@value}.
	 * <p>This is primarily meant for use with {@link #addCronTask(Runnable, String)}
	 * when the value for the supplied {@code expression} is retrieved from an
	 * external source &mdash; for example, from a property in the
	 * {@link org.springframework.core.env.Environment Environment}.
	 * @since 5.2
	 * @see org.springframework.scheduling.annotation.Scheduled#CRON_DISABLED
	 */
	public static final String CRON_DISABLED = "-";


	// 任务调度器 -- 可以向其中注册定时任务
	// 1. 用户通过实现SchedulingConfigurer.configureTasks(ScheduledTaskRegistrar)允许向ScheduledTaskRegistrar.setTaskScheduler()定制调度器
	// 2. 去ioc容器中查找
	// 		2.1 从ioc容器查找一个TaskScheduler的bean使用,如果恰好只有一个，直接返回
	// 			2.1.1 在2.1如果有多个TaskScheduler，就去查找一个beanName为“taskScheduler”，beanType为TaskScheduler的bean，找不到就报错啦
	// 			2.1.2 在2.2如果没有找到任何TaskScheduler的bean，那就去查找ScheduledExecutorService类型的一个bean，如果有多个bean，同样通过beanName=”taskScheduler“进行过滤
	// 3. 在ScheduledTaskRegistrar.afterPropertiesSet()中如果发现还没有设置过TaskScheduler，就使用默认的ConcurrentTaskScheduler作为TaskScheduler，并且本地的Executor设置为newSingleThreadScheduledExecutor()
	@Nullable
	private TaskScheduler taskScheduler;

	// 该类事JUC包中的类
	@Nullable
	private ScheduledExecutorService localExecutor;

	// 对任务进行分类 管理
	// 含义: 在ScheduledAnnotatedBeanPostProcessor.postProcessAfterInitialization()->processScheduled()方法中，需要将从@Scheduled标注的方法中
	// 处理得到的Task[CronTask等等]注册到ScheduledTaskRegistrar中
	// 但是如果ScheduledTaskRegistrar.taskScheduler任务调度器为空，不好意思，无法立即执行，就先存放在unresolvedTasks字段以及各自对应的triggerTasks/cronTasks等字段中，
	// 待后续当前类的afterPropertiesSet()中去统一处理未完成的任务
	// afterPropertiesSet()中发现没有任务调度器时会兜底创建一个ConcurrentTaskScheduler来处理


	// 一次性任务,到时就执行
	@Nullable
	private List<TriggerTask> triggerTasks;

	// Cron表达式的周期任务tasks
	@Nullable
	private List<CronTask> cronTasks;

	// 固定Rate的tasks
	@Nullable
	private List<IntervalTask> fixedRateTasks;

	// 固定Delay延迟的周期tasks
	@Nullable
	private List<IntervalTask> fixedDelayTasks;

	// 未解决的任务
	private final Map<Task, ScheduledTask> unresolvedTasks = new HashMap<>(16);

	// 计划的任务 -> 上面的triggerTasks/cronTasks/fixedRateTasks/fixedDelayTasks最终都会转换为ScheduledTask哦
	private final Set<ScheduledTask> scheduledTasks = new LinkedHashSet<>(16);

	
	// 没有构造器
	
	/**
	 * Set the {@link TaskScheduler} to register scheduled tasks with.
	 */
	public void setTaskScheduler(TaskScheduler taskScheduler) {
		// 规则:
		//
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		this.taskScheduler = taskScheduler;
	}

	/**
	 * Set the {@link TaskScheduler} to register scheduled tasks with, or a
	 * {@link java.util.concurrent.ScheduledExecutorService} to be wrapped as a
	 * {@code TaskScheduler}.
	 */
	public void setScheduler(@Nullable Object scheduler) {
		// 设置调度器对象
		
		if (scheduler == null) {
			this.taskScheduler = null;
		}
		else if (scheduler instanceof TaskScheduler) {
			this.taskScheduler = (TaskScheduler) scheduler;
		}
		else if (scheduler instanceof ScheduledExecutorService) {
			this.taskScheduler = new ConcurrentTaskScheduler(((ScheduledExecutorService) scheduler));
		}
		else {
			throw new IllegalArgumentException("Unsupported scheduler type: " + scheduler.getClass());
		}
	}

	/**
	 * Return the {@link TaskScheduler} instance for this registrar (may be {@code null}).
	 */
	@Nullable
	public TaskScheduler getScheduler() {
		return this.taskScheduler;
	}


	/**
	 * Specify triggered tasks as a Map of Runnables (the tasks) and Trigger objects
	 * (typically custom implementations of the {@link Trigger} interface).
	 */
	public void setTriggerTasks(Map<Runnable, Trigger> triggerTasks) {
		this.triggerTasks = new ArrayList<>();
		triggerTasks.forEach((task, trigger) -> addTriggerTask(new TriggerTask(task, trigger)));
	}

	/**
	 * Specify triggered tasks as a list of {@link TriggerTask} objects. Primarily used
	 * by {@code <task:*>} namespace parsing.
	 * @since 3.2
	 * @see ScheduledTasksBeanDefinitionParser
	 */
	public void setTriggerTasksList(List<TriggerTask> triggerTasks) {
		this.triggerTasks = triggerTasks;
	}

	/**
	 * Get the trigger tasks as an unmodifiable list of {@link TriggerTask} objects.
	 * @return the list of tasks (never {@code null})
	 * @since 4.2
	 */
	public List<TriggerTask> getTriggerTaskList() {
		return (this.triggerTasks != null? Collections.unmodifiableList(this.triggerTasks) :
				Collections.emptyList());
	}

	/**
	 * Specify triggered tasks as a Map of Runnables (the tasks) and cron expressions.
	 * @see CronTrigger
	 */
	public void setCronTasks(Map<Runnable, String> cronTasks) {
		this.cronTasks = new ArrayList<>();
		cronTasks.forEach(this::addCronTask);
	}

	/**
	 * Specify triggered tasks as a list of {@link CronTask} objects. Primarily used by
	 * {@code <task:*>} namespace parsing.
	 * @since 3.2
	 * @see ScheduledTasksBeanDefinitionParser
	 */
	public void setCronTasksList(List<CronTask> cronTasks) {
		this.cronTasks = cronTasks;
	}

	/**
	 * Get the cron tasks as an unmodifiable list of {@link CronTask} objects.
	 * @return the list of tasks (never {@code null})
	 * @since 4.2
	 */
	public List<CronTask> getCronTaskList() {
		return (this.cronTasks != null ? Collections.unmodifiableList(this.cronTasks) :
				Collections.emptyList());
	}

	/**
	 * Specify triggered tasks as a Map of Runnables (the tasks) and fixed-rate values.
	 * @see TaskScheduler#scheduleAtFixedRate(Runnable, long)
	 */
	public void setFixedRateTasks(Map<Runnable, Long> fixedRateTasks) {
		this.fixedRateTasks = new ArrayList<>();
		fixedRateTasks.forEach(this::addFixedRateTask);
	}

	/**
	 * Specify fixed-rate tasks as a list of {@link IntervalTask} objects. Primarily used
	 * by {@code <task:*>} namespace parsing.
	 * @since 3.2
	 * @see ScheduledTasksBeanDefinitionParser
	 */
	public void setFixedRateTasksList(List<IntervalTask> fixedRateTasks) {
		this.fixedRateTasks = fixedRateTasks;
	}

	/**
	 * Get the fixed-rate tasks as an unmodifiable list of {@link IntervalTask} objects.
	 * @return the list of tasks (never {@code null})
	 * @since 4.2
	 */
	public List<IntervalTask> getFixedRateTaskList() {
		return (this.fixedRateTasks != null ? Collections.unmodifiableList(this.fixedRateTasks) :
				Collections.emptyList());
	}

	/**
	 * Specify triggered tasks as a Map of Runnables (the tasks) and fixed-delay values.
	 * @see TaskScheduler#scheduleWithFixedDelay(Runnable, long)
	 */
	public void setFixedDelayTasks(Map<Runnable, Long> fixedDelayTasks) {
		this.fixedDelayTasks = new ArrayList<>();
		fixedDelayTasks.forEach(this::addFixedDelayTask);
	}

	/**
	 * Specify fixed-delay tasks as a list of {@link IntervalTask} objects. Primarily used
	 * by {@code <task:*>} namespace parsing.
	 * @since 3.2
	 * @see ScheduledTasksBeanDefinitionParser
	 */
	public void setFixedDelayTasksList(List<IntervalTask> fixedDelayTasks) {
		this.fixedDelayTasks = fixedDelayTasks;
	}

	/**
	 * Get the fixed-delay tasks as an unmodifiable list of {@link IntervalTask} objects.
	 * @return the list of tasks (never {@code null})
	 * @since 4.2
	 */
	public List<IntervalTask> getFixedDelayTaskList() {
		return (this.fixedDelayTasks != null ? Collections.unmodifiableList(this.fixedDelayTasks) :
				Collections.emptyList());
	}


	/**
	 * Add a Runnable task to be triggered per the given {@link Trigger}.
	 * @see TaskScheduler#scheduleAtFixedRate(Runnable, long)
	 */
	public void addTriggerTask(Runnable task, Trigger trigger) {
		addTriggerTask(new TriggerTask(task, trigger));
	}

	/**
	 * Add a {@code TriggerTask}.
	 * @since 3.2
	 * @see TaskScheduler#scheduleAtFixedRate(Runnable, long)
	 */
	public void addTriggerTask(TriggerTask task) {
		if (this.triggerTasks == null) {
			this.triggerTasks = new ArrayList<>();
		}
		this.triggerTasks.add(task);
	}

	/**
	 * Add a {@link Runnable} task to be triggered per the given cron {@code expression}.
	 * <p>As of Spring Framework 5.2, this method will not register the task if the
	 * {@code expression} is equal to {@link #CRON_DISABLED}.
	 */
	public void addCronTask(Runnable task, String expression) {
		if (!CRON_DISABLED.equals(expression)) {
			addCronTask(new CronTask(task, expression));
		}
	}

	/**
	 * Add a {@link CronTask}.
	 * @since 3.2
	 */
	public void addCronTask(CronTask task) {
		if (this.cronTasks == null) {
			this.cronTasks = new ArrayList<>();
		}
		this.cronTasks.add(task);
	}

	/**
	 * Add a {@code Runnable} task to be triggered at the given fixed-rate interval.
	 * @see TaskScheduler#scheduleAtFixedRate(Runnable, long)
	 */
	public void addFixedRateTask(Runnable task, long interval) {
		addFixedRateTask(new IntervalTask(task, interval, 0));
	}

	/**
	 * Add a fixed-rate {@link IntervalTask}.
	 * @since 3.2
	 * @see TaskScheduler#scheduleAtFixedRate(Runnable, long)
	 */
	public void addFixedRateTask(IntervalTask task) {
		if (this.fixedRateTasks == null) {
			this.fixedRateTasks = new ArrayList<>();
		}
		this.fixedRateTasks.add(task);
	}

	/**
	 * Add a Runnable task to be triggered with the given fixed delay.
	 * @see TaskScheduler#scheduleWithFixedDelay(Runnable, long)
	 */
	public void addFixedDelayTask(Runnable task, long delay) {
		addFixedDelayTask(new IntervalTask(task, delay, 0));
	}

	/**
	 * Add a fixed-delay {@link IntervalTask}.
	 * @since 3.2
	 * @see TaskScheduler#scheduleWithFixedDelay(Runnable, long)
	 */
	public void addFixedDelayTask(IntervalTask task) {
		if (this.fixedDelayTasks == null) {
			this.fixedDelayTasks = new ArrayList<>();
		}
		this.fixedDelayTasks.add(task);
	}


	/**
	 * Return whether this {@code ScheduledTaskRegistrar} has any tasks registered.
	 * @since 3.2
	 */
	public boolean hasTasks() {
		// 是否有triggerTasks、cronTasks、fixedRateTasks、fixedDelayTasks
		return (!CollectionUtils.isEmpty(this.triggerTasks) ||
				!CollectionUtils.isEmpty(this.cronTasks) ||
				!CollectionUtils.isEmpty(this.fixedRateTasks) ||
				!CollectionUtils.isEmpty(this.fixedDelayTasks));
	}


	/**
	 * Calls {@link #scheduleTasks()} at bean construction time.
	 */
	@Override
	public void afterPropertiesSet() {
		// ❗️❗️❗️
		// 核心 -- 开始将定时任务注册到调度器中
		scheduleTasks();
	}

	/**
	 * Schedule all registered tasks against the underlying
	 * {@linkplain #setTaskScheduler(TaskScheduler) task scheduler}.
	 */
	@SuppressWarnings("deprecation")
	protected void scheduleTasks() {
		// 关于TaskScheduler的设置时机如下:
		// 1. 用户通过实现SchedulingConfigurer.configureTasks(ScheduledTaskRegistrar)允许向ScheduledTaskRegistrar.setTaskScheduler()定制调度器
		// 2. 如果没有在1中去完成,就去ioc容器中查找
		// 		2.1 从ioc容器查找一个TaskScheduler的bean使用,如果恰好只有一个，直接返回
		// 			2.1.1 在2.1如果有多个TaskScheduler，就去查找一个beanName为“taskScheduler”，beanType为TaskScheduler的bean，找不到就报错啦
		// 			2.1.2 在2.2如果没有找到任何TaskScheduler的bean，那就去查找ScheduledExecutorService类型的一个bean，如果有多个bean，同样通过beanName=”taskScheduler“进行过滤
		// 3. 如果在2中去ioc中也找不到,那就会在ScheduledTaskRegistrar.afterPropertiesSet()中如果发现还没有设置过TaskScheduler，就使用默认的ConcurrentTaskScheduler作为TaskScheduler，并且本地的Executor设置为newSingleThreadScheduledExecutor()

		// 1. 还是没有调度器就使用默认的ConcurrentTaskScheduler，
		// 并且本地的线程池即执行器使用一个newSingleThreadScheduledExecutor()单例的线程池啦 -- 
		// ❗️❗️❗️ newSingleThreadScheduledExecutor -> 导致一个问题:因此如果同一个时刻有10个任务启动,就会出现争夺在这个线程池资源的问题哦
		if (this.taskScheduler == null) {
			this.localExecutor = Executors.newSingleThreadScheduledExecutor();
			this.taskScheduler = new ConcurrentTaskScheduler(this.localExecutor);
		}
		// 2. 下面几步：将各种之前注册但未加入到调度器中的tasks转换为ScheduledTask后加入到scheduledTasks集合中
		// 然后再 scheduleTriggerTask()/scheduleCronTask()/scheduleFixedRateTask()/scheduleFixedDelayTask()
		// 就会发现taskScheduler不为空,从而将tasks转为ScheduledTask后加入到调度器中去执行哦
		if (this.triggerTasks != null) {
			for (TriggerTask task : this.triggerTasks) {
				// 2.1 scheduleTriggerTask() 将 TriggerTask 适配为 ScheduledTask
				addScheduledTask(scheduleTriggerTask(task));
			}
		}
		if (this.cronTasks != null) {
			for (CronTask task : this.cronTasks) {
				// 2.1 scheduleCronTask() 将 CronTask 适配为 ScheduledTask
				addScheduledTask(scheduleCronTask(task));
			}
		}
		if (this.fixedRateTasks != null) {
			for (IntervalTask task : this.fixedRateTasks) {
				// 2.1 scheduleFixedRateTask() 将 IntervalTask 适配为 ScheduledTask
				addScheduledTask(scheduleFixedRateTask(task));
			}
		}
		if (this.fixedDelayTasks != null) {
			for (IntervalTask task : this.fixedDelayTasks) {
				// 2.1 scheduleFixedDelayTask() 将 IntervalTask 适配为 ScheduledTask
				addScheduledTask(scheduleFixedDelayTask(task));
			}
		}
	}

	private void addScheduledTask(@Nullable ScheduledTask task) {
		// 全部加入到scheduledTasks
		if (task != null) {
			this.scheduledTasks.add(task);
		}
	}


	/**
	 * Schedule the specified trigger task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * @since 4.3
	 */
	@Nullable
	public ScheduledTask scheduleTriggerTask(TriggerTask task) {
		
		// 1. 
		// 
		
		ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
		boolean newTask = false;
		if (scheduledTask == null) {
			scheduledTask = new ScheduledTask(task);
			newTask = true;
		}
		if (this.taskScheduler != null) {
			scheduledTask.future = this.taskScheduler.schedule(task.getRunnable(), task.getTrigger());
		}
		else {
			addTriggerTask(task);
			this.unresolvedTasks.put(task, scheduledTask);
		}
		return (newTask ? scheduledTask : null);
	}

	/**
	 * Schedule the specified cron task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * (or {@code null} if processing a previously registered task)
	 * @since 4.3
	 */
	@Nullable
	public ScheduledTask scheduleCronTask(CronTask task) {
		// 该方法的执行：
		//	第一次：当在ScheduledAnnotationBeanPostProcessor.processScheduled()时如果有@Scheduled的cron属性就会触发当前方法
		//		99%的情况由于没有注册TaskScheduler，导致注册的CronTask不能被加入到调度器中，只能放入unresolvedTasks与cronTasks中
		//	第二次：当在ScheduledTaskRegistrar.afterPropertiesSet()被调用是会触发这里的scheduleTriggerTask(TriggerTask task)
		//		此刻TaskScheduler必定是存在，即使用户没有注册，也会使用默认的ConcurrentTaskScheduler，然后这个时候从unresolvedTasks.remove(task)
		//		就可以返回对应的ScheduledTask，并将其加入到ConcurrentTaskScheduler.schedule()调度起来哦
		
		ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
		boolean newTask = false;
		// 1.这是一个新的任务CronTask，将标记为newTask设为true,并封装为ScheduledTask
		if (scheduledTask == null) {
			scheduledTask = new ScheduledTask(task);
			newTask = true;
		}
		// 2. 调度器不为空，就直接调用调度的schedule方法执行 -> 并获取一个Future设置到ScheduledTask中
		if (this.taskScheduler != null) {
			scheduledTask.future = this.taskScheduler.schedule(task.getRunnable(), task.getTrigger());
		}
		// 3. 如果暂时没有调度器，就先加入到cronTasks中，并将其加入中缓存到unresolvedTasks
		// 在后续的 ScheduledTaskRegistratr.afterPropertiesSet()中会触发
		else {
			addCronTask(task);
			this.unresolvedTasks.put(task, scheduledTask);
		}
		return (newTask ? scheduledTask : null);
	}

	/**
	 * Schedule the specified fixed-rate task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * (or {@code null} if processing a previously registered task)
	 * @since 4.3
	 * @deprecated as of 5.0.2, in favor of {@link #scheduleFixedRateTask(FixedRateTask)}
	 */
	@Deprecated
	@Nullable
	public ScheduledTask scheduleFixedRateTask(IntervalTask task) {
		FixedRateTask taskToUse = (task instanceof FixedRateTask ? (FixedRateTask) task :
				new FixedRateTask(task.getRunnable(), task.getInterval(), task.getInitialDelay()));
		return scheduleFixedRateTask(taskToUse);
	}

	/**
	 * Schedule the specified fixed-rate task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * (or {@code null} if processing a previously registered task)
	 * @since 5.0.2
	 */
	@Nullable
	public ScheduledTask scheduleFixedRateTask(FixedRateTask task) {
		// 和ScheduledTask scheduleCronTask(CronTask task)同样的逻辑，忽略~~
		
		ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
		boolean newTask = false;
		if (scheduledTask == null) {
			scheduledTask = new ScheduledTask(task);
			newTask = true;
		}
		if (this.taskScheduler != null) {
			if (task.getInitialDelay() > 0) {
				Date startTime = new Date(System.currentTimeMillis() + task.getInitialDelay());
				scheduledTask.future = this.taskScheduler.scheduleAtFixedRate(task.getRunnable(), startTime, task.getInterval());
			}
			else {
				scheduledTask.future = this.taskScheduler.scheduleAtFixedRate(task.getRunnable(), task.getInterval());
			}
		}
		else {
			// 没有调度器，就预先添加到FixedRateTask集合以及unresolvedTasks集合中
			addFixedRateTask(task);
			this.unresolvedTasks.put(task, scheduledTask);
		}
		// 如果是newTask，就返回新建的scheduledTask，否则返回null
		return (newTask ? scheduledTask : null);
	}

	/**
	 * Schedule the specified fixed-delay task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * (or {@code null} if processing a previously registered task)
	 * @since 4.3
	 * @deprecated as of 5.0.2, in favor of {@link #scheduleFixedDelayTask(FixedDelayTask)}
	 */
	@Deprecated
	@Nullable
	public ScheduledTask scheduleFixedDelayTask(IntervalTask task) {
		FixedDelayTask taskToUse = (task instanceof FixedDelayTask ? (FixedDelayTask) task :
				new FixedDelayTask(task.getRunnable(), task.getInterval(), task.getInitialDelay()));
		return scheduleFixedDelayTask(taskToUse);
	}

	/**
	 * Schedule the specified fixed-delay task, either right away if possible
	 * or on initialization of the scheduler.
	 * @return a handle to the scheduled task, allowing to cancel it
	 * (or {@code null} if processing a previously registered task)
	 * @since 5.0.2
	 */
	@Nullable
	public ScheduledTask scheduleFixedDelayTask(FixedDelayTask task) {
		// 和ScheduledTask scheduleCronTask(CronTask task)同样的逻辑，忽略~~
		
		ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
		boolean newTask = false;
		if (scheduledTask == null) {
			scheduledTask = new ScheduledTask(task);
			newTask = true;
		}
		if (this.taskScheduler != null) {
			if (task.getInitialDelay() > 0) {
				Date startTime = new Date(System.currentTimeMillis() + task.getInitialDelay());
				scheduledTask.future = this.taskScheduler.scheduleWithFixedDelay(task.getRunnable(), startTime, task.getInterval());
			}
			else {
				scheduledTask.future = this.taskScheduler.scheduleWithFixedDelay(task.getRunnable(), task.getInterval());
			}
		}
		else {
			addFixedDelayTask(task);
			this.unresolvedTasks.put(task, scheduledTask);
		}
		return (newTask ? scheduledTask : null);
	}


	/**
	 * Return all locally registered tasks that have been scheduled by this registrar.
	 * @since 5.0.2
	 * @see #addTriggerTask
	 * @see #addCronTask
	 * @see #addFixedRateTask
	 * @see #addFixedDelayTask
	 */
	@Override
	public Set<ScheduledTask> getScheduledTasks() {
		return Collections.unmodifiableSet(this.scheduledTasks);
	}

	@Override
	public void destroy() {
		for (ScheduledTask task : this.scheduledTasks) {
			task.cancel();
		}
		if (this.localExecutor != null) {
			this.localExecutor.shutdownNow();
		}
	}

}
