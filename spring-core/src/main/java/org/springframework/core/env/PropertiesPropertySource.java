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

package org.springframework.core.env;

import java.util.Map;
import java.util.Properties;

/**
 * {@link PropertySource} implementation that extracts properties from a
 * {@link java.util.Properties} object.
 *
 * <p>Note that because a {@code Properties} object is technically an
 * {@code <Object, Object>} {@link java.util.Hashtable Hashtable}, one may contain
 * non-{@code String} keys or values. This implementation, however is restricted to
 * accessing only {@code String}-based keys and values, in the same fashion as
 * {@link Properties#getProperty} and {@link Properties#setProperty}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
public class PropertiesPropertySource extends MapPropertySource {

	/**
	 * 数据源为Properties
	 * 请注意，由于Properties对象在实现上是一个<object，object>哈希表，因此可能包含非字符串键或值。
	 * 然而，这种实现仅限于访问基于字符串的键和值，方式与属性相同。getProperty和Properties。setProperty。
	 */

	@SuppressWarnings({"rawtypes", "unchecked"})
	public PropertiesPropertySource(String name, Properties source) {
		super(name, (Map) source); // Properties类本身就是通过继承HansTab实现的，因此这里的Properties属性源继承了Map属性源
	}

	protected PropertiesPropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}


	@Override
	public String[] getPropertyNames() {
		synchronized (this.source) {
			return super.getPropertyNames();
		}
	}

}
