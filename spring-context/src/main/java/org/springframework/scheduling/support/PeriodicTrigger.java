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

package org.springframework.scheduling.support;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;

/**
 * A trigger for periodic task execution. The period may be applied as either
 * fixed-rate or fixed-delay, and an initial delay value may also be configured.
 * The default initial delay is 0, and the default behavior is fixed-delay
 * (i.e. the interval between successive executions is measured from each
 * <i>completion</i> time). To measure the interval between the
 * scheduled <i>start</i> time of each execution instead, set the
 * 'fixedRate' property to {@code true}.
 *
 * <p>Note that the TaskScheduler interface already defines methods for scheduling
 * tasks at fixed-rate or with fixed-delay. Both also support an optional value
 * for the initial delay. Those methods should be used directly whenever
 * possible. The value of this Trigger implementation is that it can be used
 * within components that rely on the Trigger abstraction. For example, it may
 * be convenient to allow periodic triggers, cron-based triggers, and even
 * custom Trigger implementations to be used interchangeably.
 *
 * @author Mark Fisher
 * @since 3.0
 */
public class PeriodicTrigger implements Trigger {
	// Trigger的作用: 计算任务下一次的触发时间
	// PeriodicTrigger = Periodic Trigger 定期触发 -> 主要是为 FixedRateTask 和 FixedDelayTask 计算任务下一次的触发时间
	

	// long类型，表示间隔时长，注意在fixedRate与fixedDelay两种模式下的不同含义
	private final long period;

	// TimeUnit类型，表示间隔时长的单位，如毫秒等；默认是毫秒
	private final TimeUnit timeUnit;

	// long类型，表示启动后任务间隔多长时间开始执行第一次任务
	private volatile long initialDelay = 0; 

	//  boolean类型，表示是否是fixedRate，
	//  True是fixedRate
	//  False是fixedDelay，
	private volatile boolean fixedRate = false; 


	/**
	 * Create a trigger with the given period in milliseconds.
	 */
	public PeriodicTrigger(long period) {
		this(period, null);
	}

	/**
	 * Create a trigger with the given period and time unit. The time unit will
	 * apply not only to the period but also to any 'initialDelay' value, if
	 * configured on this Trigger later via {@link #setInitialDelay(long)}.
	 */
	public PeriodicTrigger(long period, @Nullable TimeUnit timeUnit) {
		Assert.isTrue(period >= 0, "period must not be negative");
		this.timeUnit = (timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS);
		this.period = this.timeUnit.toMillis(period);
	}


	/**
	 * Return this trigger's period.
	 * @since 5.0.2
	 */
	public long getPeriod() {
		return this.period;
	}

	/**
	 * Return this trigger's time unit (milliseconds by default).
	 * @since 5.0.2
	 */
	public TimeUnit getTimeUnit() {
		return this.timeUnit;
	}

	/**
	 * Specify the delay for the initial execution. It will be evaluated in
	 * terms of this trigger's {@link TimeUnit}. If no time unit was explicitly
	 * provided upon instantiation, the default is milliseconds.
	 */
	public void setInitialDelay(long initialDelay) {
		this.initialDelay = this.timeUnit.toMillis(initialDelay);
	}

	/**
	 * Return the initial delay, or 0 if none.
	 * @since 5.0.2
	 */
	public long getInitialDelay() {
		return this.initialDelay;
	}

	/**
	 * Specify whether the periodic interval should be measured between the
	 * scheduled start times rather than between actual completion times.
	 * The latter, "fixed delay" behavior, is the default.
	 */
	public void setFixedRate(boolean fixedRate) {
		this.fixedRate = fixedRate;
	}

	/**
	 * Return whether this trigger uses fixed rate ({@code true}) or
	 * fixed delay ({@code false}) behavior.
	 * @since 5.0.2
	 */
	public boolean isFixedRate() {
		return this.fixedRate;
	}


	/**
	 * Returns the time after which a task should run again.
	 */
	@Override
	public Date nextExecutionTime(TriggerContext triggerContext) {
		// 实现Trigger的核心方法 -> 如何计算下一次任务的执行时机
		
		// 0. 从TriggerContext获取上一次任务的相关时间节点
		// 上一次预定执行的开始时间
		Date lastExecution = triggerContext.lastScheduledExecutionTime();
		// 上一次实际执行的结束时间
		Date lastCompletion = triggerContext.lastCompletionTime();
		
		// 2. task还未开始第一次执行，没有上一次执行记录
		if (lastExecution == null || lastCompletion == null) {
			// 2.1 初始化的执行时间：当前时间+initialDelay即可
			return new Date(System.currentTimeMillis() + this.initialDelay);
		}
		// 3. fixedRate 模式
		if (this.fixedRate) {
			// fixedRate是两次任务开始时间的间隔 -- 因此取预定开始执行时间
			return new Date(lastExecution.getTime() + this.period);
		}
		// 4. fixedDelay 模式
		// fixedRate是上一次任务结束时间到下一次任务的开始时间的间隔 -- 因此取实际执行结束的时间
		return new Date(lastCompletion.getTime() + this.period);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PeriodicTrigger)) {
			return false;
		}
		PeriodicTrigger otherTrigger = (PeriodicTrigger) other;
		return (this.fixedRate == otherTrigger.fixedRate && this.initialDelay == otherTrigger.initialDelay &&
				this.period == otherTrigger.period);
	}

	@Override
	public int hashCode() {
		return (this.fixedRate ? 17 : 29) + (int) (37 * this.period) + (int) (41 * this.initialDelay);
	}

}
