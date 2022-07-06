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

package org.springframework.aop.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.IntroductionInfo;
import org.springframework.util.ClassUtils;

/**
 * Support for implementations of {@link org.springframework.aop.IntroductionInfo}.
 *
 * <p>Allows subclasses to conveniently add all interfaces from a given object,
 * and to suppress interfaces that should not be added. Also allows for querying
 * all introduced interfaces.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class IntroductionInfoSupport implements IntroductionInfo, Serializable {

	// 发布给定的委托对象所实现的所有接口
	protected final Set<Class<?>> publishedInterfaces = new LinkedHashSet<>();

	// 缓存处理过的方法
	private transient Map<Method, Boolean> rememberedMethods = new ConcurrentHashMap<>(32);


	/**
	 * Suppress the specified interface, which may have been autodetected
	 * due to the delegate implementing it. Call this method to exclude
	 * internal interfaces from being visible at the proxy level.
	 * <p>Does nothing if the interface is not implemented by the delegate.
	 * @param ifc the interface to suppress
	 */
	public void suppressInterface(Class<?> ifc) {
		this.publishedInterfaces.remove(ifc);
	}

	@Override
	public Class<?>[] getInterfaces() {
		return ClassUtils.toClassArray(this.publishedInterfaces);
	}

	/**
	 * Check whether the specified interfaces is a published introduction interface.
	 * @param ifc the interface to check
	 * @return whether the interface is part of this introduction
	 */
	public boolean implementsInterface(Class<?> ifc) {
		for (Class<?> pubIfc : this.publishedInterfaces) {
			if (ifc.isInterface() && ifc.isAssignableFrom(pubIfc)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Publish all interfaces that the given delegate implements at the proxy level.
	 * @param delegate the delegate object
	 */
	protected void implementInterfacesOnObject(Object delegate) {
		this.publishedInterfaces.addAll(ClassUtils.getAllInterfacesAsSet(delegate));
	}

	/**
	 * Is this method on an introduced interface?
	 * @param mi the method invocation
	 * @return whether the invoked method is on an introduced interface
	 */
	protected final boolean isMethodOnIntroducedInterface(MethodInvocation mi) {
		// 检查方法mi是否在需要被代理类额外扩展的接口上
		
		// 1. 检查缓存
		Boolean rememberedResult = this.rememberedMethods.get(mi.getMethod());
		if (rememberedResult != null) {
			return rememberedResult;
		}
		else {
			// 2. 第一次检查是否为目标接口下的方法 -- 如果是的话就会交给实例化后的
			boolean result = implementsInterface(mi.getMethod().getDeclaringClass());
			this.rememberedMethods.put(mi.getMethod(), result);
			return result;
		}
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	/**
	 * This method is implemented only to restore the logger.
	 * We don't make the logger static as that would mean that subclasses
	 * would use this class's log category.
	 */
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();
		// Initialize transient fields.
		this.rememberedMethods = new ConcurrentHashMap<>(32);
	}

}
