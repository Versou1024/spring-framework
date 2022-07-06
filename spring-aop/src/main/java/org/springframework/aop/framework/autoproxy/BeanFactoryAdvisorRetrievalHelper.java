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

package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AbstractAdvisorAutoProxyCreator
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	// 本地会做一个简单的字段缓存
	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * Find all eligible Advisor beans in the current bean factory,
	 * ignoring FactoryBeans and excluding beans that are currently in creation.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> findAdvisorBeans() {
		// note: 这里是从Spring ioc容器中寻找原生的Advisor,这也就是说允许用户直接注入Advisor的实现类
		// 而 常用的AspectJ注解标注的切面是在 AnnotationAwareAspectJAutoProxyCreator的BeanFactoryAspectJAdvisorsBuilder和AspectJAdvisorFactory
		// 下完成查找的
		
		// 所以这里的并不是核心哦 -- 只是说用户可以直接注入Advisor的实现类 ~~ 但几乎没有人这样使用过的
		
		// 1. 确定 advisor bean name 的列表（如果尚未兑现）
		String[] advisorNames = this.cachedAdvisorBeanNames;
		// 2. 尚未缓存cachedAdvisorBeanNames表示还没有经过初始化，需
		if (advisorNames == null) {
			// 2.1 找到所有在BeanFactory中生命过的Advisor的beanNames集合
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Advisor.class, true, false);
			this.cachedAdvisorBeanNames = advisorNames;
		}
		// 3. 如果容器里面没有注册过任何的advisor，那就拉倒吧 ??
		// 实际上: 切面类中的使用AspectJ注解标注的通知方法早就被解析为Advisor存到ioc容器中
		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		// 4. 开始遍历ioc容器中Advisor类型的beanNames集合
		for (String name : advisorNames) {
			// 4.1 isEligibleBean：表示这个bean作为Advisor是否是合格的，默认是true
			if (isEligibleBean(name)) {
				// 4.1.1 如果当前Bean正在创建中,提示一个异常
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				// 4.1.2 否则就把这个Advisor加入到advisors的List里面，是个合法的
				else {
					try {
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					}
					catch (BeanCreationException ex) {
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// Ignore: indicates a reference back to the bean we're trying to advise.
								// We want to find advisors other than the currently created bean itself.
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}

	/**
	 * Determine whether the aspect bean with the given name is eligible.
	 * <p>The default implementation always returns {@code true}.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
