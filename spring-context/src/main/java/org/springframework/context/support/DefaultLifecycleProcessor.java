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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.Lifecycle;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.Phased;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link LifecycleProcessor} strategy.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 3.0
 */
public class DefaultLifecycleProcessor implements LifecycleProcessor, BeanFactoryAware {

	private final Log logger = LogFactory.getLog(getClass());

	private volatile long timeoutPerShutdownPhase = 30000;

	private volatile boolean running;

	@Nullable
	private volatile ConfigurableListableBeanFactory beanFactory;


	/**
	 * Specify the maximum time allotted in milliseconds for the shutdown of
	 * any phase (group of SmartLifecycle beans with the same 'phase' value).
	 * <p>The default value is 30 seconds.
	 */
	public void setTimeoutPerShutdownPhase(long timeoutPerShutdownPhase) {
		this.timeoutPerShutdownPhase = timeoutPerShutdownPhase;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"DefaultLifecycleProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	private ConfigurableListableBeanFactory getBeanFactory() {
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(beanFactory != null, "No BeanFactory available");
		return beanFactory;
	}


	// Lifecycle implementation

	/**
	 * Start all registered beans that implement {@link Lifecycle} and are <i>not</i>
	 * already running. Any bean that implements {@link SmartLifecycle} will be
	 * started within its 'phase', and all phases will be ordered from lowest to
	 * highest value. All beans that do not implement {@link SmartLifecycle} will be
	 * started in the default phase 0. A bean declared as a dependency of another bean
	 * will be started before the dependent bean regardless of the declared phase.
	 */
	@Override
	public void start() {
		// false，表示所有实现LifeCycle的Bean都会调用其start
		// 这个一般是主动调用 context.start() 触发的哦
		startBeans(false);
		this.running = true;
	}

	/**
	 * Stop all registered beans that implement {@link Lifecycle} and <i>are</i>
	 * currently running. Any bean that implements {@link SmartLifecycle} will be
	 * stopped within its 'phase', and all phases will be ordered from highest to
	 * lowest value. All beans that do not implement {@link SmartLifecycle} will be
	 * stopped in the default phase 0. A bean declared as dependent on another bean
	 * will be stopped before the dependency bean regardless of the declared phase.
	 */
	@Override
	public void stop() {
		stopBeans();
		this.running = false;
	}

	@Override
	public void onRefresh() {
		// getLifecycleProcessor().onRefresh(); 这个方法才是容器启动时候自动会调用的，其余都不是
		// 显然它默认只会执行实现了SmartLifecycle接口并且isAutoStartup = true的Bean的start方法
		startBeans(true);
		this.running = true;
	}

	@Override
	public void onClose() {
		// 容器关闭的时候自动会调的
		stopBeans();
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}


	// Internal helpers

	// 核心方法1
	// autoStartupOnly:是否仅支持自动启动
	// true：只支持伴随容器启动 （bean必须实现了`SmartLifecycle`接口且isAutoStartup为true才行）
	// false：表示无所谓。都会执行bean的start方法=======
	private void startBeans(boolean autoStartupOnly) {
		// 获取所有实现了LifeCycle接口的Bean，因此Bean的生命周期除了以下三种方式
		// 初始化：@PostConstruct、实现InitializingBean接口、指定init-method方法名
		// 销毁：@PreDestroy、实现DisposableBean接口、指定destroy-method方法名
		// 还额外扩展了：LifeCycle的start、stop
		Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
		// phases 这个Map，表示按照phase 值，吧这个Bean进行分组，最后分组执行
		Map<Integer, LifecycleGroup> phases = new HashMap<>();
		lifecycleBeans.forEach((beanName, bean) -> {
			// autoStartupOnly 为false，通常是在AbstractApplicationContext中完成refresh后，即finishRefresh方法中，获取DefaultLifecycleProcessor
			// autoStartupOnly 为true，通常是用户主动调用 AbstractApplicationContext#start，才可保证所有的实现LifeCycle的Bean被调用
			// 因此如果想要，启动时自动完成生命周期的接口，就必须实现SmartLifeCycle接口，并设置为autoStartup为true才可以
			if (!autoStartupOnly || (bean instanceof SmartLifecycle && ((SmartLifecycle) bean).isAutoStartup())) {

				// 获取阶段数
				int phase = getPhase(bean);
				// 获取对应阶段数的LifeCycleGroup
				LifecycleGroup group = phases.get(phase);
				if (group == null) {
					group = new LifecycleGroup(phase, this.timeoutPerShutdownPhase, lifecycleBeans, autoStartupOnly);
					phases.put(phase, group);
				}
				// 添加到phase 值相同的组  分组嘛
				group.add(beanName, bean);
			}
		});
		if (!phases.isEmpty()) {
			List<Integer> keys = new ArrayList<>(phases.keySet());
			// 此处有个根据key从小到大的排序，然后一个个的调用他们的start方法
			Collections.sort(keys);
			for (Integer key : keys) {
				// 接下来调用每个LifeCycle实现类的start()方法
				// 执行顺序：很明显就是phase阶段数越低的越会靠前执行start()
				phases.get(key).start();
			}
		}
	}

