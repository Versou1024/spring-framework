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

package org.springframework.transaction.interceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;

/**
 * Simple {@link TransactionAttributeSource} implementation that
 * allows attributes to be stored per method in a {@link Map}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 24.04.2003
 * @see #isMatch
 * @see NameMatchTransactionAttributeSource
 */
public class MethodMapTransactionAttributeSource implements TransactionAttributeSource, BeanClassLoaderAware, InitializingBean {

	// 命名:
	// MethodMapTransactionAttributeSource = Method Map TransactionAttributeSource
	// 类似NamedTransactionAttributeSource,也有一个Map<String, TransactionAttribute> 的 map
	// 不过它还有一个: Map<Method, TransactionAttribute> 的 transactionAttributeMap

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	// 从 方法名 称映射到 事务属性TransactionAttribute
	// 这里的方法名必须是 全限定名package名 + "." + 类名  的全限定名
	@Nullable
	private Map<String, TransactionAttribute> methodMap;

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	// 是否需要急切的进行初始化
	private boolean eagerlyInitialized = false;

	// 是否已经完成初始化
	private boolean initialized = false;

	// 映射 Method 到 TransactionAttribute
	private final Map<Method, TransactionAttribute> transactionAttributeMap = new HashMap<>();

	// 映射 Method 到 用于注册的名称 
	private final Map<Method, String> methodNameMap = new HashMap<>();


	/**
	 * Set a name/attribute map, consisting of "FQCN.method" method names
	 * (e.g. "com.mycompany.mycode.MyClass.myMethod") and
	 * {@link TransactionAttribute} instances (or Strings to be converted
	 * to {@code TransactionAttribute} instances).
	 * <p>Intended for configuration via setter injection, typically within
	 * a Spring bean factory. Relies on {@link #afterPropertiesSet()}
	 * being called afterwards.
	 * @param methodMap said {@link Map} from method name to attribute value
	 * @see TransactionAttribute
	 * @see TransactionAttributeEditor
	 */
	public void setMethodMap(Map<String, TransactionAttribute> methodMap) {
		this.methodMap = methodMap;
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}


	/**
	 * Eagerly initializes the specified
	 * {@link #setMethodMap(java.util.Map) "methodMap"}, if any.
	 * @see #initMethodMap(java.util.Map)
	 */
	@Override
	public void afterPropertiesSet() {
		// 1. 需要提前设置好methodMap
		// 这里会对methodMap进行初始化处理 -> 根据methoMmap,去填充对应的transactionAttributeMap/methodNameMap
		initMethodMap(this.methodMap);
		// 2. 标记为设置为ture
		this.eagerlyInitialized = true;
		this.initialized = true;
	}

	/**
	 * Initialize the specified {@link #setMethodMap(java.util.Map) "methodMap"}, if any.
	 * @param methodMap a Map from method names to {@code TransactionAttribute} instances
	 * @see #setMethodMap
	 */
	protected void initMethodMap(@Nullable Map<String, TransactionAttribute> methodMap) {
		if (methodMap != null) {
			methodMap.forEach(this::addTransactionalMethod);
		}
	}


	/**
	 * Add an attribute for a transactional method.
	 * <p>Method names can end or start with "*" for matching multiple methods.
	 * @param name class and method name, separated by a dot
	 * @param attr attribute associated with the method
	 * @throws IllegalArgumentException in case of an invalid name
	 */
	public void addTransactionalMethod(String name, TransactionAttribute attr) {
		Assert.notNull(name, "Name must not be null");
		int lastDotIndex = name.lastIndexOf('.');
		if (lastDotIndex == -1) {
			throw new IllegalArgumentException("'" + name + "' is not a valid method name: format is FQN.methodName");
		}
		// 1. 从methodMap的key中分离出className和methodName
		String className = name.substring(0, lastDotIndex);
		String methodName = name.substring(lastDotIndex + 1);
		// 2. 创建clazz
		Class<?> clazz = ClassUtils.resolveClassName(className, this.beanClassLoader);
		// 3. 继续处理 -> 处理methodName
		addTransactionalMethod(clazz, methodName, attr);
	}

