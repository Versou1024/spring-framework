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

package org.springframework.transaction.event;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * {@link EventListenerFactory} implementation that handles {@link TransactionalEventListener}
 * annotated methods.
 *
 * @author Stephane Nicoll
 * @since 4.2
 */
public class TransactionalEventListenerFactory implements EventListenerFactory, Ordered {

	private int order = 50;


	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	@Override
	public boolean supportsMethod(Method method) {
		// 1. 支持方法上有 @TransactionalEventListener 注解,专门为事务而创建的监听器
		return AnnotatedElementUtils.hasAnnotation(method, TransactionalEventListener.class);
	}

	@Override
	public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
		// 2. 将 @TransactionalEventListener 标注的method,转换为 ApplicationListenerMethodTransactionalAdapter 适配器
		return new ApplicationListenerMethodTransactionalAdapter(beanName, type, method);
	}

}
