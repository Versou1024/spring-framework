/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.expression;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * EL property accessor that knows how to traverse the beans of a
 * Spring {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author Juergen Hoeller
 * @author Andy Clement
 * @since 3.0
 */
public class BeanFactoryAccessor implements PropertyAccessor {

	@Override
	public Class<?>[] getSpecificTargetClasses() {
		// 用于访问 target 是 BeanFactory 的
		// 也就是 read() 和 write() 方法中 target 对象应该是 BeanFactory 哦
		return new Class<?>[] {BeanFactory.class};
	}

	@Override
	public boolean canRead(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
		// 读的前提
		// target是BeanFactory的,且target中包含这个BeanName
		return (target instanceof BeanFactory && ((BeanFactory) target).containsBean(name));
	}

	@Override
	public TypedValue read(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
		// 1. 首先 target 必须是 BeanFactory
		Assert.state(target instanceof BeanFactory, "Target must be of type BeanFactory");
		// 2. 从 BeanFactory 中根据属性name去查找Bean
		return new TypedValue(((BeanFactory) target).getBean(name));
	}

	@Override
	public boolean canWrite(EvaluationContext context, @Nullable Object target, String name) throws AccessException {
		// 不能向 BeanFactory 中写
		return false;
	}

	@Override
	public void write(EvaluationContext context, @Nullable Object target, String name, @Nullable Object newValue)
			throws AccessException {

		throw new AccessException("Beans in a BeanFactory are read-only");
	}

}
