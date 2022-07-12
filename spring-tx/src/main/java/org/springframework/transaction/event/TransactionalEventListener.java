/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.transaction.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AliasFor;

/**
 * An {@link EventListener} that is invoked according to a {@link TransactionPhase}.
 *
 * <p>If the event is not published within an active transaction, the event is discarded
 * unless the {@link #fallbackExecution} flag is explicitly set. If a transaction is
 * running, the event is handled according to its {@code TransactionPhase}.
 *
 * <p>Adding {@link org.springframework.core.annotation.Order @Order} to your annotated
 * method allows you to prioritize that listener amongst other listeners running before
 * or after transaction completion.
 *
 * <p><b>NOTE: Transactional event listeners only work with thread-bound transactions
 * managed by a {@link org.springframework.transaction.PlatformTransactionManager
 * PlatformTransactionManager}.</b> A reactive transaction managed by a
 * {@link org.springframework.transaction.ReactiveTransactionManager ReactiveTransactionManager}
 * uses the Reactor context instead of thread-local variables, so from the perspective of
 * an event listener, there is no compatible active transaction that it can participate in.
 *
 * <p><strong>WARNING:</strong> if the {@code TransactionPhase} is set to
 * {@link TransactionPhase#AFTER_COMMIT AFTER_COMMIT} (the default),
 * {@link TransactionPhase#AFTER_ROLLBACK AFTER_ROLLBACK}, or
 * {@link TransactionPhase#AFTER_COMPLETION AFTER_COMPLETION}, the transaction will
 * have been committed or rolled back already, but the transactional resources might
 * still be active and accessible. As a consequence, any data access code triggered
 * at this point will still "participate" in the original transaction, but changes
 * will not be committed to the transactional resource. See
 * {@link org.springframework.transaction.support.TransactionSynchronization#afterCompletion(int)
 * TransactionSynchronization.afterCompletion(int)} for details.
 *
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 4.2
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EventListener //有类似于注解继承的效果
public @interface TransactionalEventListener {
	// @since 4.2  显然，注解的方式提供得还是挺晚的，而API的方式第一个版本就已经提供了
	// 另外最重要的是，它头上有一个注解：`@EventListener`  so
	
	// 从源码里可以看出，其实@TransactionalEventListener的底层实现原理还是事务同步器：TransactionSynchronization和TransactionSynchronizationManager。
	// 以上，建立在小伙伴已经知晓了Spring事件/监听机制的基础上，回头看Spring事务的监听机制其实就非常非常的简单了(没有多少新东西)。

	/**
	 * Phase to bind the handling of an event to.
	 * <p>The default phase is {@link TransactionPhase#AFTER_COMMIT}.
	 * <p>If no transaction is in progress, the event is not processed at
	 * all unless {@link #fallbackExecution} has been enabled explicitly.
	 */
	TransactionPhase phase() default TransactionPhase.AFTER_COMMIT;
	// 这个注解取值有：BEFORE_COMMIT、AFTER_COMMIT、AFTER_ROLLBACK、AFTER_COMPLETION
	// 各个值都代表什么意思表达什么功能，非常清晰~
	// 需要注意的是：AFTER_COMMIT + AFTER_COMPLETION是可以同时生效的
	// AFTER_ROLLBACK + AFTER_COMPLETION是可以同时生效的

	/**
	 * Whether the event should be handled if no transaction is running.
	 */
	boolean fallbackExecution() default false;
	// 若没有事务的时候，对应的event是否已经执行  
	// 默认值为false表示  没事务就不执行了

	/**
	 * Alias for {@link #classes}.
	 */
	@AliasFor(annotation = EventListener.class, attribute = "classes")
	Class<?>[] value() default {};
	// 这里巧妙的用到了@AliasFor的能力，放到了@EventListener身上
	// 注意：一般我建议都需要指定此值，否则默认可以处理所有类型的事件  范围太广了

	/**
	 * The event classes that this listener handles.
	 * <p>If this attribute is specified with a single value, the annotated
	 * method may optionally accept a single parameter. However, if this
	 * attribute is specified with multiple values, the annotated method
	 * must <em>not</em> declare any parameters.
	 */
	@AliasFor(annotation = EventListener.class, attribute = "classes")
	Class<?>[] classes() default {};

	/**
	 * Spring Expression Language (SpEL) attribute used for making the event
	 * handling conditional.
	 * <p>The default is {@code ""}, meaning the event is always handled.
	 * @see EventListener#condition
	 */
	String condition() default "";

}
