/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.TargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * Auto proxy creator that identifies beans to proxy via a list of names.
 * Checks for direct, "xxx*", and "*xxx" matches.
 *
 * <p>For configuration details, see the javadoc of the parent class
 * AbstractAutoProxyCreator. Typically, you will specify a list of
 * interceptor names to apply to all identified beans, via the
 * "interceptorNames" property.
 *
 * @author Juergen Hoeller
 * @since 10.10.2003
 * @see #setBeanNames
 * @see #isMatch
 * @see #setInterceptorNames
 * @see AbstractAutoProxyCreator
 */
@SuppressWarnings("serial")
public class BeanNameAutoProxyCreator extends AbstractAutoProxyCreator {
	// BeanNameAutoProxyCreator 根据beanName判断是否需要做AOP代理 ~~ 简单了解即可

	@Nullable
	private List<String> beanNames; // 用来判断beanName的bean是否需要做AOP代理


	/**
	 * Set the names of the beans that should automatically get wrapped with proxies.
	 * A name can specify a prefix to match by ending with "*", e.g. "myBean,tx*"
	 * will match the bean named "myBean" and all beans whose name start with "tx".
	 * <p><b>NOTE:</b> In case of a FactoryBean, only the objects created by the
	 * FactoryBean will get proxied. This default behavior applies as of Spring 2.0.
	 * If you intend to proxy a FactoryBean instance itself (a rare use case, but
	 * Spring 1.2's default behavior), specify the bean name of the FactoryBean
	 * including the factory-bean prefix "&": e.g. "&myFactoryBean".
	 * @see org.springframework.beans.factory.FactoryBean
	 * @see org.springframework.beans.factory.BeanFactory#FACTORY_BEAN_PREFIX
	 */
	public void setBeanNames(String... beanNames) {
		Assert.notEmpty(beanNames, "'beanNames' must not be empty");
		this.beanNames = new ArrayList<>(beanNames.length);
		for (String mappedName : beanNames) {
			this.beanNames.add(StringUtils.trimWhitespace(mappedName));
		}
	}


	/**
	 * Identify as bean to proxy if the bean name is in the configured list of names.
	 */
	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
		// ❗️这里面注意一点：BeanNameAutoProxyCreator的此方法并没有去寻找Advisor，所以需要拦截的话
		// 只能依靠：setInterceptorNames()来指定拦截器。它是根据名字去Bean容器里取的

		if (this.beanNames != null) {
			// 根据beanNames进行尝试匹配beanName
			for (String mappedName : this.beanNames) {
				// beanClass是FactoryBean，那么同样如果要将其匹配为Proxy代理类，mappedName也必须有一个"&"前缀进行匹配
				if (FactoryBean.class.isAssignableFrom(beanClass)) {
					if (!mappedName.startsWith(BeanFactory.FACTORY_BEAN_PREFIX)) {
						continue;
					}
					// 去掉 & 前缀
					mappedName = mappedName.substring(BeanFactory.FACTORY_BEAN_PREFIX.length());
				}
				// 检查是否需要match -- 源码：ant风格 PatternMatchUtils.simpleMatch(mappedName, beanName)
				if (isMatch(beanName, mappedName)) {
					return PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS;
				}
				// 这里需要注意的是，如国存在Bean工厂，哪怕任意一个alias匹配都是可以的~~~
				BeanFactory beanFactory = getBeanFactory();
				if (beanFactory != null) {
					String[] aliases = beanFactory.getAliases(beanName);
					for (String alias : aliases) {
						// 别名也需要尝试匹配
						if (isMatch(alias, mappedName)) {
							return PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS;
						}
					}
				}
			}
		}
		return DO_NOT_PROXY;
	}

	/**
	 * Return if the given bean name matches the mapped name.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*" matches,
	 * as well as direct equality. Can be overridden in subclasses.
	 * @param beanName the bean name to check
	 * @param mappedName the name in the configured list of names
	 * @return if the names match
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean isMatch(String beanName, String mappedName) {
		return PatternMatchUtils.simpleMatch(mappedName, beanName);
	}

}