	/**
	 * Start the specified bean as part of the given set of Lifecycle beans,
	 * making sure that any beans that it depends on are started first.
	 * @param lifecycleBeans a Map with bean name as key and Lifecycle instance as value
	 * @param beanName the name of the bean to start
	 */
	private void doStart(Map<String, ? extends Lifecycle> lifecycleBeans, String beanName, boolean autoStartupOnly) {
		Lifecycle bean = lifecycleBeans.remove(beanName);
		if (bean != null && bean != this) {
			String[] dependenciesForBean = getBeanFactory().getDependenciesForBean(beanName);
			for (String dependency : dependenciesForBean) {
				doStart(lifecycleBeans, dependency, autoStartupOnly);
			}
			if (!bean.isRunning() &&
					(!autoStartupOnly || !(bean instanceof SmartLifecycle) || ((SmartLifecycle) bean).isAutoStartup())) {
				if (logger.isTraceEnabled()) {
					logger.trace("Starting bean '" + beanName + "' of type [" + bean.getClass().getName() + "]");
				}
				try {
					bean.start();
				}
				catch (Throwable ex) {
					throw new ApplicationContextException("Failed to start bean '" + beanName + "'", ex);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Successfully started bean '" + beanName + "'");
				}
			}
		}
	}

	// 核心方法2
	private void stopBeans() {
		Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
		Map<Integer, LifecycleGroup> phases = new HashMap<>();
		lifecycleBeans.forEach((beanName, bean) -> {
			int shutdownPhase = getPhase(bean);
			LifecycleGroup group = phases.get(shutdownPhase);
			if (group == null) {
				group = new LifecycleGroup(shutdownPhase, this.timeoutPerShutdownPhase, lifecycleBeans, false);
				phases.put(shutdownPhase, group);
			}
			group.add(beanName, bean);
		});
		if (!phases.isEmpty()) {
			List<Integer> keys = new ArrayList<>(phases.keySet());
			keys.sort(Collections.reverseOrder());
			for (Integer key : keys) {
				// 按照阶段数phase从小到大的顺序，依次调用 LifeCycle#stop 方法
				phases.get(key).stop();
			}
		}
	}

