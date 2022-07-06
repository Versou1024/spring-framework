/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.aop.aspectj;

import org.springframework.aop.PointcutAdvisor;

/**
 * Interface to be implemented by Spring AOP Advisors wrapping AspectJ
 * aspects that may have a lazy initialization strategy. For example,
 * a perThis instantiation model would mean lazy initialization of the advice.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
public interface InstantiationModelAwarePointcutAdvisor extends PointcutAdvisor {
	// PointcutAdvisor 本身就是SpringAOP提出的概念
	// 而Spring为了使用@AspectJ各种切入点的概念,以及AspectJ的表达式语法
	
	// 就必须通过一种方法:将AspectJ的表达式语法,翻译为增强的ClassFilter和MethodMatcher
	// 而将Aspect系列注解标注的方法作为增强通知advice来使用

	/**
	 * Return whether this advisor is lazily initializing its underlying advice.
	 */
	boolean isLazy(); 
	// 返回当前Advisor是否为延迟初始化器底层的advice哦

	/**
	 * Return whether this advisor has already instantiated its advice.
	 */
	boolean isAdviceInstantiated();
	// 检查这个advisor是否已经实例化了它的advice

}
