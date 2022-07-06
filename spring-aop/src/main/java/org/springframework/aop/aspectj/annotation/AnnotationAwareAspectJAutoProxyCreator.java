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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link AspectJAwareAdvisorAutoProxyCreator} subclass that processes all AspectJ
 * annotation aspects in the current application context, as well as Spring Advisors.
 *
 * <p>Any AspectJ annotated classes will automatically be recognized, and their
 * advice applied if Spring AOP's proxy-based model is capable of applying it.
 * This covers method execution joinpoints.
 *
 * <p>If the &lt;aop:include&gt; element is used, only @AspectJ beans with names matched by
 * an include pattern will be considered as defining aspects to use for Spring auto-proxying.
 *
 * <p>Processing of Spring Advisors follows the rules established in
 * {@link org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.aop.aspectj.annotation.AspectJAdvisorFactory
 */
@SuppressWarnings("serial")
public class AnnotationAwareAspectJAutoProxyCreator extends AspectJAwareAdvisorAutoProxyCreator {
	
	// 注入时机:
	// 在使用@EnableAspectJAutoProxy注解后,将向BeanDefinitionRegistry注册表中注入AnnotationAwareAspectJAutoProxyCreator的BeanDefinition
	
	// 作用:
	// 属于AspectJAwareAdvisorAutoProxyCreator的子类。是一个自动代理创建器帮助我们创建使用了AspectJ的代理
	// 因此其实我们的@EnableAspectJAutoProxy它导入的就是这个自动代理创建器去帮我们创建和AspectJ相关的代理对象的。【即AOP生效的地方】
	// 这也是我们当下使用最为广泛的方式~

	// 很显然，它还支持我们自定义一个正则的模版
	//并在isEligibleAspectBean()该方法使用此正则模板，从而决定使用哪些Adviso的bean可以被使用
	@Nullable
	private List<Pattern> includePatterns;

	// ❗️❗️❗️ -- 实际上 AspectJAdvisorFactory 才是最最重要的东西 -> 没有它,这个自动代理创建器甚至都无法生效
	// 唯一实现类：ReflectiveAspectJAdvisorFactory -- AspectJ切面类工厂
	// 作用：查找出@Aspect的切面类时,为其中的所有的AspectJ注解标注的通知方法创建Spring AOP的Advice或者Advisor
	// 里面会对标注这些注解Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class的方法进行排序
	// 然后把他们都变成Advisor( getAdvisors()方法 )
	@Nullable
	private AspectJAdvisorFactory aspectJAdvisorFactory;

	// 该工具类用来从bean容器，也就是BeanFactory中获取所有使用了@AspectJ注解的bean
	// 就是这个方法：aspectJAdvisorsBuilder.buildAspectJAdvisors()
	@Nullable
	private BeanFactoryAspectJAdvisorsBuilder aspectJAdvisorsBuilder; // AspectJ切面类构建器


	/**
	 * Set a list of regex patterns, matching eligible @AspectJ bean names.
	 * <p>Default is to consider all @AspectJ beans as eligible.
	 */
	public void setIncludePatterns(List<String> patterns) {
		// 很显然，它还支持我们自定义一个正则的模版
		// isEligibleAspectBean()该方法使用此模版，从而决定使用哪些Advisor
		this.includePatterns = new ArrayList<>(patterns.size());
		for (String patternText : patterns) {
			this.includePatterns.add(Pattern.compile(patternText));
		}
	}

	// 可以自己实现一个AspectJAdvisorFactory  否则用默认的ReflectiveAspectJAdvisorFactory
	public void setAspectJAdvisorFactory(AspectJAdvisorFactory aspectJAdvisorFactory) {
		Assert.notNull(aspectJAdvisorFactory, "AspectJAdvisorFactory must not be null");
		this.aspectJAdvisorFactory = aspectJAdvisorFactory;
	}

	// 此处一定要记得调用：super.initBeanFactory(beanFactory);
	@Override
	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// initBeanFactory 发生在顶级超类 AbstractAdvisorAutoProxyCreator#setBeanFactor()中 -> 也就是在bean的实例化之后,在初始化\初始化前置BeanPostProcessor之前
		// 就会知道被触发哦
		
		// ❗️❗️❗️
		super.initBeanFactory(beanFactory);
		if (this.aspectJAdvisorFactory == null) {
			// 1. ❗️❗️❗️❗️❗️❗️
			// 创建了 ReflectiveAspectJAdvisorFactory
			this.aspectJAdvisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory); // 常用的默认aspectJAdvisorFactory
		}
		this.aspectJAdvisorsBuilder = new BeanFactoryAspectJAdvisorsBuilderAdapter(beanFactory, this.aspectJAdvisorFactory); // 常用的默认的aspectJAdvisorsBuilder
	}


	// 拿到所有的候选的advisor们。请注意：这里先调用了父类的super.findCandidateAdvisors()  去容器里找出来一些
	// 然后，然后自己又通过aspectJAdvisorsBuilder.buildAspectJAdvisors()  解析@Aspect的方法得到一些Advisor
	@Override
	protected List<Advisor> findCandidateAdvisors() {
		
		// 1. 超类 AbstractAdvisorAutoProxyCreator#findCandidateAdvisors 利用BeanFactoryAdvisorRetrievalHelper从BeanFactory中获取合格的Advisor类型出俩
		// 也就说允许用户直接向Spring的ioc容器注入的原生的Advisor -> 是可以在这一步提取出来的
		List<Advisor> advisors = super.findCandidateAdvisors();
		if (this.aspectJAdvisorsBuilder != null) {
			// 2. 子类 AnnotationAwareAspectJAutoProxyCreator#findCandidateAdvisors[注解感知] 在用户定义的原生的Advisor的基础上
			// 去BeanFactory中找到使用@Aspect的切面类,然后找到其中有AspectJ注解的通知方法，并包装为advisor
			advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
		}
		return advisors;
	}

	@Override
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		// 在父类的isInfrastructureClass()校验规则额外扩展以下:
		// 就是如果该Bean自己本身就是一个@Aspect， 那也认为是属于AOP框架的,不应该被代理
		return (super.isInfrastructureClass(beanClass) ||
				(this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass)));
	}

	/**
	 * Check whether the given aspect bean is eligible for auto-proxying.
	 * <p>If no &lt;aop:include&gt; elements were used then "includePatterns" will be
	 * {@code null} and all beans are included. If "includePatterns" is non-null,
	 * then one of the patterns must match.
	 */
	protected boolean isEligibleAspectBean(String beanName) {
		// ❗️❗️❗️
		// 当 BeanFactoryAspectJAdvisorsBuilderAdapter.buildAspectJAdvisors()从BeanFactory中寻找@Aspect的切面类,并转换出切面类中的通知增强方法Advice时
		// 第一步就会间接调用这里的 isEligibleAspectBean() 来检查这个bean是否有资格作为Advisor,所以即使一个@Aspect标注的切面类只要在这里无法匹配上正则表达式
		// 那也只能是被放弃,无法继续对这个切面类处理
		
		// 1. 拿传入的正则模版进行匹配（没传就返回true，所有的Advisor都会生效）
		if (this.includePatterns == null) {
			return true;
		}
		else {
			// 2. 如果满足includePatterns的正则就返回ture,表示当前的Advisor会生效
			for (Pattern pattern : this.includePatterns) {
				if (pattern.matcher(beanName).matches()) {
					return true;
				}
			}
			return false;
		}
	}


	/**
	 * Subclass of BeanFactoryAspectJAdvisorsBuilderAdapter that delegates to
	 * surrounding AnnotationAwareAspectJAutoProxyCreator facilities.
	 */
	private class BeanFactoryAspectJAdvisorsBuilderAdapter extends BeanFactoryAspectJAdvisorsBuilder {
		// 对 BeanFactoryAspectJAdvisorsBuilder#isEligibleBean 重写
		// 并交给 AnnotationAwareAspectJAutoProxyCreator#isEligibleAspectBean 方法

		public BeanFactoryAspectJAdvisorsBuilderAdapter(
				ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {

			super(beanFactory, advisorFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AnnotationAwareAspectJAutoProxyCreator.this.isEligibleAspectBean(beanName);
		}
	}

}
