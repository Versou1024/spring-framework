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

package org.springframework.context;

/**
 * A common interface defining methods for start/stop lifecycle control.
 * The typical use case for this is to control asynchronous processing.
 * <b>NOTE: This interface does not imply specific auto-startup semantics.
 * Consider implementing {@link SmartLifecycle} for that purpose.</b>
 *
 * <p>Can be implemented by both components (typically a Spring bean defined in a
 * Spring context) and containers  (typically a Spring {@link ApplicationContext}
 * itself). Containers will propagate start/stop signals to all components that
 * apply within each container, e.g. for a stop/restart scenario at runtime.
 *
 * <p>Can be used for direct invocations or for management operations via JMX.
 * In the latter case, the {@link org.springframework.jmx.export.MBeanExporter}
 * will typically be defined with an
 * {@link org.springframework.jmx.export.assembler.InterfaceBasedMBeanInfoAssembler},
 * restricting the visibility of activity-controlled components to the Lifecycle
 * interface.
 *
 * <p>Note that the present {@code Lifecycle} interface is only supported on
 * <b>top-level singleton beans</b>. On any other component, the {@code Lifecycle}
 * interface will remain undetected and hence ignored. Also, note that the extended
 * {@link SmartLifecycle} interface provides sophisticated integration with the
 * application context's startup and shutdown phases.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see SmartLifecycle
 * @see ConfigurableApplicationContext
 * @see org.springframework.jms.listener.AbstractMessageListenerContainer
 * @see org.springframework.scheduling.quartz.SchedulerFactoryBean
 */
public interface Lifecycle {
	/*
	 * 定义start/stop生命周期控制方法的通用接口。这方面的典型用例是控制异步处理。
	 * 注意：此接口并不含有特定的自动启动语义。如果需要考虑为此实现SmartLifecycle。
	 *
	 * 可以由组件（通常是在Spring上下文中定义的SpringBean）和容器（通常是Spring ApplicationContext本身）实现。
	 * 容器将向每个容器中应用的所有组件传播启动/停止信号，例如，对于运行时的停止/重启场景。
	 *
	 * 可用于直接调用或通过JMX进行管理操作。在后一种情况下，组织。springframework。jmx。出口MBeanExporter通常由一个组织定义。springframework。jmx。出口汇编程序。InterfaceBasedMBeanInfoAssembler，将活动控制组件的可见性限制在生命周期接口上。
	 * 请注意，目前的Lifecycle接口仅在顶级单例bean上实现。在任何其他组件上，Lifecycle接口都不会被检测到，因此会被忽略。
	 * 另外，请注意，扩展的SmartLifecycle接口提供了与应用程序上下文的启动和关闭阶段的复杂集成。
	 *
	 * API：start、stop、isRunning
	 *
	 * 在 Spring 中还提供了 Lifecycle 接口， Lifecycle 中包含start/stop方法，实现此接口后Spring会保证在启动的时候调用其start方法开始生命周期，并在Spring关闭的时候调用 stop方法来结束生命周期，
	 * 通常用来配置后台程序，在启动后一直运行（如对 MQ 进行轮询等）。
	 * 而ApplicationContext的初始化最后正是保证了这一功能的实现。
	 */

	/**
	 * Start this component.
	 * <p>Should not throw an exception if the component is already running.
	 * <p>In the case of a container, this will propagate the start signal to all
	 * components that apply.
	 * @see SmartLifecycle#isAutoStartup()
	 */
	void start(); // 启动组件component，如果组件component已经启动执行start()也不会抛出异常
	// 如果是容器的话，就会启动容器中所有的组件

	/**
	 * Stop this component, typically in a synchronous fashion, such that the component is
	 * fully stopped upon return of this method. Consider implementing {@link SmartLifecycle}
	 * and its {@code stop(Runnable)} variant when asynchronous stop behavior is necessary.
	 * <p>Note that this stop notification is not guaranteed to come before destruction:
	 * On regular shutdown, {@code Lifecycle} beans will first receive a stop notification
	 * before the general destruction callbacks are being propagated; however, on hot
	 * refresh during a context's lifetime or on aborted refresh attempts, a given bean's
	 * destroy method will be called without any consideration of stop signals upfront.
	 * <p>Should not throw an exception if the component is not running (not started yet).
	 * <p>In the case of a container, this will propagate the stop signal to all components
	 * that apply.
	 * @see SmartLifecycle#stop(Runnable)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	void stop(); //

	/**
	 * Check whether this component is currently running.
	 * <p>In the case of a container, this will return {@code true} only if <i>all</i>
	 * components that apply are currently running.
	 * @return whether the component is currently running
	 */
	boolean isRunning(); // 监测组件是否正在运行中，如果是一个容器的话，只有容器中所有的组件都在运行才返回true

}
