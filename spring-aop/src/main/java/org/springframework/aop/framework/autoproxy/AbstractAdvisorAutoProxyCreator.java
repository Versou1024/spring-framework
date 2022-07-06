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

package org.springframework.aop.framework.autoproxy;

import java.util.List;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Generic auto proxy creator that builds AOP proxies for specific beans
 * based on detected Advisors for each bean.
 *
 * <p>Subclasses may override the {@link #findCandidateAdvisors()} method to
 * return a custom list of Advisors applying to any object. Subclasses can
 * also override the inherited {@link #shouldSkip} method to exclude certain
 * objects from auto-proxying.
 *
 * <p>Advisors or advices requiring ordering should be annotated with
 * {@link org.springframework.core.annotation.Order @Order} or implement the
 * {@link org.springframework.core.Ordered} interface. This class sorts
 * advisors using the {@link AnnotationAwareOrderComparator}. Advisors that are
 * not annotated with {@code @Order} or don't implement the {@code Ordered}
 * interface will be considered as unordered; they will appear at the end of the
 * advisor chain in an undefined order.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #findCandidateAdvisors
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {
	/*
	 * AbstractAdvisorAutoProxyCreator 通用适配到Advisor的自动代理创建器，基于检测到的每个bean的Advisors，为特定bean构建AOP代理。
	 * 子类可以重写 findCandidateAdvisors() 方法，以返回应用于任何对象的自定义Advisors列表.
	 * 子类还可以重写继承的shouldSkip方法，将某些对象从自动代理autoAopProxy中排除。
	 * 		可以使用@Order注释或未实现Ordered接口的Advisor进行排序；
	 * 		未使用@Order注释或未实现Ordered接口的Advisor将被视为无序；它们将以未定义的顺序出现在advisor链的末尾。
	 */

	// ❗️❗️❗️
	// 见名知其重要性 -- 持有BeanFactory,以从其中检索出切面列中的Advisor集合,并进行汇总
	@Nullable
	private BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		// ❗️❗️❗️重写了setBeanFactory方法，事需要保证bean工厂必须是ConfigurableListableBeanFactory
		super.setBeanFactory(beanFactory);
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		// 就这一句话：this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory)
		// 对Helper进行初始化，找advisor最终事委托给他了的
		// BeanFactoryAdvisorRetrievalHelperAdapter继承自BeanFactoryAdvisorRetrievalHelper,为私有内部类，主要重写了isEligibleBean（）方法，调用 this.isEligibleAdvisorBean(beanName) 方法
		initBeanFactory((ConfigurableListableBeanFactory) beanFactory);
	}

	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
	}


	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
		// 1. ️调用findEligibleAdvisors()
		List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
		// 2. advisors为空，无须额外代理,使用普通代理即interceptorNames对应的拦截器即可吧
		if (advisors.isEmpty()) {
			return DO_NOT_PROXY;
		}
		return advisors.toArray();
	}

	/**
	 * Find all eligible Advisors for auto-proxying this class.
	 * @param beanClass the clazz to find advisors for
	 * @param beanName the name of the currently proxied bean
	 * @return the empty List, not {@code null},
	 * if there are no pointcuts or interceptors
	 * @see #findCandidateAdvisors
	 * @see #sortAdvisors
	 * @see #extendAdvisors
	 */
	protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
		// 找出合适的Advisor们~~~  主要分了下面几步
		// 简答描述:
		// 子类AbstractAdvisorAutoProxyCreator重写getAdvicesAndAdvisorsForBean()方法
		// 1. 调用findCandidateAdvisors()查看可用的Advisor
		// 		1.1 AbstractAdvisorAutoProxyCreator.findCandidateAdvisors()仅仅是去ioc容器中查找Advisor类型的bean，作为候选的Advisor，也就是用户可以向ioc容器直接注入Advisor
		// 		1.2 其子类 AnnotationAwareAspectJAutoProxyCreator.findCandidateAdvisors() 在1.1基础又去ioc容器中查找所有@Aspect的切面类，将切面类中使用@After、@Before等AspectJ注解标注的通知方法转换为Advisor后返回出来
		// 2. 调用findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName)确定candidateAdvisors可以给beanClass的bean使用的那一批Advisor【期间会使用ClassFilter|MethodMatcher进行判断】
		// 3. 调用extendAdvisors()去扩展Advisor集合，主要是如果匹配上的Advisor中有切面类中通知方法形成的Advisor，就必须在advisors的第一个位置加入`ExposeInvocationInterceptor.ADVISOR`用来暴露MethodInvocation
		// 4. 调用sortAdvisors(eligibleAdvisors)对合格的Advisor进行排序
		// 		4.1 其子类AspectJAwareAdvisorAutoProxyCreator重写了sortAdvisors(eligibleAdvisors)方法，主要以以下依据排序
		// 			当给出两条advice比如A和B ：
		// 			如果A和B被定义在不同的切面上，那么具有更低order值的切面的中的通知方法具有最高优先级。
		// 			如果A和B定义在同一个切面上，如果A或B中的一个是after advice的形式，则在同一个切面中中after advice的通知增强方法会具有最高优先级。
		// 			如果A和B定义在同一个切面上,但A和B都不是 after advice的一种形式[@After @AfterThrowing @AfterReturning]，则在方面中首先声明的通知具有最高优先级。

		// 1. 首先找出所有的候选的Advisors，（根据名字判断）实现见下面~~~~
		// 包括BeanFactory中已经注册的Advisor的实现类；也包括BeanFactory中的@Aspect注解的bean中带有@Before、@After的方法转换为Advisor值
		List<Advisor> candidateAdvisors = findCandidateAdvisors();
		// 2. 对上面找到的候选的Advisors们，进行过滤操作~~~  看看Advisor能否被用在Bean上（根据Advisor的PointCut判断）
		// 主要依赖于AopUtils.findAdvisorsThatCanApply()方法  在工具类讲解中有详细分析的
		// 逻辑简单概述为：看目标类是不是符合代理对象的条件，如果符合就把Advisor加到集合中，最后返回集合
		// 简单的说：它就是会根据ClassFilter和MethodMatcher等等各种匹配。（但凡只有有一个方法被匹配上了，就会给他创建代理类了）
		// 方法用的ReflectionUtils.getAllDeclaredMethods，**因此哪怕是私有方法，匹配上都会给创建的代理对象，这点务必要特别特别的注意**
		List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
		// 3. 提供一个钩子。子类可以复写此方法  然后对eligibleAdvisors进行处理（增加/删除/修改等等）
		// AspectJAwareAdvisorAutoProxyCreator提供了实现
		extendAdvisors(eligibleAdvisors);
		// 4. 排序
		if (!eligibleAdvisors.isEmpty()) {
			// 5. 默认排序方式：AnnotationAwareOrderComparator.sort()排序  这个排序和PriorityOrder接口\Order接口\@Order有关~~~
			// 但是子类：AspectJAwareAdvisorAutoProxyCreator有复写此排序方法，需要特别注意~~~
			eligibleAdvisors = sortAdvisors(eligibleAdvisors);
		}
		return eligibleAdvisors;
	}

	/**
	 * Find all candidate Advisors to use in auto-proxying.
	 * @return the List of candidate Advisors
	 */
	protected List<Advisor> findCandidateAdvisors() {
		// 利用 BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans 查找所有BeanFactory中的注册的Advisor切面
		Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
		return this.advisorRetrievalHelper.findAdvisorBeans();
	}

	/**
	 * Search the given candidate Advisors to find all Advisors that
	 * can apply to the specified bean.
	 * @param candidateAdvisors the candidate Advisors
	 * @param beanClass the target's bean class
	 * @param beanName the target's bean name
	 * @return the List of applicable Advisors
	 * @see ProxyCreationContext#getCurrentProxiedBeanName()
	 */
	protected List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {
		// ❗️❗️❗️ 在Proxy创建的上下文中
		ProxyCreationContext.setCurrentProxiedBeanName(beanName);
		try {
			// 遍历候选的candidateAdvisors，查看beanClass是否需要被增强
			return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
		}
		finally {
			ProxyCreationContext.setCurrentProxiedBeanName(null);
		}
	}

	/**
	 * Return whether the Advisor bean with the given name is eligible
	 * for proxying in the first place.
	 * @param beanName the name of the Advisor bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleAdvisorBean(String beanName) {
		// 判断给定的BeanName这个Bean，是否是合格的(BeanFactoryAdvisorRetrievalHelper里会用到这个属性)
		// 其中：DefaultAdvisorAutoProxyCreator和InfrastructureAdvisorAutoProxyCreator有复写
		return true;
	}

	/**
	 * Sort advisors based on ordering. Subclasses may choose to override this
	 * method to customize the sorting strategy.
	 * @param advisors the source List of Advisors
	 * @return the sorted List of Advisors
	 * @see org.springframework.core.Ordered
	 * @see org.springframework.core.annotation.Order
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		AnnotationAwareOrderComparator.sort(advisors);
		return advisors;
	}

	/**
	 * Extension hook that subclasses can override to register additional Advisors,
	 * given the sorted Advisors obtained to date.
	 * <p>The default implementation is empty.
	 * <p>Typically used to add Advisors that expose contextual information
	 * required by some of the later advisors.
	 * @param candidateAdvisors the Advisors that have already been identified as
	 * applying to a given bean
	 */
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
	}

	/**
	 * This auto-proxy creator always returns pre-filtered Advisors.
	 */
	@Override
	protected boolean advisorsPreFiltered() {
		return true;
	}


	/**
	 * Subclass of BeanFactoryAdvisorRetrievalHelper that delegates to
	 * surrounding AbstractAdvisorAutoProxyCreator facilities.
	 */
	private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {
		// 继承的是: BeanFactoryAdvisorRetrievalHelper

		public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
			super(beanFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			// 用从ioc容器取出Advisor后,根据Advisor的beanName检查是否合格,不合格就无法加入到返回的Advisors集合中
			return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
		}
	}

}
