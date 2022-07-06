/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import java.io.Serializable;

import org.springframework.aop.aspectj.SingletonAspectInstanceFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

/**
 * Implementation of {@link MetadataAwareAspectInstanceFactory} that is backed
 * by a specified singleton object, returning the same instance for every
 * {@link #getAspectInstance()} call.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see SimpleMetadataAwareAspectInstanceFactory
 */
@SuppressWarnings("serial")
public class SingletonMetadataAwareAspectInstanceFactory extends SingletonAspectInstanceFactory implements MetadataAwareAspectInstanceFactory, Serializable {
	// MetadataAwareAspectInstanceFactory extends AspectInstanceFactory -> 扩展了一个获取AspectMetadata的方法
	// SingletonAspectInstanceFactory implements AspectInstanceFactory -> 通过传入一个单例的Aspect对象后,确定了如何获取一个AspectJ的Obj实例对象 
	// SingletonMetadataAwareAspectInstanceFactory extends SingletonAspectInstanceFactory implements MetadataAwareAspectInstanceFactory 
	// 从继承关系上可以直到,说明其创建Aspect实例的任务是交给当前类的超类SingletonAspectInstanceFactory完成的
	// 所以SingletonMetadataAwareAspectInstanceFactory的关注点就是MetadataAwareAspectInstanceFactory扩展的获取AspectMetadata的方法

	private final AspectMetadata metadata;


	/**
	 * Create a new SingletonMetadataAwareAspectInstanceFactory for the given aspect.
	 * @param aspectInstance the singleton aspect instance
	 * @param aspectName the name of the aspect
	 */
	public SingletonMetadataAwareAspectInstanceFactory(Object aspectInstance, String aspectName) {
		super(aspectInstance);
		this.metadata = new AspectMetadata(aspectInstance.getClass(), aspectName);
	}


	@Override
	public final AspectMetadata getAspectMetadata() {
		return this.metadata;
	}

	@Override
	public Object getAspectCreationMutex() {
		return this;
	}

	@Override
	protected int getOrderForAspectClass(Class<?> aspectClass) {
		return OrderUtils.getOrder(aspectClass, Ordered.LOWEST_PRECEDENCE);
	}

}
