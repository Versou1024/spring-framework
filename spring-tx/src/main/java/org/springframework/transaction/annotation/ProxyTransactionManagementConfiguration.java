/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.transaction.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans
 * necessary to enable proxy-based annotation-driven transaction management.
 *
 * @author Chris Beams
 * @author Sebastien Deleuze
 * @since 3.1
 * @see EnableTransactionManagement
 * @see TransactionManagementConfigurationSelector
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {
	// ❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️
	// 经典的导入结构 -> 可以去看看@EnableCaching的那段导入的注释,已经说的非常的相似 [我可以相似度达到了99%]
	
	// ~~ 经典的结构
	// 导入过程: @EnableTransactionManagement -> 元注解@Import(TransactionManagementConfigurationSelector.class) -> ProxyTransactionManagementConfiguration导入
	// 继承体系:
	// ProxyTransactionManagementConfiguration 本身就是一个配置类,最终要的是向容器注册一个Advisor即BeanFactoryTransactionAttributeSourceAdvisor方便

	@Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(TransactionAttributeSource transactionAttributeSource, TransactionInterceptor transactionInterceptor) {
		// 切面通知对象 BeanFactoryTransactionAttributeSourceAdvisor

		BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
		advisor.setTransactionAttributeSource(transactionAttributeSource);
		advisor.setAdvice(transactionInterceptor); // 设置 通知方法/拦截器/回调方法
		if (this.enableTx != null) {
			// @EnableTransaction的order属性可以指定Advisor在整个List<Advisor>的执行优先级哦
			advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
		}
		return advisor;
	}


	// TransactionAttributeSource 这种类特别像 `CacheOperationSource`这种类的设计模式
	// 这里直接使用的是 AnnotationTransactionAttributeSource  基于注解的事务属性源~~~
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public TransactionAttributeSource transactionAttributeSource() {
		// TransactionAttributeSource 事务属性源 -> 就是用来查找事务属性的即能够查找 TransactionAttribute
		return new AnnotationTransactionAttributeSource();
	}

	// 事务拦截器，它是个`MethodInterceptor`，它也是Spring处理事务最为核心的部分
	// 请注意：你可以自己定义一个TransactionInterceptor（同名的），来覆盖此Bean（注意是覆盖）
	// 另外请注意：你自定义的BeanName必须同名，也就是必须名为：transactionInterceptor  否则两个都会注册进容器里面去~~~~~~
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public TransactionInterceptor transactionInterceptor(TransactionAttributeSource transactionAttributeSource) {
		// 获取事务拦截器，设置事务管理器、事务属性源 -- 核心拦截器

		TransactionInterceptor interceptor = new TransactionInterceptor();
		// 注意这里传入了事务属性源：用来从属性源中获取真实的事务属性
		interceptor.setTransactionAttributeSource(transactionAttributeSource);
		if (this.txManager != null) {
			interceptor.setTransactionManager(this.txManager);
		}
		return interceptor;
	}

}
