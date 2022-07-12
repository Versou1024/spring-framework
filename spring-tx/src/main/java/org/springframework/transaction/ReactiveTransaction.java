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
 * Representation of an ongoing reactive transaction.
 * This is currently a marker interface extending {@link TransactionExecution}
 * but may acquire further methods in a future revision.
 *
 * <p>Transactional code can use this to retrieve status information,
 * and to programmatically request a rollback (instead of throwing
 * an exception that causes an implicit rollback).
 *
 * @author Mark Paluch
 * @author Juergen Hoeller
 * @since 5.2
 * @see #setRollbackOnly()
 * @see ReactiveTransactionManager#getReactiveTransaction
 */
public interface ReactiveTransaction extends TransactionExecution {
	// 命名:
	// 象征正在进行的reactive事务
	// 目前这是一个扩展TransactionExecution的标记接口，但可能会在未来的版本中获得更多方法。

}
