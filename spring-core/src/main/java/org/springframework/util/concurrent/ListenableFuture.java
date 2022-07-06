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

/**
 * Extend {@link Future} with the capability to accept completion callbacks.
 * If the future has completed when the callback is added, the callback is
 * triggered immediately.
 *
 * <p>Inspired by {@code com.google.common.util.concurrent.ListenableFuture}.
 *
 * @author Arjen Poutsma
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @since 4.0
 * @param <T> the result type returned by this Future's {@code get} method
 */
public interface ListenableFuture<T> extends Future<T> {
	// 位于: org.springframework.util.concurrent
	
	// ListenableFuture 是继承的Future
	// ListenableFuture = Listenable Future
	// 表示有注册回调并触发回调的嗯嗯呢

	/**
	 * Register the given {@code ListenableFutureCallback}.
	 * @param callback the callback to register
	 */
	void addCallback(ListenableFutureCallback<? super T> callback);
	// 注册给定的ListenableFutureCallback 。

	/**
	 * Java 8 lambda-friendly alternative with success and failure callbacks.
	 * @param successCallback the success callback
	 * @param failureCallback the failure callback
	 * @since 4.1
	 */
	void addCallback(SuccessCallback<? super T> successCallback, FailureCallback failureCallback);
	// Java 8 lambda 友好的替代方案，带有成功和失败回调。
	// SuccessCallback 成功回调
	// FailureCallback 失败回调


	/**
	 * Expose this {@link ListenableFuture} as a JDK {@link CompletableFuture}.
	 * @since 5.0
	 */
	default CompletableFuture<T> completable() {
		// 将此ListenableFuture公开为 JDK CompletableFuture 。 
		// 起到一个适配作者用
		
		CompletableFuture<T> completable = new DelegatingCompletableFuture<>(this);
		addCallback(completable::complete, completable::completeExceptionally);
		return completable;
	}

}
