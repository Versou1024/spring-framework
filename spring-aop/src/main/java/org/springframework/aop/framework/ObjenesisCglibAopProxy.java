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

package org.springframework.aop.framework;

import java.lang.reflect.Constructor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.ReflectionUtils;

/**
 * Objenesis-based extension of {@link CglibAopProxy} to create proxy instances
 * without invoking the constructor of the class. Used by default as of Spring 4.
 *
 * @author Oliver Gierke
 * @author Juergen Hoeller
 * @since 4.0
 */
@SuppressWarnings("serial")
class ObjenesisCglibAopProxy extends CglibAopProxy {
	/**
	 * 基于对象的CglibAopProxy扩展，用于创建代理实例，而无需调用类的构造函数。从Spring 4开始默认使用。
	 * Objenesis它专门用来创建对象，即使你没有空的构造函数，都木有问题~~ 可谓非常的强大，它不使用构造方法创建Java对象，所以即使你有空的构造方法，也是不会执行的。
	 */

	private static final Log logger = LogFactory.getLog(ObjenesisCglibAopProxy.class);

	private static final SpringObjenesis objenesis = new SpringObjenesis();


	/**
	 * Create a new ObjenesisCglibAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 */
	public ObjenesisCglibAopProxy(AdvisedSupport config) {
		super(config);
	}


	@Override
	protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
		// 创建一个代理得实例
		Class<?> proxyClass = enhancer.createClass();
		Object proxyInstance = null;

		// 如果为true，那我们就采用objenesis去new一个实例
		// Spring为我们提供了一个isWorthTrying()方法：
		// 是否需要尝试：也就是说，它是否还没有被使用过，或者已知是否有效。方法返回true，表示值得尝试
		// 如果配置的Objenesis Instantiator策略被确定为不处理当前JVM。或者系统属性"spring.objenesis.ignore"值设置为true，表示不尝试了
		// 这个在ObjenesisCglibAopProxy创建代理实例的时候用到了。若不尝试使用Objenesis，那就还是用老的方式用空构造函数吧
		if (objenesis.isWorthTrying()) {
			try {
				proxyInstance = objenesis.newInstance(proxyClass, enhancer.getUseCache());
			}
			catch (Throwable ex) {
				logger.debug("Unable to instantiate proxy using Objenesis, " +
						"falling back to regular proxy construction", ex);
			}
		}

		// 若果还为null，就再去拿到构造函数（指定参数的）
		if (proxyInstance == null) {
			// Regular instantiation via default constructor...
			try {
				Constructor<?> ctor = (this.constructorArgs != null ?
						proxyClass.getDeclaredConstructor(this.constructorArgTypes) :
						proxyClass.getDeclaredConstructor());
				// 通过此构造函数  去new一个实例
				ReflectionUtils.makeAccessible(ctor);
				proxyInstance = (this.constructorArgs != null ?
						ctor.newInstance(this.constructorArgs) : ctor.newInstance());
			}
			catch (Throwable ex) {
				throw new AopConfigException("Unable to instantiate proxy using Objenesis, " +
						"and regular proxy instantiation via default constructor fails as well", ex);
			}
		}

		((Factory) proxyInstance).setCallbacks(callbacks);
		return proxyInstance;
	}

}