	/**
	 * Stop the specified bean as part of the given set of Lifecycle beans,
	 * making sure that any beans that depends on it are stopped first.
	 * @param lifecycleBeans a Map with bean name as key and Lifecycle instance as value
	 * @param beanName the name of the bean to stop
	 */
	private void doStop(Map<String, ? extends Lifecycle> lifecycleBeans, final String beanName,
			final CountDownLatch latch, final Set<String> countDownBeanNames) {

		Lifecycle bean = lifecycleBeans.remove(beanName);
		if (bean != null) {
			String[] dependentBeans = getBeanFactory().getDependentBeans(beanName);
			for (String dependentBean : dependentBeans) {
				doStop(lifecycleBeans, dependentBean, latch, countDownBeanNames);
			}
			try {
				if (bean.isRunning()) {
					if (bean instanceof SmartLifecycle) {
						if (logger.isTraceEnabled()) {
							logger.trace("Asking bean '" + beanName + "' of type [" +
									bean.getClass().getName() + "] to stop");
						}
						countDownBeanNames.add(beanName);
						((SmartLifecycle) bean).stop(() -> {
							latch.countDown();
							countDownBeanNames.remove(beanName);
							if (logger.isDebugEnabled()) {
								logger.debug("Bean '" + beanName + "' completed its stop procedure");
							}
						});
					}
					else {
						if (logger.isTraceEnabled()) {
							logger.trace("Stopping bean '" + beanName + "' of type [" +
									bean.getClass().getName() + "]");
						}
						bean.stop();
						if (logger.isDebugEnabled()) {
							logger.debug("Successfully stopped bean '" + beanName + "'");
						}
					}
				}
				else if (bean instanceof SmartLifecycle) {
					// Don't wait for beans that aren't running...
					latch.countDown();
				}
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failed to stop bean '" + beanName + "'", ex);
				}
			}
		}
	}


	// overridable hooks

	/**
	 * Retrieve all applicable Lifecycle beans: all singletons that have already been created,
	 * as well as all SmartLifecycle beans (even if they are marked as lazy-init).
	 * @return the Map of applicable beans, with bean names as keys and bean instances as values
	 */
	protected Map<String, Lifecycle> getLifecycleBeans() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		Map<String, Lifecycle> beans = new LinkedHashMap<>();
		String[] beanNames = beanFactory.getBeanNamesForType(Lifecycle.class, false, false);
		// 从BeanFactory中获取所有的Lifecycle的子类
		for (String beanName : beanNames) {
			String beanNameToRegister = BeanFactoryUtils.transformedBeanName(beanName);
			boolean isFactoryBean = beanFactory.isFactoryBean(beanNameToRegister);
			String beanNameToCheck = (isFactoryBean ? BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
			if ((beanFactory.containsSingleton(beanNameToRegister) &&
					(!isFactoryBean || matchesBeanType(Lifecycle.class, beanNameToCheck, beanFactory))) ||
					matchesBeanType(SmartLifecycle.class, beanNameToCheck, beanFactory)) {
				Object bean = beanFactory.getBean(beanNameToCheck);
				if (bean != this && bean instanceof Lifecycle) {
					beans.put(beanNameToRegister, (Lifecycle) bean);
				}
			}
		}
		// 返回一个Map结构，key为标准BeanName，value为LifeCycle接口实现类
		return beans;
	}

	private boolean matchesBeanType(Class<?> targetType, String beanName, BeanFactory beanFactory) {
		Class<?> beanType = beanFactory.getType(beanName);
		return (beanType != null && targetType.isAssignableFrom(beanType));
	}

	/**
	 * Determine the lifecycle phase of the given bean.
	 * <p>The default implementation checks for the {@link Phased} interface, using
	 * a default of 0 otherwise. Can be overridden to apply other/further policies.
	 * @param bean the bean to introspect
	 * @return the phase (an integer value)
	 * @see Phased#getPhase()
	 * @see SmartLifecycle
	 */
	protected int getPhase(Lifecycle bean) {
		return (bean instanceof Phased ? ((Phased) bean).getPhase() : 0);
	}


	/**
	 * Helper class for maintaining a group of Lifecycle beans that should be started
	 * and stopped together based on their 'phase' value (or the default value of 0).
	 */
	private class LifecycleGroup {
		// 用于维护一组lifecycle生命周期bean的Helper类
		// 这些bean应根据其“phase”值（或默认值0）一起启动和停止。

		private final int phase;

		private final long timeout;

		private final Map<String, ? extends Lifecycle> lifecycleBeans;

		private final boolean autoStartupOnly; // 只能自动启动

		private final List<LifecycleGroupMember> members = new ArrayList<>();

		private int smartMemberCount;

		public LifecycleGroup(
				int phase, long timeout, Map<String, ? extends Lifecycle> lifecycleBeans, boolean autoStartupOnly) {

			this.phase = phase;
			this.timeout = timeout;
			this.lifecycleBeans = lifecycleBeans;
			this.autoStartupOnly = autoStartupOnly;
		}

		public void add(String name, Lifecycle bean) {
			this.members.add(new LifecycleGroupMember(name, bean));
			if (bean instanceof SmartLifecycle) {
				this.smartMemberCount++;
			}
		}

		public void start() {
			if (this.members.isEmpty()) {
				return;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Starting beans in phase " + this.phase);
			}
			// 按照权重值进行排序  若没有实现Smart接口的权重值都为0
			Collections.sort(this.members);
			for (LifecycleGroupMember member : this.members) {
				// 一次执行这些Bean的start方法（这里面逻辑就没啥好看的，只有一个考虑到getBeanFactory().dependenciesForBean控制Bean的依赖关系的）
				doStart(this.lifecycleBeans, member.name, this.autoStartupOnly);
			}
		}

		public void stop() {
			if (this.members.isEmpty()) {
				return;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Stopping beans in phase " + this.phase);
			}
			this.members.sort(Collections.reverseOrder());
			CountDownLatch latch = new CountDownLatch(this.smartMemberCount);
			Set<String> countDownBeanNames = Collections.synchronizedSet(new LinkedHashSet<>());
			Set<String> lifecycleBeanNames = new HashSet<>(this.lifecycleBeans.keySet());
			for (LifecycleGroupMember member : this.members) {
				if (lifecycleBeanNames.contains(member.name)) {
					doStop(this.lifecycleBeans, member.name, latch, countDownBeanNames);
				}
				else if (member.bean instanceof SmartLifecycle) {
					// Already removed: must have been a dependent bean from another phase
					latch.countDown();
				}
			}
			try {
				latch.await(this.timeout, TimeUnit.MILLISECONDS);
				if (latch.getCount() > 0 && !countDownBeanNames.isEmpty() && logger.isInfoEnabled()) {
					logger.info("Failed to shut down " + countDownBeanNames.size() + " bean" +
							(countDownBeanNames.size() > 1 ? "s" : "") + " with phase value " +
							this.phase + " within timeout of " + this.timeout + "ms: " + countDownBeanNames);
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}


	/**
	 * Adapts the Comparable interface onto the lifecycle phase model.
	 */
	private class LifecycleGroupMember implements Comparable<LifecycleGroupMember> {
		// 封装 - name + bean

		private final String name;

		private final Lifecycle bean;

		LifecycleGroupMember(String name, Lifecycle bean) {
			this.name = name;
			this.bean = bean;
		}

		@Override
		public int compareTo(LifecycleGroupMember other) {
			// 根据阶段比较
			int thisPhase = getPhase(this.bean);
			int otherPhase = getPhase(other.bean);
			return Integer.compare(thisPhase, otherPhase);
		}
	}

}
