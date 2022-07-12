/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.transaction.support;

/**
 * Generic interface to be implemented by resource holders.
 * Allows Spring's transaction infrastructure to introspect
 * and reset the holder when necessary.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 * @see ResourceHolderSupport
 * @see ResourceHolderSynchronization
 */
public interface ResourceHolder {
	// 位于: org.springframework.transaction.support -> 工具包
	
	// 定义:
	// reset() - 重置资源持有者的状态
	// unbound() - 解除和事务的绑定关系
	// isVoid() - 事务是否视为无效
	
	
	// 实现类:
	//	ResourceHolderSupport
	//		ConnectionHolder -- 位于 org.springframework.jdbc.datasource -> 在spring-jdbc项目下
	//		JmsResourceHolder -- 位于 org.springframework.jms.connection -> 在spring-jms项目下
	//		EntityManagerHolder	-- 位于 org.springframework.orm.jpa 

	/**
	 * Reset the transactional state of this holder.
	 */
	void reset();

	/**
	 * Notify this holder that it has been unbound from transaction synchronization.
	 */
	void unbound();

	/**
	 * Determine whether this holder is considered as 'void',
	 * i.e. as a leftover from a previous thread.
	 */
	boolean isVoid();

}
