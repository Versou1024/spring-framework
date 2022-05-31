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

package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.util.Properties;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Basic {@link AutowireCandidateResolver} that performs a full generic type
 * match with the candidate's type if the dependency is declared as a generic type
 * (e.g. Repository&lt;Customer&gt;).
 *
 * <p>This is the base class for
 * {@link org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver},
 * providing an implementation all non-annotation-based resolution steps at this level.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
public class GenericTypeAwareAutowireCandidateResolver extends SimpleAutowireCandidateResolver implements BeanFactoryAware, Cloneable {

	@Nullable
	private BeanFactory beanFactory;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Nullable
	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		// 1.isAutowireCandidate() -> return bdHolder.getBeanDefinition().isAutowireCandidate();
		// 检查当前beanDefinition是否被禁止DI依赖注入到其他Bean中去,一般默认就是返回true,允许作为依赖注入
		if (!super.isAutowireCandidate(bdHolder, descriptor)) {
			// If explicitly false, do not proceed with any other checks...
			return false;
		}
		// 这里，这里，这里  看方法名就能看出来。检测看看泛型是否匹配。
		// 若泛型都不匹配，就直接返回false了,基本步骤为：
		// 1、从descriptor里拿倒泛型类型
		// 2、First, check factory method return type, if applicable
		// 3、return dependencyType.isAssignableFrom(targetType);
		// 这个方法官方doc为：Full check for complex generic type match... 带泛型的全检查，而不是简单Class类型的判断
		// Spring4.0后的泛型依赖注入主要是它来实现的，所以这个类也是Spring4.0后出现的
		return checkGenericTypeMatch(bdHolder, descriptor);
	}

	/**
	 * Match the given dependency type with its generic type information against the given
	 * candidate bean definition.
	 */
	protected boolean checkGenericTypeMatch(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		// descriptor 依赖注入的目标对象,用来描述需要需要注入依赖的字段或形参
		// bdHolder 被用来检查是否可以作为注入的依赖,将注入到descriptorr对象中

		// 1. 依赖注入的目标对象,带泛型
		// 因此支持注入 List<XxxServer>
		ResolvableType dependencyType = descriptor.getResolvableType();
		if (dependencyType.getType() instanceof Class) {
			// 1.1 依赖注入的目标对象,没有泛型，属于Class，就直接返回true
			// No generic type -> we know it's a Class type-match, so no need to check again.
			return true;
		}

		// 2. 否则就是进行泛型匹配

		ResolvableType targetType = null; // 需要注入的字段或者形参的类型
		boolean cacheType = false; //
		RootBeanDefinition rbd = null; // 准备检查是否可以作为依赖注入的BeanDefinition
		if (bdHolder.getBeanDefinition() instanceof RootBeanDefinition) {
			rbd = (RootBeanDefinition) bdHolder.getBeanDefinition();
		}

		// 2.1 下面这个步骤：就是解析注入对象的目标类型，
		// 注意: 用来检查如果注入的值是bdHolder,注入到descriptor目标中是否合法
		// 会尝试直接从targetType获取、FactoryMethod的返回值获取、被修饰的BeanDefinition中获取

		if (rbd != null) {
			// 2.2 注入依赖的目标字段或形参的类型,可从BeanDefinition即rbd的目标类型获取
			targetType = rbd.targetType;
			// 2.3 没有指定targetType，那么可能就是FactoryMethod返回值的类型
			// 即@Bean标注的方法
			if (targetType == null) {
				cacheType = true;
				// First, check factory method return type, if applicable
				// 2.4 检查是否为FactoryMethod，比如@Bean标注的方法注入的类，就是FactoryMethod
				targetType = getReturnTypeForFactoryMethod(rbd, descriptor);
				if (targetType == null) {

					// 2.5 如果即没有直接指定targetType，也不是FactoryMethod，那么就只能够，对rbd进行重新解析BeanDefinition了
					RootBeanDefinition dbd = getResolvedDecoratedDefinition(rbd);
					if (dbd != null) {
						targetType = dbd.targetType;
						if (targetType == null) {
							targetType = getReturnTypeForFactoryMethod(dbd, descriptor);
						}
					}
				}
			}
		}

		// 2.6 执行到这，如果targetType仍为null，也就是说根据类型解析失败，可能需要根据名字解析出targetType

		if (targetType == null) {
			// Regular case: straight bean instance, with BeanFactory available.
			// 2.7 常规情况：纯bean实例，可使用BeanFactory。
			if (this.beanFactory != null) {
				// 2.8 根据目标对象的beanName获取目标对象的类型即可
				Class<?> beanType = this.beanFactory.getType(bdHolder.getBeanName());
				if (beanType != null) {
					targetType = ResolvableType.forClass(ClassUtils.getUserClass(beanType));
				}
			}
			// Fallback: no BeanFactory set, or no type resolvable through it
			// -> best-effort match against the target class if applicable.
			// 2.9 解析再次是失败，那就直接获取bean对应的class吧
			//
			if (targetType == null && rbd != null && rbd.hasBeanClass() && rbd.getFactoryMethodName() == null) {
				Class<?> beanClass = rbd.getBeanClass();
				// 2.10 非FactoryBean
				if (!FactoryBean.class.isAssignableFrom(beanClass)) {
					targetType = ResolvableType.forClass(ClassUtils.getUserClass(beanClass));
				}
			}
		}

		// 2.11 targetType 还是 null,啥也不说返回null吧
		if (targetType == null) {
			return true;
		}
		if (cacheType) {
			rbd.targetType = targetType;
		}
		if (descriptor.fallbackMatchAllowed() &&
				(targetType.hasUnresolvableGenerics() || targetType.resolve() == Properties.class)) {
			// Fallback matches allow unresolvable generics, e.g. plain HashMap to Map<String,String>;
			// and pragmatically also java.util.Properties to any Map (since despite formally being a
			// Map<Object,Object>, java.util.Properties is usually perceived as a Map<String,String>).
			return true;
		}
		// Full check for complex generic type match...
		// 全面检查复杂泛型类型匹配。。。
		// dependencyType:注入的依赖类型；
		// targetType:查找到注入对象的类型;
		return dependencyType.isAssignableFrom(targetType);
	}

	@Nullable
	protected RootBeanDefinition getResolvedDecoratedDefinition(RootBeanDefinition rbd) {
		// 获取rbd修饰的目标BeanDefinition
		BeanDefinitionHolder decDef = rbd.getDecoratedDefinition();
		if (decDef != null && this.beanFactory instanceof ConfigurableListableBeanFactory) {
			// 被修饰的BeanDefinition存在，验证是否存在BeanDefinitionRegister中
			ConfigurableListableBeanFactory clbf = (ConfigurableListableBeanFactory) this.beanFactory;
			if (clbf.containsBeanDefinition(decDef.getBeanName())) {
				BeanDefinition dbd = clbf.getMergedBeanDefinition(decDef.getBeanName());
				if (dbd instanceof RootBeanDefinition) {
					// 是的话，就返回修饰的BeanDefinition吧
					return (RootBeanDefinition) dbd;
				}
			}
		}
		// 如果没有修饰BeanDefinition，就返回null
		return null;
	}

	@Nullable
	protected ResolvableType getReturnTypeForFactoryMethod(RootBeanDefinition rbd, DependencyDescriptor descriptor) {
		// Should typically be set for any kind of factory method, since the BeanFactory
		// pre-resolves them before reaching out to the AutowireCandidateResolver...
		// 1. 检查是否有returnType
		ResolvableType returnType = rbd.factoryMethodReturnType;
		if (returnType == null) {
			// 2. 没有returnType,还得继续检查解析的FactoryMethod
			// 因为 returnType 可以是懒加载的
			Method factoryMethod = rbd.getResolvedFactoryMethod();
			if (factoryMethod != null) {
				// 3. 解析返回值
				returnType = ResolvableType.forMethodReturnType(factoryMethod);
			}
		}
		// 2.
		if (returnType != null) {
			Class<?> resolvedClass = returnType.resolve();
			if (resolvedClass != null && descriptor.getDependencyType().isAssignableFrom(resolvedClass)) {
				// Only use factory method metadata if the return type is actually expressive enough
				// for our dependency. Otherwise, the returned instance type may have matched instead
				// in case of a singleton instance having been registered with the container already.
				return returnType;
			}
		}
		// 3. 说明不是说 FactoryMethod
		return null;
	}


	/**
	 * This implementation clones all instance fields through standard
	 * {@link Cloneable} support, allowing for subsequent reconfiguration
	 * of the cloned instance through a fresh {@link #setBeanFactory} call.
	 * @see #clone()
	 */
	@Override
	public AutowireCandidateResolver cloneIfNecessary() {
		try {
			return (AutowireCandidateResolver) clone();
		}
		catch (CloneNotSupportedException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
