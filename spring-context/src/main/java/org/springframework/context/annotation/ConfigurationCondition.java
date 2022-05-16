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

package org.springframework.context.annotation;

/**
 * A {@link Condition} that offers more fine-grained control when used with
 * {@code @Configuration}. Allows certain conditions to adapt when they match
 * based on the configuration phase. For example, a condition that checks if a bean
 * has already been registered might choose to only be evaluated during the
 * {@link ConfigurationPhase#REGISTER_BEAN REGISTER_BEAN} {@link ConfigurationPhase}.
 *
 * @author Phillip Webb
 * @since 4.0
 * @see Configuration
 */
public interface ConfigurationCondition extends Condition {
	/*
	 * getConfigurationPhase() 定义了子类必须实现，返回下面枚举的一种
	 */

	/**
	 * Return the {@link ConfigurationPhase} in which the condition should be evaluated.
	 */

	// 判断阶段
	ConfigurationPhase getConfigurationPhase();


	/**
	 * The various configuration phases where the condition could be evaluated.
	 */
	enum ConfigurationPhase {

		/**
		 * The {@link Condition} should be evaluated as a {@code @Configuration}
		 * class is being parsed.
		 * <p>If the condition does not match at this point, the {@code @Configuration}
		 * class will not be added.
		 */
		PARSE_CONFIGURATION,  // 当前的Condition在配置类解析时执行.如果该condition返回false,则该配置类不会被解析
		// 因此如果@Configuration的class在解析阶段没有通过这个条件，就会不会加到BeanDefinition注册中心

		/**
		 * The {@link Condition} should be evaluated when adding a regular
		 * (non {@code @Configuration}) bean. The condition will not prevent
		 * {@code @Configuration} classes from being added.
		 * <p>At the time that the condition is evaluated, all {@code @Configuration}
		 * classes will have been parsed.
		 */
		REGISTER_BEAN 		// 当前的Condition在注册bean时执行
		// 在添加常规（非@Configuration）bean时，应该评估该条件。该条件不会阻止添加@Configuration类。
		// 在计算条件时，所有@Configuration类都将被解析。

	}

}
