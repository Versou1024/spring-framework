/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.beans;

import java.beans.PropertyEditorSupport;
import java.util.Properties;

import org.springframework.beans.propertyeditors.PropertiesEditor;

/**
 * {@link java.beans.PropertyEditor Editor} for a {@link PropertyValues} object.
 *
 * <p>The required format is defined in the {@link java.util.Properties}
 * documentation. Each property must be on a new line.
 *
 * <p>The present implementation relies on a
 * {@link org.springframework.beans.propertyeditors.PropertiesEditor}
 * underneath.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public class PropertyValuesEditor extends PropertyEditorSupport {
	// PropertyEditor是JavaBean规范定义的接口，这是java.beans中一个接口，其设计的意图是图形化编程上，方便对象与String之间的转换工作，
	// 而spring将其扩展，方便各种对象与String之间的转换工作。

	// Spring所有的扩展都是通过继承PropertyEditorSupport，因为它只聚焦于转换上，所以只需复写setAsText()、getAsText()以及构造方法即可实现扩展。

	private final PropertiesEditor propertiesEditor = new PropertiesEditor();

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		this.propertiesEditor.setAsText(text);
		Properties props = (Properties) this.propertiesEditor.getValue();
		setValue(new MutablePropertyValues(props));
	}

}

