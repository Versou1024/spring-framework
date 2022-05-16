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
	// 支持Cron表达式

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
		Date date = triggerContext.lastCompletionTime();
		// 这里面有个处理：如果data为null，相当于任务还没有开始执行过
		if (date != null) {
			Date scheduled = triggerContext.lastScheduledExecutionTime();
			// 实际执行时间在预定执行时间之前，
			if (scheduled != null && date.before(scheduled)) {
				// Previous task apparently executed too early...
				// Let's simply use the last calculated execution time then,
				// in order to prevent accidental re-fires in the same second.
				// 纠正为预定执行时间之前，即允许实际执行之前在预定执行时间之前，但不允许之后
				date = scheduled;
			}
		}
		// 如果任务还没有开始，那就以当前时间去计算下一个时间
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
