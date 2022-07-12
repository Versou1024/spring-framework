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

package org.springframework.transaction;

/**
 * Common representation of the current state of a transaction.
 * Serves as base interface for {@link TransactionStatus} as well as
 * {@link ReactiveTransaction}.
 *
 * @author Juergen Hoeller
 * @since 5.2
 */
public interface TransactionExecution {
	// 命名: 
	// Transaction Execution  事务当前执行状态的通用表示

	/**
	 * Return whether the present transaction is new; otherwise participating
	 * in an existing transaction, or potentially not running in an actual
	 * transaction in the first place.
	 */
	boolean isNewTransaction(); 
	// 返回当前事务是否是新的；以其他方式参与现有事务，或者可能一开始就没有在实际事务中运行。

	/**
	 * Set the transaction rollback-only. This instructs the transaction manager
	 * that the only possible outcome of the transaction may be a rollback, as
	 * alternative to throwing an exception which would in turn trigger a rollback.
	 */
	void setRollbackOnly();
	// 仅设置事务回滚,这表示事务的唯一结果是进行回滚。因此如果你在外层给try catch 住不让事务回滚，就会抛出你可能常见的异常：

	/**
	 * Return whether the transaction has been marked as rollback-only
	 * (either by the application or by the transaction infrastructure).
	 */
	boolean isRollbackOnly(); 
	// 返回事务是否已被标记为仅回滚（由应用程序或事务基础结构）

	/**
	 * Return whether this transaction is completed, that is,
	 * whether it has already been committed or rolled back.
	 */
	boolean isCompleted(); 
	// 返回此事务是否完成，即是否已经提交或回滚

}
