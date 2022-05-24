/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.net.URL;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.util.Assert;

/**
 * Editor for {@code java.net.URL}, to directly populate a URL property
 * instead of using a String property as bridge.
 *
 * <p>Supports Spring-style URL notation: any fully qualified standard URL
 * ("file:", "http:", etc) and Spring's special "classpath:" pseudo-URL,
 * as well as Spring's context-specific relative file paths.
 *
 * <p>Note: A URL must specify a valid protocol, else it will be rejected
 * upfront. However, the target resource does not necessarily have to exist
 * at the time of URL creation; this depends on the specific resource type.
 *
 * @author Juergen Hoeller
 * @since 15.12.2003
 * @see java.net.URL
 * @see org.springframework.core.io.ResourceEditor
 * @see org.springframework.core.io.ResourceLoader
 * @see FileEditor
 * @see InputStreamEditor
 */
public class URLEditor extends PropertyEditorSupport {
	// PropertyEditor是JavaBean规范定义的接口，这是java.beans中一个接口，其设计的意图是图形化编程上，方便对象与String之间的转换工作，
	// 而spring将其扩展，方便各种对象与String之间的转换工作。

	// Spring所有的扩展都是通过继承PropertyEditorSupport，因为它只聚焦于转换上，所以只需复写setAsText()、getAsText()以及构造方法即可实现扩展。

	private final ResourceEditor resourceEditor;


	/**
	 * Create a new URLEditor, using a default ResourceEditor underneath.
	 */
	public URLEditor() {
		this.resourceEditor = new ResourceEditor();
	}

	/**
	 * Create a new URLEditor, using the given ResourceEditor underneath.
	 * @param resourceEditor the ResourceEditor to use
	 */
	public URLEditor(ResourceEditor resourceEditor) {
		Assert.notNull(resourceEditor, "ResourceEditor must not be null");
		this.resourceEditor = resourceEditor;
	}

	// setAsText(String text) 将String类型的text解析为某种类型的value后
	// 调用 setValue(Object value) 这样在ProperEditor中有这个String text被转换为对应的value值啦

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		// 将String转换为指定的value设置进去

		// 借用ResourceEditor的将String转换为Resource的能力
		this.resourceEditor.setAsText(text);
		Resource resource = (Resource) this.resourceEditor.getValue();
		try {
			setValue(resource != null ? resource.getURL() : null);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Could not retrieve URL for " + resource + ": " + ex.getMessage());
		}
	}

	// getAsText() 是将Object的value值取出来,然后用一种方式将其转换为String类型的text后返回即可
	// ProperEditor中只保存value,不会保留对应String的text值

	@Override
	public String getAsText() {
		// 将value转换为String

		// 1. value强转为URL
		URL value = (URL) getValue();
		// 2. 调用 url.toExternalForm()
		return (value != null ? value.toExternalForm() : "");
	}

}
