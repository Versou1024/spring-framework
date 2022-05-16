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

package org.springframework.transaction.event;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link GenericApplicationListener} adapter that delegates the processing of
 * an event to a {@link TransactionalEventListener} annotated method. Supports
 * the exact same features as any regular {@link EventListener} annotated method
 * but is aware of the transactional context of the event publisher.
 *
 * <p>Processing of {@link TransactionalEventListener} is enabled automatically
 * when Spring's transaction management is enabled. For other cases, registering
 * a bean of type {@link TransactionalEventListenerFactory} is required.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 * @see ApplicationListenerMethodAdapter
 * @see TransactionalEventListener
 */
class ApplicationListenerMethodTransactionalAdapter extends ApplicationListenerMethodAdapter {

	private final TransactionalEventListener annotation;


	public ApplicationListenerMethodTransactionalAdapter(String beanName, Class<?> targetClass, Method method) {
		super(beanName, targetClass, method);
		// 1. 找出 TransactionalEventListener 注解
		TransactionalEventListener ann = AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);
		if (ann == null) {
			throw new IllegalStateException("No TransactionalEventListener annotation found on method: " + method);
		}
		this.annotation = ann;
	}


	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 事件处理

		// 1.1. 当前线程的事务同步处于活动状态,且返回当前是否有实际的事务活动。这表明当前线程是否与实际事务相关联，而不仅仅是与活动事务同步相关联
		// 就根据这个event\listener\监听阶段来创建一个事务同步器适配者,并将这个事务同步器注册到事务管理器中,和当前线程做一个绑定
		if (TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.isActualTransactionActive()) {
			// 一般我们认为都是进入这个代码块中!!
			TransactionSynchronization transactionSynchronization = createTransactionSynchronization(event);
			TransactionSynchronizationManager.registerSynchronization(transactionSynchronization);
		}
		// 1.2. 执行到这:说明没有事务这个方法上执行,注解中annotation.fallbackExecution()若表名如果没有事务正在运行，也需要处理这个事件时
		// 就直接使用这个监听器适配者this的processEvent处理事件吧
		else if (this.annotation.fallbackExecution()) {
			if (this.annotation.phase() == TransactionPhase.AFTER_ROLLBACK && logger.isWarnEnabled()) {
				logger.warn("Processing " + event + " as a fallback execution on AFTER_ROLLBACK phase");
			}
			// 1.2.1 直接处理事件,不和事务的阶段有关系,即annotation.phase()没屁用
			processEvent(event);
		}
		// 1.3 当前线程的没有事务开启,或者事务已经被关闭,这个@TransactionalEventListener注解同时表名没有事务时不响应监听到的event时
		// 做一个日志记录,就直接返回跳过
		else {
			// No transactional event execution at all
			if (logger.isDebugEnabled()) {
				logger.debug("No transaction is active - skipping " + event);
			}
		}
	}

	private TransactionSynchronization createTransactionSynchronization(ApplicationEvent event) {
		// 会创建一个事务同步器的适配器,获取注解中的phase阶段为止
		return new TransactionSynchronizationEventAdapter(this, event, this.annotation.phase());
	}


	private static class TransactionSynchronizationEventAdapter extends TransactionSynchronizationAdapter {

		private final ApplicationListenerMethodAdapter listener; // 监听器适配者

		private final ApplicationEvent event; // 事务监听的事件

		private final TransactionPhase phase; // 事务同步阶段

		public TransactionSynchronizationEventAdapter(ApplicationListenerMethodAdapter listener,
				ApplicationEvent event, TransactionPhase phase) {

			this.listener = listener;
			this.event = event;
			this.phase = phase;
		}

		@Override
		public int getOrder() {
			return this.listener.getOrder();
		}

		@Override
		public void beforeCommit(boolean readOnly) {
			// 判断阶段是否正确,如果正确,就调用 listener.processEvent() 方法
			if (this.phase == TransactionPhase.BEFORE_COMMIT) {
				// 是否处理这个Event,还取决于这里的ProcessEvent()方法
				processEvent();
			}
		}

		@Override
		public void afterCompletion(int status) {
			// 同样的也是需要判断阶段数的
			if (this.phase == TransactionPhase.AFTER_COMMIT && status == STATUS_COMMITTED) {
				processEvent();
			}
			else if (this.phase == TransactionPhase.AFTER_ROLLBACK && status == STATUS_ROLLED_BACK) {
				processEvent();
			}
			else if (this.phase == TransactionPhase.AFTER_COMPLETION) {
				processEvent();
			}
		}

		protected void processEvent() {
			this.listener.processEvent(this.event);
		}
	}

}
