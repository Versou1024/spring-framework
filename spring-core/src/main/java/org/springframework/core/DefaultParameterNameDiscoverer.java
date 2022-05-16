/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core;

/**
 * Default implementation of the {@link ParameterNameDiscoverer} strategy interface,
 * using the Java 8 standard reflection mechanism (if available), and falling back
 * to the ASM-based {@link LocalVariableTableParameterNameDiscoverer} for checking
 * debug information in the class file.
 *
 * <p>If a Kotlin reflection implementation is present,
 * {@link KotlinReflectionParameterNameDiscoverer} is added first in the list and
 * used for Kotlin classes and interfaces. When compiling or running as a GraalVM
 * native image, the {@code KotlinReflectionParameterNameDiscoverer} is not used.
 *
 * <p>Further discoverers may be added through {@link #addDiscoverer(ParameterNameDiscoverer)}.
 *
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 4.0
 * @see StandardReflectionParameterNameDiscoverer
 * @see LocalVariableTableParameterNameDiscoverer
 * @see KotlinReflectionParameterNameDiscoverer
 */
public class DefaultParameterNameDiscoverer extends PrioritizedParameterNameDiscoverer {
	/**
	 * 继承 PrioritizedParameterNameDiscoverer
	 *
	 * DefaultParameterNameDiscoverer：它其实就是个聚合的作用
	 */

	public DefaultParameterNameDiscoverer() {
		// 这里非常非常需要注意的一点是：用于存储的是一个LinkedList（见父类：PrioritizedParameterNameDiscoverer）
		// LinkedList是先进先出的。所以for循环遍历的时候，会最先执行Kotlin、Standard、Local... 按照这个优先级
		if (KotlinDetector.isKotlinReflectPresent() && !GraalDetector.inImageCode()) {
			addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
		}
		// 默认至少添加一下两个形参名发现器 【StandardReflectionParameterNameDiscoverer】 / 【LocalVariableTableParameterNameDiscoverer】
		addDiscoverer(new StandardReflectionParameterNameDiscoverer()); // 两个具体的形参名实现者
		addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
	}

}
