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

package org.springframework.aop.target;

/**
 * Simple {@link org.springframework.aop.TargetSource} implementation,
 * freshly obtaining the specified target bean from its containing
 * Spring {@link org.springframework.beans.factory.BeanFactory}.
 *
 * <p>Can obtain any kind of target bean: singleton, scoped, or prototype.
 * Typically used for scoped beans.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class SimpleBeanTargetSource extends AbstractBeanFactoryBasedTargetSource {
	// 简单基于BeanFactory完成getTarget的操作

	@Override
	public Object getTarget() throws Exception {
		// 因此如果是单例就总是同一个对象
		// 如果是原型，那么getTarget就是一个新的原型对象
		return getBeanFactory().getBean(getTargetBeanName());
	}

}
