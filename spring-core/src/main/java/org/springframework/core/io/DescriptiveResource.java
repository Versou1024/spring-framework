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

package org.springframework.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.lang.Nullable;

/**
 * Simple {@link Resource} implementation that holds a resource description
 * but does not point to an actually readable resource.
 *
 * <p>To be used as placeholder if a {@code Resource} argument is
 * expected by an API but not necessarily used for actual reading.
 *
 * @author Juergen Hoeller
 * @since 1.2.6
 */
public class DescriptiveResource extends AbstractResource {
	/**
	 * 包含资源描述但不指向实际可读资源的简单Resource实现。
	 * 如果 API 需要Resource参数但不一定用于实际读取，则用作占位符。
	 *
	 * 这个类实际只是对资源描述的定义，既不是可读，实际文件也是不存在的，其他Resource接口中的方法也并未实现。
	 * 当一个方法需要你传递一个资源对象，但又不会在方法中真正读取该对象的时候，
	 * 如果没有合适的资源对象作为参数，就创建一个 DescriptiveResource 资源做参数。比如ConfigurationClass就有使用~~~
	 */

	private final String description;


	/**
	 * Create a new DescriptiveResource.
	 * @param description the resource description
	 */
	public DescriptiveResource(@Nullable String description) {
		this.description = (description != null ? description : "");
	}


	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public boolean isReadable() {
		return false;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		throw new FileNotFoundException(
				getDescription() + " cannot be opened because it does not point to a readable resource");
	}

	@Override
	public String getDescription() {
		return this.description;
	}


	/**
	 * This implementation compares the underlying description String.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof DescriptiveResource &&
				((DescriptiveResource) other).description.equals(this.description)));
	}

	/**
	 * This implementation returns the hash code of the underlying description String.
	 */
	@Override
	public int hashCode() {
		return this.description.hashCode();
	}

}
