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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
		// 逻辑:
		// 首先指定 BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()
		// 执行的优先级: 形参传递进来的beanFactoryPostProcessors > spring容器中的带有PriorityOrdered接口的BeanDefinitionRegistryPostProcessor
		// > spring容器中的带有Ordered接口的BeanDefinitionRegistryPostProcessor > spring容器中的实现了@Order/@Proprity接口的的BeanDefinitionRegistryPostProcessor
		// > spring容器中的实现了无特殊情况的的BeanDefinitionRegistryPostProcessor
		// 同样:按照上面的优先级执行 BeanFactoryPostProcessor#postProcessBeanFactory()
		// note: 注意一下,对于同样级别的,比如都是用@Order接口且value值大小一样时,执行顺序是不定的
		

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 首先引用 BeanDefinitionRegistryPostProcessors 的实现类
		Set<String> processedBeans = new HashSet<>(); // 已经处理的BeanName集合

		// 只有此 beanFactory 是 BeanDefinitionRegistry才能执行BeanDefinitionRegistryPostProcessor，才能修改Bean的定义嘛~
		if (beanFactory instanceof BeanDefinitionRegistry) { // 一般为true
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 此处安放了两个容器，一个装载普通的BeanFactoryPostProcessor
			// 另外一个装载和Bean定义有关的 BeanDefinitionRegistryPostProcessor
			// 另外都是LinkedList，所以执行顺序和set进去的顺序是保持一样的
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>(); // 常规的BeanFactoryPostProcessor集合
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>(); // 特殊的BeanDefinitionRegistryPostProcessor集合

			// 已有的注册的beanFactoryPostProcessors -- 这里通常是用户手动提供的一些，优先级高
			// 这里是我们自己的set进去的，若没set，这里就是空(若是Spring容器里的，下面会处理，见下面)
			// 从此处可以看出，我们手动set进去的，最最最最优先执行的
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 检查已注册的BeanFactoryProcessors中是否有特殊的BeanDefinitionRegistryPostProcessor
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;

					// 不难看出 --- BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry 执行的顺序是比较早的，允许修改BeanDefinition注册中心的信息
					// 执行完之后，然后把它缓冲起来了，放在了registryProcessors里，因为后续还有一个 BeanDefinitionRegistryPostProcessor#postProcessBeanFactory 需要执行
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				} else {
					// 缓冲起来常规的处理器
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 前面执行完提前通过形参注入的PostProcessor中的BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(registry)方法
			// 然后存入到regularPostProcessors/registryProcessors容器中 --> 后续再统一执行,但因为他们在首部所以也会比Spring容器里的BeanFactoryPostProcessor执行的早哦
			// 接下来，就是去执行Spring容器里面的一些PostProcessor了。他们顺序doc里也写得很清楚：
			// 先执行实现了PriorityOrdered接口的，然后是Ordered接口的，最后执行剩下的
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 第一步，调用所有的 BeanDefinitionRegistryPostProcessors 且实现接口 PriorityOrdered 的
			// 先从容器中拿出来所有的BeanDefinitionRegistryPostProcessor 然后先执行PriorityOrdered
			// 本例中有一个这个类型的处理器：ConfigurationClassPostProcessor（显然是处理@Configuration这种Bean的）
			// 至于这个Bean是什么时候注册进去的，前面有。在loadBeanDefinitions()初始化AnnotatedBeanDefinitionReader的时候调用的AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry)方法的时候，默认注册了8个Bean
			String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			for (String ppName : postProcessorNames) {
				// 是否实现了 PriorityOrdered 接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName); // 添加到已经处理的集合中
				}
			}
			// 排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 将从Spring容器中找到的有PriorityOrdered接口的currentRegistryProcessors注册到待执行的总的registryProcessors的尾部
			registryProcessors.addAll(currentRegistryProcessors);
			// registryProcessors 是 BeanDefinitionRegistryPostProcessor 
			// 同样的Spring容器中的BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()也需要立即去执行掉
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 清空
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 第二步，引用BeanDefinitionRegistryPostProcessors实现了Ordered接口的
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 是否实现了 Ordered 接口
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			// 核心 --- 开始引用
			// 这个方法很简单，就是吧currentRegistryProcessors中的BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()
			// 里面所有的处理器for循环一个个的执行掉(本处只有ConfigurationClassPostProcessor，详见我的另一篇专门博文讲解)
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 此处把当前持有的执行对象给清空了，需要注意。以方便装载后续执行的处理器们
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 最后，引用所有BeanDefinitionRegistryPostProcessors
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						// 没有实现任何接口的BeanDefinitionRegistryPostProcessor
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 前面BeanDefinitionRegistryPostProcessor使用完毕后
			// 现在，才开始引用 BeanFactoryPostProcessors
			// 现在，这里很明显：去执行BeanDefinitionRegistryPostProcessor的postProcessBeanFactory方法
			// 以及 顶层接口BeanFactoryPostProcessor的postProcessBeanFactory方法
			// 我们当前环境regularPostProcessors长度为0.registryProcessors有一个解析@Configuration的处理器
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			// Invoke factory processors registered with the context instance.
			// 若是普通的Bean工厂，就直接执行set进来的后置处理器即可（因为容器里就没有其它Bean定义了）
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 上面是执行的 BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry + BeanDefinitionRegistryPostProcessor#postProcessBeanFactory
		// 下面是执行的 BeanFactoryPostProcessorpostProcessBeanFactory

		// 上面9个Bean，我们知道 也就ConfigurationClassPostProcessor是实现了此接口的。因此本环境下，只有它了，并且它在上面已经执行完啦


		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 和上面不同，这里取出来的是 BeanFactoryPostProcessor超类 ，上面取出来的是 BeanDefinitionRegistryPostProcessor子类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
				// 跳过 - 第一阶段已经处理过的bean
			} else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();

		//至此，invokeBeanFactoryPostProcessors(beanFactory)这一步就完成了。这一步主要做了：
		//
		//执行了BeanDefinitionRegistryPostProcessor（此处只有ConfigurationClassPostProcessor）
		//执行了BeanFactoryPostProcessor
		//完成了@Configuration配置文件的解析，并且把扫描到的、配置的Bean定义信息都加载进容器里
		//Full模式下，完成了对@Configuration配置文件的加强，使得管理Bean依赖关系更加的方便了
	}


	public static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		/*
		如果使用的是AbstractApplicationContext（实现了ApplicationContext）的实现，则通过如下规则指定顺序。
		PriorityOrdered > Ordered > 无实现接口的 > 内部Bean后处理器（实现了MergedBeanDefinitionPostProcessor接口的是内部Bean PostProcessor，将在最后且无序注册）
		 */

		// 从Bean定义中提取出BeanPostProcessor类型的Bean，显然，最初的6个bean，有三个是BeanPostProcessor：
		// AutowiredAnnotationBeanPostProcessor  RequiredAnnotationBeanPostProcessor  CommonAnnotationBeanPostProcessor
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// 不难看出一点：这里是允许beanFactory提前在容器刷新前添加一些BeanPostProcessor
		// 此处有点意思了，向beanFactory又add了一个BeanPostProcessorChecker，并且此事后总数设置为了getBeanPostProcessorCount和addBeanPostProcessor的总和（+1表示后续添加的）
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 此处注意：第一个参数beanPostProcessorTargetCount表示的是处理器的总数，总数（包含两个位置离的，用于后面的校验）
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 同样的 先按优先级，归类了BeanPostProcessor
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>(); // PriorityOrdered 级别的BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>(); // 内部Bean后处理器，即 MergedBeanDefinitionPostProcessor
		List<String> orderedPostProcessorNames = new ArrayList<>(); // Ordered 的后置处理器
		List<String> nonOrderedPostProcessorNames = new ArrayList<>(); //  无实现接口的后置处理器
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 实现 PriorityOrdered 的 BeanPostProcessor
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				// 内部Bean的即实现MergedBeanDefinitionPostProcessor的 BeanPostProcessor
				// MergedBeanDefinitionPostProcessor则是在合并处理Bean定义的时候的回调。-- 这个东东按我的理解也基本是框架内部使用的，用户不用管
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			// 实现 Ordered接口 的 BeanPostProcessor
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			// 无实现任何接口的 BeanPostProcessor
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// 接下来就是将BeanPostProcessor注册到BeanFactory中

		// 第一步 - First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// 第二步 - Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// 第三步 - Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// 第四步 -  re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 将ApplicationListenerDetector重新添加进去，保证是最后的位置
		// 最后此处需要注意的是：Spring还给我们注册了一个Bean的后置处理器：ApplicationListenerDetector  检测器的作用：用来检查所有得ApplicationListener
		// 有的人就想问了：之前不是注册过了吗？怎么这里又注册一次呢？其实上面的doc里面说得很清楚？
		// Re-register重新注册这个后置处理器。把它移动到处理器连条的最后面，最后执行（小技巧是：先remove，然后执行add操作~~~ 自己可以点进addBeanPostProcessor源码可以看到这个小技巧）
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// 是否有必要进行排序
		if (postProcessors.size() <= 1) {
			return;
		}
		// 查找比较器
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			// 实际上在注解Spring中获取的是AnnotationAwareOrderComparator.INSTANCE
			// 源码展示:
			// 创建 AnnotatedBeanDefinitionReader 中调用 AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry)
			// 将触发下面两行代码
			// if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
			//		beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
			//	}
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			// 没有设置比较器时，使用默认的比较器
			comparatorToUse = OrderComparator.INSTANCE;
		}
		// 对集合进行排序
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {
		// 递归处理 BeanDefinitionRegistryPostProcessor
		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// 递归处理 BeanFactoryPostProcessor
		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {
		// 遍历注册到beanFactory的BeanPostProcessor容器中
		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			// 如果是非框架角色的bean就需要检查BeanPostProcessor数量，BeanFactory中已经注册的BeanPostProcessor数量 小于 创建BeanPostProcessorChecker时beanPostProcessorTargetCount 就报错
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			// 是否为框架内的bean，主要是bena的角色
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
