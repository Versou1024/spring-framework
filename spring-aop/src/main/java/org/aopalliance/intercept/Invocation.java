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

package org.aopalliance.intercept;

/**
 * This interface represents an invocation in the program.
 *
 * <p>An invocation is a joinpoint and can be intercepted by an
 * interceptor.
 *
 * @author Rod Johnson
 */
public interface Invocation extends Joinpoint {
	/**
	 * aop联盟，Invocation继承 Joinpoint，扩展 getArguments
	 * 首先需要注意的是，一般我们会接触到两个Joinpoint
	 *
	 * org.aspectj.lang.JoinPoint：该对象封装了SpringAop中切面方法的信息,在切面方法中添加JoinPoint参数，可以很方便的获得更多信息。（一般用于@Aspect标注的切面的方法入参里），它的API很多，常用的有下面几个：
	 * 1. Signature getSignature(); ：封装了署名信息的对象,在该对象中可以获取到目标方法名,所属类的Class等信息
	 * 2. Object[] getArgs();：传入目标方法的参数们
	 * 3. Object getTarget();：被代理的对象（目标对象）
	 * 4. Object getThis();：该代理对象
	 *
	 * org.aopalliance.intercept.Joinpoint是本文的重点，下面主要看看它的解释和相关方法：
	 * 	1.Object proceed() throws Throwable; 执行此拦截点，并进入到下一个连接点
	 * 	2.Object getThis(); 返回保存当前连接点静态部分【的对象】。  这里一般指的target
	 * 	3.AccessibleObject getStaticPart();	返回此静态连接点  一般就为当前的Method(至少目前的唯一实现是MethodInvocation,所以连接点得静态部分肯定就是本方法喽)
	 */

	/**
	 * Get the arguments as an array object.
	 * It is possible to change element values within this
	 * array to change the arguments.
	 * @return the argument of the invocation
	 */
	Object[] getArguments();

}
