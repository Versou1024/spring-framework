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

package org.springframework.validation;

import org.springframework.lang.Nullable;

/**
 * Strategy interface for building message codes from validation error codes.
 * Used by DataBinder to build the codes list for ObjectErrors and FieldErrors.
 *
 * <p>The resulting message codes correspond to the codes of a
 * MessageSourceResolvable (as implemented by ObjectError and FieldError).
 *
 * @author Juergen Hoeller
 * @since 1.0.1
 * @see DataBinder#setMessageCodesResolver
 * @see ObjectError
 * @see FieldError
 * @see org.springframework.context.MessageSourceResolvable#getCodes()
 */
public interface MessageCodesResolver {
	// 从验证错误代码构建消息代码的策略接口。由 DataBinder 用于构建 ObjectErrors 和 FieldErrors 的代码列表。
	// 生成的消息代码对应于 MessageSourceResolvable 的代码（由 ObjectError 和 FieldError 实现）

	// 主要作用就是:当绑定出现问题异常时,可以将errorCode,以及objectName,转换为一段错误消息即ErrorMessage
	// 唯一实现: DefaultMessageCodesResolver

	/**
	 * Build message codes for the given error code and object name.
	 * Used for building the codes list of an ObjectError.
	 * @param errorCode the error code used for rejecting the object
	 * @param objectName the name of the object
	 * @return the message codes to use
	 */
	String[] resolveMessageCodes(String errorCode, String objectName);
	// 为给定的错误代码和对象名称构建消息代码。用于构建 ObjectError 的代码列表。

	/**
	 * Build message codes for the given error code and field specification.
	 * Used for building the codes list of an FieldError.
	 * @param errorCode the error code used for rejecting the value
	 * @param objectName the name of the object
	 * @param field the field name
	 * @param fieldType the field type (may be {@code null} if not determinable)
	 * @return the message codes to use
	 */
	String[] resolveMessageCodes(String errorCode, String objectName, String field, @Nullable Class<?> fieldType);
	// 为给定的错误代码和字段规范构建消息代码。用于构建 FieldError 的代码列表

}
