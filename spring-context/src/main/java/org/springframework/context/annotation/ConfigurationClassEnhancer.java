/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.asm.Type;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.cglib.transform.ClassEmitterTransformer;
import org.springframework.cglib.transform.TransformingClassGenerator;
import org.springframework.lang.Nullable;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Enhances {@link Configuration} classes by generating a CGLIB subclass which
 * interacts with the Spring container to respect bean scoping semantics for
 * {@code @Bean} methods. Each such {@code @Bean} method will be overridden in
 * the generated subclass, only delegating to the actual {@code @Bean} method
 * implementation if the container actually requests the construction of a new
 * instance. Otherwise, a call to such an {@code @Bean} method serves as a
 * reference back to the container, obtaining the corresponding bean by name.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.0
 * @see #enhance
 * @see ConfigurationClassPostProcessor
 */
class ConfigurationClassEnhancer {
	// ❗️❗️❗️
	// 通过生成与 Spring 容器交互以尊重@Bean方法的 bean 范围语义的 CGLIB 子类来增强Configuration类。
	// 每个这样@Bean方法都将在生成的代理子类中被覆盖，只有在容器实际请求构造一个新实例时才委托给实际的@Bean方法实现。
	// 否则，对此类@Bean方法的调用将作为对容器的引用，通过名称获取相应的bean

	// The callbacks to use. Note that these callbacks must be stateless.
	// 提前创建好的要使用的回调。请注意，这些回调必须是无状态的。
	private static final Callback[] CALLBACKS = new Callback[] {
			new BeanMethodInterceptor(),
			new BeanFactoryAwareMethodInterceptor(),
			NoOp.INSTANCE
	};

	// 将CALLBACKS全部提前存入到CallbackFilter中
	private static final ConditionalCallbackFilter CALLBACK_FILTER = new ConditionalCallbackFilter(CALLBACKS);

	// 在FULL配置类中会额外添加一个BeanFactory的字段,形参名就是BEAN_FACTORY_FIELD
	private static final String BEAN_FACTORY_FIELD = "$$beanFactory";


	private static final Log logger = LogFactory.getLog(ConfigurationClassEnhancer.class);

	private static final SpringObjenesis objenesis = new SpringObjenesis();


