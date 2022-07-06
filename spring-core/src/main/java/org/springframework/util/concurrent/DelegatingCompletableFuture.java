/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.springframework.util.Assert;

/**
 * Extension of {@link CompletableFuture} which allows for cancelling
 * a delegate along with the {@link CompletableFuture} itself.
 *
 * @author Juergen Hoeller
 * @since 5.0
 * @param <T> the result type returned by this Future's {@code get} method
 */
class DelegatingCompletableFuture<T> extends CompletableFuture<T> {
	// 适配器模式 -> 目标模式为 JDK的CompletableFuture

	// 被适配的对象是 Future的子接口 ListenableFuture[拥有在Future执行完成(成功或失败后的)回调能力]
	private final Future<T> delegate;


	public DelegatingCompletableFuture(Future<T> delegate) {
		Assert.notNull(delegate, "Delegate must not be null");
		this.delegate = delegate;
	}


	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		// mayInterruptIfRunning: 运行时是否可以中断
		
		boolean result = this.delegate.cancel(mayInterruptIfRunning);
		super.cancel(mayInterruptIfRunning);
		return result;
	}

}
