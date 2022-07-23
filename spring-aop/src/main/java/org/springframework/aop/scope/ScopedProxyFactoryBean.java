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

package org.springframework.aop.scope;

import java.lang.reflect.Modifier;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.aop.target.SimpleBeanTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Convenient proxy factory bean for scoped objects.
 *
 * <p>Proxies created using this factory bean are thread-safe singletons
 * and may be injected into shared objects, with transparent scoping behavior.
 *
 * <p>Proxies returned by this class implement the {@link ScopedObject} interface.
 * This presently allows for removing the corresponding object from the scope,
 * seamlessly creating a new instance in the scope on next access.
 *
 * <p>Please note that the proxies created by this factory are
 * <i>class-based</i> proxies by default. This can be customized
 * through switching the "proxyTargetClass" property to "false".
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see #setProxyTargetClass
 */
@SuppressWarnings("serial")
public class ScopedProxyFactoryBean extends ProxyConfig implements FactoryBean<Object>, BeanFactoryAware, AopInfrastructureBean {
	// 命名:
	// Scoped Proxy FactoryBean == 范围代理的FactoryBean
	// 和@Scope注解有关哦 ->
	
	// 使用到该类的流程之一:
	// ClassPathBeanDefinitionScanner#doScan()方法中触发:AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry) ->
	//	1. 检查类上是否有@Scope注解,有的话,去获取其@Scope的value属性作为Scope的name,@Scope的proxyMode属性作为代理模式[只要非ScopedProxyMode.DEFAULT]
	//  2. 将上面的ScopeMetadata设置到扫描出来的BeanDefinition的scope属性上面去
	//  3. ...
	//  4. 如果第一步生成的ScopeMetaData中的ScopeProxyMode非ScopedProxyMode.NO -- 就表示需要代理 [ScopedProxyMode.TARGE_CLASS表示Cglib代理,否则就是JDK代理]
	//	5. 将扫描到的BeanDefinition用 RootBeanDefinition(ScopedProxyFactoryBean.class) 来代替
	//  6. 而代替或者代理的 RootBeanDefinition(ScopedProxyFactoryBean.class) 中持有原来的BeanDefinition的相关信息
	//		6.1 代理的BeanDefinition的beanName为 "scopedTarget." + 目标BeanDefinition的BeanName
	//		6.2 source为目标BeanDefinition的source\role为目标BeanDefinition的role\originatingBeanDefinition就是目标BeanDefinition..
	//		6.3 复制目标BeanFactory的primary属性
	// 	7. 设置代理 RootBeanDefinition(ScopedProxyFactoryBean.class) 中的 targetBeanName 以及 proxyTargetClass 属性值
	// 	8. 将目标BeanDefinition的autowireCandidate设置为false,表示目标BeanDefinition不能作为候选bean被注入,在依赖注入时应该使用代理的BeanDefinition
	//	9. 目标BeanDefinition和代理的BeanDefinition都加入到BEanDefinitionRegistry中去

	/** The TargetSource that manages scoping. */
	private final SimpleBeanTargetSource scopedTargetSource = new SimpleBeanTargetSource();

	/** The name of the target bean. */
	@Nullable
	private String targetBeanName; // 目标bean的名字

	/** The cached singleton proxy. */
	@Nullable
	private Object proxy;


	/**
	 * Create a new ScopedProxyFactoryBean instance.
	 */
	public ScopedProxyFactoryBean() {
		// 实例化 -> 默认是使用的Cglib代理哦
		setProxyTargetClass(true);
	}


	/**
	 * Set the name of the bean that is to be scoped.
	 */
	public void setTargetBeanName(String targetBeanName) {
		// 设置targetName时 -> 同时创建TargetSource[AOP框架的一个概念]
		this.targetBeanName = targetBeanName;
		this.scopedTargetSource.setTargetBeanName(targetBeanName);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		// ❗️❗️❗️ 在setBeanFactory()中处理出proxy代理对象哦
		
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		ConfigurableBeanFactory cbf = (ConfigurableBeanFactory) beanFactory;

		// 1. 在SimpleBeanTargetSource中要想根据targetBeanName获取对应的bean,前提就是先注入BeanFactory吧
		this.scopedTargetSource.setBeanFactory(beanFactory);

		// 2. 手动创建Proxy代理对象 -> 硬编码必备的ProxyFactory
		ProxyFactory pf = new ProxyFactory();
		
		// 3. 老样子 -> 当前类的ProxyConfig的代理配置复制一份
		pf.copyFrom(this);
		
		// 4. 设置targetSource
		pf.setTargetSource(this.scopedTargetSource);

		Assert.notNull(this.targetBeanName, "Property 'targetBeanName' is required");
		Class<?> beanType = beanFactory.getType(this.targetBeanName);
		if (beanType == null) {
			throw new IllegalStateException("Cannot create scoped proxy for bean '" + this.targetBeanName +
					"': Target type could not be determined at the time of proxy creation.");
		}
		
		// 5. 如果没有指定必须使用Cglib代理 或者 指定了Cglib代理但@Scope代理的bean是一个接口 或者 指定了Cglib代理但@Scope代理的bean是私有的
		// 就检索出beanType实现的接口吧
		if (!isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {
			pf.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, cbf.getBeanClassLoader()));
		}

		// 6. 为代理对象添加 拦截器 -> ❗️❗️❗️❗️❗️❗️
		ScopedObject scopedObject = new DefaultScopedObject(cbf, this.scopedTargetSource.getTargetBeanName());
		pf.addAdvice(new DelegatingIntroductionInterceptor(scopedObject));

		// 7. 添加标志性接口 -> AopInfrastructureBean 标记以指示作用域代理本身不受自动代理的影响
		pf.addInterface(AopInfrastructureBean.class);

		// 8. 获取代理Proxy对象
		this.proxy = pf.getProxy(cbf.getBeanClassLoader());
	}


	@Override
	public Object getObject() {
		// ❗️❗️FactoryBean#getObject() -> 必须在setBeanFactory()中提前获取好proxy哦
		
		if (this.proxy == null) {
			throw new FactoryBeanNotInitializedException();
		}
		return this.proxy;
	}

	@Override
	public Class<?> getObjectType() {
		if (this.proxy != null) {
			return this.proxy.getClass();
		}
		return this.scopedTargetSource.getTargetClass();
	}

	@Override
	public boolean isSingleton() {
		// 默认是: 单例的代理对象
		return true;
	}

}
