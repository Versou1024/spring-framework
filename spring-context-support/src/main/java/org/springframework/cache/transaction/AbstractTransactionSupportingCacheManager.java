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

package org.springframework.cache.transaction;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;

/**
 * Base class for CacheManager implementations that want to support built-in
 * awareness of Spring-managed transactions. This usually needs to be switched
 * on explicitly through the {@link #setTransactionAware} bean property.
 *
 * @author Juergen Hoeller
 * @since 3.2
 * @see #setTransactionAware
 * @see TransactionAwareCacheDecorator
 * @see TransactionAwareCacheManagerProxy
 */
public abstract class AbstractTransactionSupportingCacheManager extends AbstractCacheManager {
	// 支持 Spring 管理事务的内置感知的 CacheManager 实现的基类

	private boolean transactionAware = false;


	/**
	 * Set whether this CacheManager should expose transaction-aware Cache objects.
	 * <p>Default is "false". Set this to "true" to synchronize cache put/evict
	 * operations with ongoing Spring-managed transactions, performing the actual cache
	 * put/evict operation only in the after-commit phase of a successful transaction.
	 */
	public void setTransactionAware(boolean transactionAware) {
		// 设置此 CacheManager 是否应公开事务感知缓存对象
		// 默认为“假”。将此设置为“true”以将CachePut/CacheEvict操作与正在进行的 Spring 管理的事务同步，仅在成功事务的提交后阶段执行实际的缓存放置/逐出操作
		this.transactionAware = transactionAware;
	}

	/**
	 * Return whether this CacheManager has been configured to be transaction-aware.
	 */
	public boolean isTransactionAware() {
		return this.transactionAware;
	}


	@Override
	protected Cache decorateCache(Cache cache) {
		// 对cache进行包装,如果需要在Spring事务成功提交之后执行的话
		return (isTransactionAware() ? new TransactionAwareCacheDecorator(cache) : cache);
	}

}
