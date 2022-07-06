/*
 * Copyright 2002-2015 the original author or authors.
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
import java.util.TimeZone;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

/**
 * {@link Trigger} implementation for cron expressions.
 * Wraps a {@link CronSequenceGenerator}.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see CronSequenceGenerator
 */
public class CronTrigger implements Trigger {
	// Trigger的作用: 计算任务下一次的触发时间
	// CronTrigger = Cron Trigger 使用Cron表达式解析触发 
	// Cron表达式的解析是通过CronSequenceGenerator完成的哦

	private final CronSequenceGenerator sequenceGenerator;


	/**
	 * Build a {@link CronTrigger} from the pattern provided in the default time zone.
	 * @param expression a space-separated list of time fields, following cron
	 * expression conventions
	 */
	public CronTrigger(String expression) {
		this.sequenceGenerator = new CronSequenceGenerator(expression);
	}

	/**
	 * Build a {@link CronTrigger} from the pattern provided in the given time zone.
	 * @param expression a space-separated list of time fields, following cron
	 * expression conventions
	 * @param timeZone a time zone in which the trigger times will be generated
	 */
	public CronTrigger(String expression, TimeZone timeZone) {
		this.sequenceGenerator = new CronSequenceGenerator(expression, timeZone);
	}


	/**
	 * Return the cron pattern that this trigger has been built with.
	 */
	public String getExpression() {
		return this.sequenceGenerator.getExpression();
	}


	/**
	 * Determine the next execution time according to the given trigger context.
	 * <p>Next execution times are calculated based on the
	 * {@linkplain TriggerContext#lastCompletionTime completion time} of the
	 * previous execution; therefore, overlapping executions won't occur.
	 */
	@Override
	public Date nextExecutionTime(TriggerContext triggerContext) {
		// 0. 上一次任务实际结束执行完的时间
		Date date = triggerContext.lastCompletionTime();
		// 1. 任务已经执行过,有上一次任务执行结束的时间
		if (date != null) {
			// 1.1  拿到上一次预定开始执行的时间
			Date scheduled = triggerContext.lastScheduledExecutionTime();
			// 1.2 纠错
			// 如果上一次任务执行完的时间 竟然比 上一次预定开始执行的时间还要找 --> 需要纠错
			if (scheduled != null && date.before(scheduled)) {
				// 纠正为预定执行时间之前，即允许实际执行之前在预定执行时间之前，但不允许之后
				date = scheduled;
			}
		}
		// 2. 如果任务还没有开始，就以当前时间去计算下一个时间
		else {
			date = new Date();
		}
		return this.sequenceGenerator.next(date);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof CronTrigger &&
				this.sequenceGenerator.equals(((CronTrigger) other).sequenceGenerator)));
	}

	@Override
	public int hashCode() {
		return this.sequenceGenerator.hashCode();
	}

	@Override
	public String toString() {
		return this.sequenceGenerator.toString();
	}

}