	/**
	 * Loads the specified class and generates a CGLIB subclass of it equipped with
	 * container-aware callbacks capable of respecting scoping and other bean semantics.
	 * @return the enhanced subclass
	 */
	public Class<?> enhance(Class<?> configClass, @Nullable ClassLoader classLoader) {
		// 加载指定的类并生成它的 CGLIB 子类，该子类配备能够尊重范围和其他 bean 语义的容器感知回调。
		
		// 1. 是否实现 EnhancedConfiguration 接口 -- 忽略
		if (EnhancedConfiguration.class.isAssignableFrom(configClass)) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Ignoring request to enhance %s as it has " +
						"already been enhanced. This usually indicates that more than one " +
						"ConfigurationClassPostProcessor has been registered (e.g. via " +
						"<context:annotation-config>). This is harmless, but you may " +
						"want check your configuration and remove one CCPP if possible",
						configClass.getName()));
			}
			return configClass;
		}
		// 2. 生成代理类
		Class<?> enhancedClass = createClass(newEnhancer(configClass, classLoader));
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Successfully enhanced %s; enhanced class name is: %s",
					configClass.getName(), enhancedClass.getName()));
		}
		// 3. 返回代理类的class
		// 而实际代理类已经被加载到内存中去啦
		return enhancedClass;
	}

	/**
	 * Creates a new CGLIB {@link Enhancer} instance.
	 */
	private Enhancer newEnhancer(Class<?> configSuperClass, @Nullable ClassLoader classLoader) {
		// 创建一个新的 CGLIB Enhancer实例。
		
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(configSuperClass); // 超类 -- FULL配置类
		enhancer.setInterfaces(new Class<?>[] {EnhancedConfiguration.class}); // 扩展的接口 -- 继承BeanFactoryAware
		enhancer.setUseFactory(false);
		enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE); // 生成的Class命名策略
		enhancer.setStrategy(new BeanFactoryAwareGeneratorStrategy(classLoader)); // 主要是添加一个 public BeanFactory $$beanFactory = null; 的字段
		enhancer.setCallbackFilter(CALLBACK_FILTER);
		enhancer.setCallbackTypes(CALLBACK_FILTER.getCallbackTypes());
		return enhancer;
	}

	/**
	 * Uses enhancer to generate a subclass of superclass,
	 * ensuring that callbacks are registered for the new subclass.
	 */
	private Class<?> createClass(Enhancer enhancer) {
		// 使用增强器enhancer生成超类的子类，确保为新子类注册回调Callbacks。
		
		Class<?> subclass = enhancer.createClass();
		Enhancer.registerStaticCallbacks(subclass, CALLBACKS);
		return subclass;
	}


	/**
	 * Marker interface to be implemented by all @Configuration CGLIB subclasses.
	 * Facilitates idempotent behavior for {@link ConfigurationClassEnhancer#enhance}
	 * through checking to see if candidate classes are already assignable to it, e.g.
	 * have already been enhanced.
	 * <p>Also extends {@link BeanFactoryAware}, as all enhanced {@code @Configuration}
	 * classes require access to the {@link BeanFactory} that created them.
	 * <p>Note that this interface is intended for framework-internal use only, however
	 * must remain public in order to allow access to subclasses generated from other
	 * packages (i.e. user code).
	 */
	public interface EnhancedConfiguration extends BeanFactoryAware {
		// 由所有FULL配置类的CGLIB子类实现的标记接口。
		// 通过检查候选类是否已经可以分配给它，例如已经被增强，从而促进enhance的幂等行为。
		// 还扩展了BeanFactoryAware ，因为所有被代理的FULL配置类都需要访问创建它们的BeanFactory 。
	}


	/**
	 * Conditional {@link Callback}.
	 * @see ConditionalCallbackFilter
	 */
	private interface ConditionalCallback extends Callback {
		// 条件Callback 

		boolean isMatch(Method candidateMethod);
	}


	/**
	 * A {@link CallbackFilter} that works by interrogating {@link Callback Callbacks} in the order
	 * that they are defined via {@link ConditionalCallback}.
	 */
	private static class ConditionalCallbackFilter implements CallbackFilter {
		// 一个CallbackFilter -- 用来过滤Callback是否执行
		// 它按照通过ConfigurationClassEnhancer.ConditionalCallback定义的顺序询问Callbacks来工作。
		// 当前类在创建Enhancer使用到

		// 代理类中所有的Callbacks
		private final Callback[] callbacks;

		// Callback的类型
		private final Class<?>[] callbackTypes;
		
		public ConditionalCallbackFilter(Callback[] callbacks) {
			this.callbacks = callbacks;
			this.callbackTypes = new Class<?>[callbacks.length];
			for (int i = 0; i < callbacks.length; i++) {
				this.callbackTypes[i] = callbacks[i].getClass();
			}
		}

		// CallbackFilter的核心方法 -- 检查哪一个callback接受指定的method
		@Override
		public int accept(Method method) {
			for (int i = 0; i < this.callbacks.length; i++) {
				Callback callback = this.callbacks[i];
				// 1. 自定义的callback如果实现了ConditionalCallback接口,就调用其isMatch(method)检查方法是否匹配
				// 如果匹配就返回对应的callback的索引位置啊
				// 好处 -- Callback只需要实现ConditionalCallback,任何重写isMatch()在自己的逻辑中判断自己需要拦截的方法
				if (!(callback instanceof ConditionalCallback) || ((ConditionalCallback) callback).isMatch(method)) {
					return i;
				}
			}
			// 抛出
			throw new IllegalStateException("No callback available for method " + method.getName());
		}

		public Class<?>[] getCallbackTypes() {
			return this.callbackTypes;
		}
	}


	/**
	 * Custom extension of CGLIB's DefaultGeneratorStrategy, introducing a {@link BeanFactory} field.
	 * Also exposes the application ClassLoader as thread context ClassLoader for the time of
	 * class generation (in order for ASM to pick it up when doing common superclass resolution).
	 */
	private static class BeanFactoryAwareGeneratorStrategy extends
			ClassLoaderAwareGeneratorStrategy {
		// 主要是重写transform()方法
		// 在 transformer 中重写 end_class() 方法 -> 指定生成一个 public BeanFactory $$beanFactory = null 的字段
		// 主要是搭配 FULL配置类的代理类将实现EnhancedConfiguration接口做准备 -> 接受 BeanFactoryAware 接口

		public BeanFactoryAwareGeneratorStrategy(@Nullable ClassLoader classLoader) {
			super(classLoader);
		}

		@Override
		protected ClassGenerator transform(ClassGenerator cg) throws Exception {
			ClassEmitterTransformer transformer = new ClassEmitterTransformer() {
				@Override
				public void end_class() {
					declare_field(Constants.ACC_PUBLIC, BEAN_FACTORY_FIELD, Type.getType(BeanFactory.class), null);
					super.end_class();
				}
			};
			return new TransformingClassGenerator(cg, transformer);
		}

	}


	/**
	 * Intercepts the invocation of any {@link BeanFactoryAware#setBeanFactory(BeanFactory)} on
	 * {@code @Configuration} class instances for the purpose of recording the {@link BeanFactory}.
	 * @see EnhancedConfiguration
	 */
	private static class BeanFactoryAwareMethodInterceptor implements MethodInterceptor, ConditionalCallback {
		// 拦截对FULL配置类的代理类的任何BeanFactoryAware.setBeanFactory(BeanFactory)的调用，以记录BeanFactory 。

		// MethodInterceptor 增强器
		@Override
		@Nullable
		public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
			Field field = ReflectionUtils.findField(obj.getClass(), BEAN_FACTORY_FIELD);
			Assert.state(field != null, "Unable to find generated BeanFactory field");
			field.set(obj, args[0]);

			// Does the actual (non-CGLIB) superclass implement BeanFactoryAware?
			// If so, call its setBeanFactory() method. If not, just exit.
			if (BeanFactoryAware.class.isAssignableFrom(ClassUtils.getUserClass(obj.getClass().getSuperclass()))) {
				return proxy.invokeSuper(obj, args);
			}
			return null;
		}

		@Override
		public boolean isMatch(Method candidateMethod) {
			// ❗️❗️❗️
			// 必须匹配到是setBeanFactory()方法
			return isSetBeanFactory(candidateMethod);
		}

		public static boolean isSetBeanFactory(Method candidateMethod) {
			return (candidateMethod.getName().equals("setBeanFactory") &&
					candidateMethod.getParameterCount() == 1 &&
					BeanFactory.class == candidateMethod.getParameterTypes()[0] &&
					BeanFactoryAware.class.isAssignableFrom(candidateMethod.getDeclaringClass()));
		}
	}


	/**
	 * Intercepts the invocation of any {@link Bean}-annotated methods in order to ensure proper
	 * handling of bean semantics such as scoping and AOP proxying.
	 * @see Bean
	 * @see ConfigurationClassEnhancer
	 */
	private static class BeanMethodInterceptor implements MethodInterceptor, ConditionalCallback {
		// 拦截任何有@Bean的方法的调用 -- 以确保正确处理bean语义,例如scope/aop

		/**
		 * Enhance a {@link Bean @Bean} method to check the supplied BeanFactory for the
		 * existence of this bean object.
		 * @throws Throwable as a catch-all for any exception that may be thrown when invoking the
		 * super implementation of the proxied method i.e., the actual {@code @Bean} method
		 */
		@Override
		@Nullable
		public Object intercept(Object enhancedConfigInstance, Method beanMethod, Object[] beanMethodArgs,
					MethodProxy cglibMethodProxy) throws Throwable {
			// 增强@Bean方法以检查提供的 BeanFactory 是否存在此 bean 对象。

			// 1. 通过上面创建Enhancer可知,增强的代理配置类有实现BeanFactoryAware接口,并且有一个BeanFactory字段,字段名就是BEAN_FACTORY_FIELD
			ConfigurableBeanFactory beanFactory = getBeanFactory(enhancedConfigInstance);
			// 2. 确定@Bean注解的方法的beanName
			String beanName = BeanAnnotationHelper.determineBeanNameFor(beanMethod);

			// 3. 确定此 bean 是否是作用域代理 -- 如果需要Scope代理,获取对应的beanName
			if (BeanAnnotationHelper.isScopedProxy(beanMethod)) {
				String scopedBeanName = ScopedProxyCreator.getTargetBeanName(beanName);
				if (beanFactory.isCurrentlyInCreation(scopedBeanName)) {
					beanName = scopedBeanName;
				}
			}

			// To handle the case of an inter-bean method reference, we must explicitly check the
			// container for already cached instances.

			// First, check to see if the requested bean is a FactoryBean. If so, create a subclass
			// proxy that intercepts calls to getObject() and returns any cached bean instance.
			// This ensures that the semantics of calling a FactoryBean from within @Bean methods
			// is the same as that of referring to a FactoryBean within XML. See SPR-6602.
			// 要处理 bean 间方法引用的情况，我们必须显式检查容器中是否有已缓存的实例。
			// 首先，检查请求的 bean 是否是 FactoryBean。如果是这样，则创建一个子类代理来拦截对 getObject() 的调用并返回任何缓存的 bean 实例。这
			// 确保了在 @Bean 方法中调用 FactoryBean 的语义与在 XML 中引用 FactoryBean 的语义相同。见 SPR-6602。
			// 忽略~~~
			// 源码只有一行: (beanFactory.containsBean(beanName) && !beanFactory.isCurrentlyInCreation(beanName))
			if (factoryContainsBean(beanFactory, BeanFactory.FACTORY_BEAN_PREFIX + beanName) &&
					factoryContainsBean(beanFactory, beanName)) {
				Object factoryBean = beanFactory.getBean(BeanFactory.FACTORY_BEAN_PREFIX + beanName);
				if (factoryBean instanceof ScopedProxyFactoryBean) {
					// Scoped proxy factory beans are a special case and should not be further proxied
					// Scoped 代理工厂 bean 是一种特殊情况，不应进一步代理
				} else {
					// factoryBean是候选 FactoryBean - 继续增强
					return enhanceFactoryBean(factoryBean, beanMethod.getReturnType(), beanFactory, beanName);
				}
			}

			// ❗️❗️❗️
			// 如果容器是想调用@bean注解的方法来实例化然后注册bean（即通过 getBean() 调用）->
			// 那么就调用超类即Full配置类的实际方法来帮助创建 bean 实例。
			// isCurrentlyInvokedFactoryMethod(beanMethod) -- 就是检查当前ioc容器创建的bean是否就是@Bean方法导入的
			// 比如 ioc 容器要创建@Bean对应的user对象,就需要调用user()方法来返回User
			// @Configuration
			// class ConfigClass{
			//	@Bean
			//	 public User user(){
			//		return new user();
			//	}
			// }
			if (isCurrentlyInvokedFactoryMethod(beanMethod)) {
				// The factory is calling the bean method in order to instantiate and register the bean
				// (i.e. via a getBean() call) -> invoke the super implementation of the method to actually
				// create the bean instance.
				if (logger.isInfoEnabled() &&
						BeanFactoryPostProcessor.class.isAssignableFrom(beanMethod.getReturnType())) {
					logger.info(String.format("@Bean method %s.%s is non-static and returns an object " +
									"assignable to Spring's BeanFactoryPostProcessor interface. This will " +
									"result in a failure to process annotations such as @Autowired, " +
									"@Resource and @PostConstruct within the method's declaring " +
									"@Configuration class. Add the 'static' modifier to this method to avoid " +
									"these container lifecycle issues; see @Bean javadoc for complete details.",
							beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName()));
				}
				// 直接调用超类即Full配置类的@Bean的方法即可
				return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
			}
			
			// ❗️❗️❗️
			// 而对于,当前ioc容器创建的bean是一个@Bean工厂方法,其中调用了另一个@Bean方法,为了保证bean对象在ioc的的唯一值
			// 调用的另一个@Bean方法不应该被直接调用,而是尝试从ioc中拿出来
			// 比如: ioc容器在创建car对象时,会调用car()方法试图返回car(),但在car()中有调用user()方法 -> 由于user()也是被@Bean注解的
			// 那么就不应该直接去直接user(),而是去看ioc容器有没有对应以及使用user()创建好的user()对象
			// @Configuration
			// class ConfigClass{
			//	@Bean
			//	 public User user(){
			//		return new user();
			//	}
			//  @Bean Car car(){
			//  	return new Car(user());
			//  }
			// }

			return resolveBeanReference(beanMethod, beanMethodArgs, beanFactory, beanName);
		}

		// 解析Bean的引用问题
		// 以上面为例:创建car的bean,引用到user() -> 使得这里进入,并且beanName=user
		// beanMethod就是user()\beanMethodArgs为空
		private Object resolveBeanReference(Method beanMethod, Object[] beanMethodArgs,
				ConfigurableBeanFactory beanFactory, String beanName) {

			// 用户（即不是ioc）通过直接或间接调用 bean 方法来请求此 bean。
			// 在某些自动装配场景中，bean 可能已经被标记为“正在创建中”；
			// 如果是这样，暂时将创建状态设置为 false 以避免异常
			boolean alreadyInCreation = beanFactory.isCurrentlyInCreation(beanName);
			try {
				// 如果user已经在创建中
				if (alreadyInCreation) {
					// 将其user控制为不在创建中
					beanFactory.setCurrentlyInCreation(beanName, false);
				}
				// 对应的方法是否有形参
				boolean useArgs = !ObjectUtils.isEmpty(beanMethodArgs);
				// 使用了形参,并且@Bean方法导入的bean是单例的
				if (useArgs && beanFactory.isSingleton(beanName)) {
					// Stubbed null arguments just for reference purposes,
					// expecting them to be autowired for regular singleton references?
					// A safe assumption since @Bean singleton arguments cannot be optional...
					// 仅用于引用的目的的存在空参数，期望它们为常规单例引用自动装配？
					// 一个安全的假设，因为 @Bean 方法的单例参数不能是可选的......
					// ❗️❗️❗️ why: 也就是说如果引用的@Bean方法有形参,而你调用的时候传入的形参有null值,那说明就不应该使用参数
					// 因为@Bena的方法的形参应该都是有值的,它的形参也是@Autowrite进来的哦,你都传一个null就说明你这是想从ioc容器中取出来
					for (Object arg : beanMethodArgs) {
						if (arg == null) {
							useArgs = false;
							break;
						}
					}
				}
				// 从BeanFactory中拿去对应的user对象
				Object beanInstance = (useArgs ? beanFactory.getBean(beanName, beanMethodArgs) :
						beanFactory.getBean(beanName));
				// 取出来的bean对象和@Bean方法的user()返回值类型不一样 [有问题]
				if (!ClassUtils.isAssignableValue(beanMethod.getReturnType(), beanInstance)) {
					// Detect package-protected NullBean instance through equals(null) check
					if (beanInstance.equals(null)) {
						if (logger.isDebugEnabled()) {
							logger.debug(String.format("@Bean method %s.%s called as bean reference " +
									"for type [%s] returned null bean; resolving to null value.",
									beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName(),
									beanMethod.getReturnType().getName()));
						}
						beanInstance = null;
					}
					else {
						String msg = String.format("@Bean method %s.%s called as bean reference " +
								"for type [%s] but overridden by non-compatible bean instance of type [%s].",
								beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName(),
								beanMethod.getReturnType().getName(), beanInstance.getClass().getName());
						try {
							BeanDefinition beanDefinition = beanFactory.getMergedBeanDefinition(beanName);
							msg += " Overriding bean of same name declared in: " + beanDefinition.getResourceDescription();
						}
						catch (NoSuchBeanDefinitionException ex) {
							// Ignore - simply no detailed message then.
						}
						throw new IllegalStateException(msg);
					}
				}
				// 拿到当前正在创建对象的工厂方法,也就是上面car()方法,注册一下依赖关系,依旧是 car的bean 依赖 user的bean
				Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
				if (currentlyInvoked != null) {
					String outerBeanName = BeanAnnotationHelper.determineBeanNameFor(currentlyInvoked);
					beanFactory.registerDependentBean(beanName, outerBeanName);
				}
				// 返回提前通过user()创建好加入到ioc容器中的user对象给car(User user)使用
				return beanInstance;
			}
			finally {
				if (alreadyInCreation) {
					beanFactory.setCurrentlyInCreation(beanName, true);
				}
			}
		}

		@Override
		public boolean isMatch(Method candidateMethod) {
			// 可以拦截的方法 -- 非Object声明的方法,并且不属于setBeanFactory()方法\并且被@Bean将方法注释啦
			// 则当前Callback也就是MethodInterceptor的Interceptor()方法将会被执行哦
			return (candidateMethod.getDeclaringClass() != Object.class &&
					!BeanFactoryAwareMethodInterceptor.isSetBeanFactory(candidateMethod) &&
					BeanAnnotationHelper.isBeanAnnotated(candidateMethod));
		}

		// 无法直接调用getBeanFactory()
		// 因为FULL配置类本身没有BeanFactory字段,是Cglib帮忙创建的一个字段
		// 所以只可以反射拉取
		private ConfigurableBeanFactory getBeanFactory(Object enhancedConfigInstance) {
			Field field = ReflectionUtils.findField(enhancedConfigInstance.getClass(), BEAN_FACTORY_FIELD);
			Assert.state(field != null, "Unable to find generated bean factory field");
			Object beanFactory = ReflectionUtils.getField(field, enhancedConfigInstance);
			Assert.state(beanFactory != null, "BeanFactory has not been injected into @Configuration class");
			Assert.state(beanFactory instanceof ConfigurableBeanFactory,
					"Injected BeanFactory is not a ConfigurableBeanFactory");
			return (ConfigurableBeanFactory) beanFactory;
		}

		/**
		 * Check the BeanFactory to see whether the bean named <var>beanName</var> already
		 * exists. Accounts for the fact that the requested bean may be "in creation", i.e.:
		 * we're in the middle of servicing the initial request for this bean. From an enhanced
		 * factory method's perspective, this means that the bean does not actually yet exist,
		 * and that it is now our job to create it for the first time by executing the logic
		 * in the corresponding factory method.
		 * <p>Said another way, this check repurposes
		 * {@link ConfigurableBeanFactory#isCurrentlyInCreation(String)} to determine whether
		 * the container is calling this method or the user is calling this method.
		 * @param beanName name of bean to check for
		 * @return whether <var>beanName</var> already exists in the factory
		 */
		private boolean factoryContainsBean(ConfigurableBeanFactory beanFactory, String beanName) {
			return (beanFactory.containsBean(beanName) && !beanFactory.isCurrentlyInCreation(beanName));
		}

		/**
		 * Check whether the given method corresponds to the container's currently invoked
		 * factory method. Compares method name and parameter types only in order to work
		 * around a potential problem with covariant return types (currently only known
		 * to happen on Groovy classes).
		 */
		private boolean isCurrentlyInvokedFactoryMethod(Method method) {
			Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
			return (currentlyInvoked != null && method.getName().equals(currentlyInvoked.getName()) &&
					Arrays.equals(method.getParameterTypes(), currentlyInvoked.getParameterTypes()));
		}

		/**
		 * Create a subclass proxy that intercepts calls to getObject(), delegating to the current BeanFactory
		 * instead of creating a new instance. These proxies are created only when calling a FactoryBean from
		 * within a Bean method, allowing for proper scoping semantics even when working against the FactoryBean
		 * instance directly. If a FactoryBean instance is fetched through the container via &-dereferencing,
		 * it will not be proxied. This too is aligned with the way XML configuration works.
		 */
		private Object enhanceFactoryBean(Object factoryBean, Class<?> exposedType,
				ConfigurableBeanFactory beanFactory, String beanName) {

			try {
				Class<?> clazz = factoryBean.getClass();
				boolean finalClass = Modifier.isFinal(clazz.getModifiers());
				boolean finalMethod = Modifier.isFinal(clazz.getMethod("getObject").getModifiers());
				if (finalClass || finalMethod) {
					if (exposedType.isInterface()) {
						if (logger.isTraceEnabled()) {
							logger.trace("Creating interface proxy for FactoryBean '" + beanName + "' of type [" +
									clazz.getName() + "] for use within another @Bean method because its " +
									(finalClass ? "implementation class" : "getObject() method") +
									" is final: Otherwise a getObject() call would not be routed to the factory.");
						}
						return createInterfaceProxyForFactoryBean(factoryBean, exposedType, beanFactory, beanName);
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Unable to proxy FactoryBean '" + beanName + "' of type [" +
									clazz.getName() + "] for use within another @Bean method because its " +
									(finalClass ? "implementation class" : "getObject() method") +
									" is final: A getObject() call will NOT be routed to the factory. " +
									"Consider declaring the return type as a FactoryBean interface.");
						}
						return factoryBean;
					}
				}
			}
			catch (NoSuchMethodException ex) {
				// No getObject() method -> shouldn't happen, but as long as nobody is trying to call it...
			}

			return createCglibProxyForFactoryBean(factoryBean, beanFactory, beanName);
		}

		private Object createInterfaceProxyForFactoryBean(Object factoryBean, Class<?> interfaceType,
				ConfigurableBeanFactory beanFactory, String beanName) {

			return Proxy.newProxyInstance(
					factoryBean.getClass().getClassLoader(), new Class<?>[] {interfaceType},
					(proxy, method, args) -> {
						if (method.getName().equals("getObject") && args == null) {
							return beanFactory.getBean(beanName);
						}
						return ReflectionUtils.invokeMethod(method, factoryBean, args);
					});
		}

		private Object createCglibProxyForFactoryBean(Object factoryBean,
				ConfigurableBeanFactory beanFactory, String beanName) {

			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(factoryBean.getClass());
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
			enhancer.setCallbackType(MethodInterceptor.class);

			// Ideally create enhanced FactoryBean proxy without constructor side effects,
			// analogous to AOP proxy creation in ObjenesisCglibAopProxy...
			Class<?> fbClass = enhancer.createClass();
			Object fbProxy = null;

			if (objenesis.isWorthTrying()) {
				try {
					fbProxy = objenesis.newInstance(fbClass, enhancer.getUseCache());
				}
				catch (ObjenesisException ex) {
					logger.debug("Unable to instantiate enhanced FactoryBean using Objenesis, " +
							"falling back to regular construction", ex);
				}
			}

			if (fbProxy == null) {
				try {
					fbProxy = ReflectionUtils.accessibleConstructor(fbClass).newInstance();
				}
				catch (Throwable ex) {
					throw new IllegalStateException("Unable to instantiate enhanced FactoryBean using Objenesis, " +
							"and regular FactoryBean instantiation via default constructor fails as well", ex);
				}
			}

			((Factory) fbProxy).setCallback(0, (MethodInterceptor) (obj, method, args, proxy) -> {
				if (method.getName().equals("getObject") && args.length == 0) {
					return beanFactory.getBean(beanName);
				}
				return proxy.invoke(factoryBean, args);
			});

			return fbProxy;
		}
	}

}
