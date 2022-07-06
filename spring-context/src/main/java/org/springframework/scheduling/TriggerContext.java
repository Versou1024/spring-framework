/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.scheduling;

import java.util.Date;

import org.springframework.lang.Nullable;

/**
 * Context object encapsulating last execution times and last completion time
 * of a given task.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public interface TriggerContext {
	// TriggerContext = Trigger Context

	/**
	 * Return the last <i>scheduled</i> execution time of the task,
	 * or {@code null} if not scheduled before.
	 */
	@Nullable
	Date lastScheduledExecutionTime();
	// 上次预定执行时间
	// fixedRate是两次任务开始时间的间隔 -- 因此取这里的预定开始执行时间

	/**
	 * Return the last <i>actual</i> execution time of the task,
	 * or {@code null} if not scheduled before.
	 */
	@Nullable
	Date lastActualExecutionTime(); // 上次实际执行时间

	/**
	 * Return the last completion time of the task,
	 * or {@code null} if not scheduled before.
	 */
	@Nullable
	Date lastCompletionTime(); 
	// 上次执行结束时间
	// fixedRate是上一次任务结束时间到下一次任务的开始时间的间隔 -- 因此取实际执行结束的时间

}