	/**
	 * Add an attribute for a transactional method.
	 * Method names can end or start with "*" for matching multiple methods.
	 * @param clazz target interface or class
	 * @param mappedName mapped method name
	 * @param attr attribute associated with the method
	 */
	public void addTransactionalMethod(Class<?> clazz, String mappedName, TransactionAttribute attr) {
		Assert.notNull(clazz, "Class must not be null");
		Assert.notNull(mappedName, "Mapped name must not be null");
		String name = clazz.getName() + '.'  + mappedName;
		
		// 1. 找到匹配的mappedName的方法,并加入到matchingMethods结果集中
		Method[] methods = clazz.getDeclaredMethods();
		List<Method> matchingMethods = new ArrayList<>();
		for (Method method : methods) {
			if (isMatch(method.getName(), mappedName)) {
				matchingMethods.add(method);
			}
		}
		if (matchingMethods.isEmpty()) {
			throw new IllegalArgumentException(
					"Could not find method '" + mappedName + "' on class [" + clazz.getName() + "]");
		}

		// 2. 注册所有匹配的方法
		for (Method method : matchingMethods) {
			// 2.1 检查methodNameMap中没有匹配的方法
			String regMethodName = this.methodNameMap.get(method);
			if (regMethodName == null || (!regMethodName.equals(name) && regMethodName.length() <= name.length())) {
				// 2.1.1 methodNameMap中不包含指定method对应的名称模式name
				if (logger.isDebugEnabled() && regMethodName != null) {
					logger.debug("Replacing attribute for transactional method [" + method + "]: current name '" +
							name + "' is more specific than '" + regMethodName + "'");
				}
				// 2.1.2 将上面创建的name作为对应method的名称模式
				this.methodNameMap.put(method, name);
				// 2.1.3 并将method和对应的attr加入到transactionAttributeMap中
				addTransactionalMethod(method, attr);
			}
			// 2.2 methodNameMap中有匹配的方法,直接返回即可
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Keeping attribute for transactional method [" + method + "]: current name '" +
							name + "' is not more specific than '" + regMethodName + "'");
				}
			}
		}
	}

	/**
	 * Add an attribute for a transactional method.
	 * @param method the method
	 * @param attr attribute associated with the method
	 */
	public void addTransactionalMethod(Method method, TransactionAttribute attr) {
		Assert.notNull(method, "Method must not be null");
		Assert.notNull(attr, "TransactionAttribute must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Adding transactional method [" + method + "] with attribute [" + attr + "]");
		}
		this.transactionAttributeMap.put(method, attr);
	}

	/**
	 * Return if the given method name matches the mapped name.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*"
	 * matches, as well as direct equality.
	 * @param methodName the method name of the class
	 * @param mappedName the name in the descriptor
	 * @return if the names match
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean isMatch(String methodName, String mappedName) {
		return PatternMatchUtils.simpleMatch(mappedName, methodName);
	}


	@Override
	@Nullable
	public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// 简单明了
		if (this.eagerlyInitialized) {
			return this.transactionAttributeMap.get(method);
		}
		else {
			synchronized (this.transactionAttributeMap) {
				if (!this.initialized) {
					initMethodMap(this.methodMap);
					this.initialized = true;
				}
				return this.transactionAttributeMap.get(method);
			}
		}
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MethodMapTransactionAttributeSource)) {
			return false;
		}
		MethodMapTransactionAttributeSource otherTas = (MethodMapTransactionAttributeSource) other;
		return ObjectUtils.nullSafeEquals(this.methodMap, otherTas.methodMap);
	}

	@Override
	public int hashCode() {
		return MethodMapTransactionAttributeSource.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + this.methodMap;
	}

}
