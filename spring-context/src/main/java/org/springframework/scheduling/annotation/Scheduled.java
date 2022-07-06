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

package org.springframework.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Annotation that marks a method to be scheduled. Exactly one of the
 * {@link #cron}, {@link #fixedDelay}, or {@link #fixedRate} attributes must be
 * specified.
 *
 * <p>The annotated method must expect no arguments. It will typically have
 * a {@code void} return type; if not, the returned value will be ignored
 * when called through the scheduler.
 *
 * <p>Processing of {@code @Scheduled} annotations is performed by
 * registering a {@link ScheduledAnnotationBeanPostProcessor}. This can be
 * done manually or, more conveniently, through the {@code <task:annotation-driven/>}
 * element or @{@link EnableScheduling} annotation.
 *
 * <p>This annotation may be used as a <em>meta-annotation</em> to create custom
 * <em>composed annotations</em> with attribute overrides.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Dave Syer
 * @author Chris Beams
 * @since 3.0
 * @see EnableScheduling
 * @see ScheduledAnnotationBeanPostProcessor
 * @see Schedules
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules.class)
public @interface Scheduled {

	/**
	 * A special cron expression value that indicates a disabled trigger: {@value}.
	 * <p>This is primarily meant for use with <code>${...}</code> placeholders,
	 * allowing for external disabling of corresponding scheduled methods.
	 * @since 5.1
	 * @see ScheduledTaskRegistrar#CRON_DISABLED
	 */
	String CRON_DISABLED = ScheduledTaskRegistrar.CRON_DISABLED;


	/**
	 * A cron-like expression, extending the usual UN*X definition to include triggers
	 * on the second, minute, hour, day of month, month, and day of week.
	 * <p>For example, {@code "0 * * * * MON-FRI"} means once per minute on weekdays
	 * (at the top of the minute - the 0th second).
	 * <p>The fields read from left to right are interpreted as follows.
	 * <ul>
	 * <li>second</li>
	 * <li>minute</li>
	 * <li>hour</li>
	 * <li>day of month</li>
	 * <li>month</li>
	 * <li>day of week</li>
	 * </ul>
	 * <p>The special value {@link #CRON_DISABLED "-"} indicates a disabled cron
	 * trigger, primarily meant for externally specified values resolved by a
	 * <code>${...}</code> placeholder.
	 * @return an expression that can be parsed to a cron schedule
	 * @see org.springframework.scheduling.support.CronSequenceGenerator
	 */
	String cron() default "";
	// 类似 cron 的表达式，扩展了通常的 UN*X 定义，以包括秒、分钟、小时、月中的某天、月和周中的某天的触发器。
	// 例如， "0 * * * * MON-FRI"表示工作日每分钟一次（在分钟的顶部 - 第 0 秒）。

	/**
	 * A time zone for which the cron expression will be resolved. By default, this
	 * attribute is the empty String (i.e. the server's local time zone will be used).
	 * @return a zone id accepted by {@link java.util.TimeZone#getTimeZone(String)},
	 * or an empty String to indicate the server's default time zone
	 * @since 4.0
	 * @see org.springframework.scheduling.support.CronTrigger#CronTrigger(String, java.util.TimeZone)
	 * @see java.util.TimeZone
	 */
	String zone() default "";
	// 自动时区
	
	// ❗️❗️❗️
	// 注意:
	// a:同时使用 fixedDelay 和 fixedDelayString 将会创建两个 FixedDelayTask
	// b:同时使用 fixedRate 和 fixedRateString 将会创建两个 FixedDelayTask

	/**
	 * Execute the annotated method with a fixed period in milliseconds between the
	 * end of the last invocation and the start of the next.
	 * @return the delay in milliseconds
	 */
	long fixedDelay() default -1;
	// 在最后一次调用结束和下一次调用开始之间以毫秒为单位执行的方法 -- 接受long

	/**
	 * Execute the annotated method with a fixed period in milliseconds between the
	 * end of the last invocation and the start of the next.
	 * @return the delay in milliseconds as a String value, e.g. a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 */
	String fixedDelayString() default "";
	// 在最后一次调用结束和下一次调用开始之间以毫秒为单位执行的方法 -- 接受String
	
	/**
	 * Execute the annotated method with a fixed period in milliseconds between
	 * invocations.
	 * @return the period in milliseconds
	 */
	long fixedRate() default -1;

	/**
	 * Execute the annotated method with a fixed period in milliseconds between
	 * invocations.
	 * @return the period in milliseconds as a String value, e.g. a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 */
	String fixedRateString() default "";
	// 在调用开始之间以毫秒为单位执行的方法 -- 接受String
	
	// note: initialDelay/initialDelayString 仅仅对Cron属性对应的CronTask有影响哦

	// 注意后面的: initialDelayString 比 initialDelay 属性的优先级高 -> 前者会覆盖后者
	// 而且 xxString 是支持使用 #{} 占位符的
	// 比如
	// @Scheduled(initialDelay=100,initialDelayString=${fixed.delay})
	// 然后再application.yaml
	// fixed:
	// 	 delay: PT15M
	// 那么最终生效的就是 15min 分钟
	// 当使用xxString时,如果字符串开头是p或者P,就会通过Duration.parse(value).toMillis()来进行解析
	// 使用规则:

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



	/**
	 * Number of milliseconds to delay before the first execution of a
	 * {@link #fixedRate} or {@link #fixedDelay} task.
	 * @return the initial delay in milliseconds
	 * @since 3.2
	 */
	long initialDelay() default -1;
	// 在第一次执行fixedRate或fixedDelay任务之前要延迟的毫秒数 -- long


	/**
	 * Number of milliseconds to delay before the first execution of a
	 * {@link #fixedRate} or {@link #fixedDelay} task.
	 * @return the initial delay in milliseconds as a String value, e.g. a placeholder
	 * or a {@link java.time.Duration#parse java.time.Duration} compliant value
	 * @since 3.2.2
	 */
	String initialDelayString() default "";
	// 在第一次执行fixedRate或fixedDelay任务之前要延迟的毫秒数 -- String

}
