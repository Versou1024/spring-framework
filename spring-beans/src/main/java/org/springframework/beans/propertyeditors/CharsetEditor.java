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

package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.nio.charset.Charset;

import org.springframework.util.StringUtils;

/**
 * Editor for {@code java.nio.charset.Charset}, translating charset
 * String representations into Charset objects and back.
 *
 * <p>Expects the same syntax as Charset's {@link java.nio.charset.Charset#name()},
 * e.g. {@code UTF-8}, {@code ISO-8859-16}, etc.
 *
 * @author Arjen Poutsma
 * @since 2.5.4
 * @see Charset
 */
public class CharsetEditor extends PropertyEditorSupport {

	/**
	 * PropertyEditorSupport 属性编辑器的支持实现
	 * 1、聚合有属性修改时的，事件类的source
	 * 2、聚合目标对象value，即要编辑的新目标对象。请注意，PropertyEditor不应修改此对象，而应创建一个新对象来保存任何修改后的值。
	 *
	 * 字符属性编辑器
	 * 1、目标value实际为Charset
	 * 2、通过setAsText将string转为charset设置到value上
	 * 3、或从getAsText获取对应的String
	 */
	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)) {
			setValue(Charset.forName(text));
		}
		else {
			setValue(null);
		}
	}

	@Override
	public String getAsText() {
		Charset value = (Charset) getValue();
		return (value != null ? value.name() : "");
	}

}
