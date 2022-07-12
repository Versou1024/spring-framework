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

package org.springframework.transaction.interceptor;

import java.io.Serializable;

import org.springframework.lang.Nullable;
import org.springframework.transaction.support.DelegatingTransactionDefinition;

/**
 * {@link TransactionAttribute} implementation that delegates all calls to a given target
 * {@link TransactionAttribute} instance. Abstract because it is meant to be subclassed,
 * with subclasses overriding specific methods that are not supposed to simply delegate
 * to the target instance.
 *
 * @author Juergen Hoeller
 * @since 1.2
 */
@SuppressWarnings("serial")
public abstract class DelegatingTransactionAttribute extends DelegatingTransactionDefinition
		implements TransactionAttribute, Serializable {
	// 命名: 
	// Delegating TransactionAttribute = 用来做委托的TransactionAttribute
	
	// 含义:
	// ❗️❗️❗️ 设计模式为委托模式
	// 当已经有一个TransactionAttribute目标对象时,是如果即想要保留其中的属性,有想要对某个方法进行重写,就会陷入无可奈何的地步
	// 但是通过抽象类DelegatingTransactionAttribute就可以很好的解决这个问题哦
	// 那就是DelegatingTransactionAttribute将TransactionAttribute和TransactionDefinition的所有方法都默认交给传递的targetAttribute实现
	// 用户比如说希望对getName()方法进行重写,那么只需要
	//  TransactionAttribute ta = new DelegatingTransactionAttribute(txAttr) {
	//				@Override
	//				public String getName() {
	//					return "xx";
	//				}
	//			};

	private final TransactionAttribute targetAttribute;


	/**
	 * Create a DelegatingTransactionAttribute for the given target attribute.
	 * @param targetAttribute the target TransactionAttribute to delegate to
	 */
	public DelegatingTransactionAttribute(TransactionAttribute targetAttribute) {
		super(targetAttribute);
		this.targetAttribute = targetAttribute;
	}


	@Override
	@Nullable
	public String getQualifier() {
		return this.targetAttribute.getQualifier();
	}

	@Override
	public boolean rollbackOn(Throwable ex) {
		return this.targetAttribute.rollbackOn(ex);
	}

}
