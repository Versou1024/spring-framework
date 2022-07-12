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

package org.springframework.transaction.annotation;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Strategy implementation for parsing Spring's {@link Transactional} annotation.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
@SuppressWarnings("serial")
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {
	// 解析 Spring 的Transactional注解的策略实现。

	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		// 当前解析器是否启用，就必须满足有@Transactional注解
		return AnnotationUtils.isCandidateClass(targetClass, Transactional.class);
	}

	@Override
	@Nullable
	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
		// 查找Transactional注解属性
		AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(element, Transactional.class, false, false);
		if (attributes != null) {
			// 开始解析
			// 此处注意，把这个注解的属性交给它，最终转换为事务的属性类~~~~
			return parseTransactionAnnotation(attributes); // 核心
		}
		// 注解都木有，那就返回null
		else {
			return null;
		}
	}

	public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
		// 顺便提供的一个重载方法，可以让你直接传入一个注解
		return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
	}


	// 这个简单的说：就是把注解的属性们 专门为事务属性们~~~~
	// 专门使用的: RuleBasedTransactionAttribute
	protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {

		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();

		// 事务的传播属性枚举：内部定义了7种事务传播行为~~~~~
		Propagation propagation = attributes.getEnum("propagation");
		rbta.setPropagationBehavior(propagation.value());
		Isolation isolation = attributes.getEnum("isolation");
		// 事务的隔离级别
		rbta.setIsolationLevel(isolation.value());
		// 事务超时时间
		rbta.setTimeout(attributes.getNumber("timeout").intValue());
		// 事务是否只读
		rbta.setReadOnly(attributes.getBoolean("readOnly"));
		// 这个属性，是指定事务管理器PlatformTransactionManager的BeanName，若不指定，那就按照类型找了
		// 若容器中存在多个事务管理器，但又没指定名字  那就报错啦~~~
		rbta.setQualifier(attributes.getString("value"));

		// rollbackFor可以指定需要回滚的异常，可议指定多个  若不指定默认为RuntimeException
		// 此处使用的RollbackRuleAttribute包装~~~~  它就是个POJO没有实现其余接口
		List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
		for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		// 指定不需要回滚的异常类型们~~~
		// 此处使用的NoRollbackRuleAttribute包装  它是RollbackRuleAttribute的子类
		for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		// 设置回滚规则rules
		// 最后别忘了set进去
		rbta.setRollbackRules(rollbackRules);

		return rbta;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || other instanceof SpringTransactionAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringTransactionAnnotationParser.class.hashCode();
	}

}
