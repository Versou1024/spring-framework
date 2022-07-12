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

package org.springframework.transaction.interceptor;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

import io.vavr.control.Try;
import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Base class for transactional aspects, such as the {@link TransactionInterceptor}
 * or an AspectJ aspect.
 *
 * <p>This enables the underlying Spring transaction infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling methods in this class in the correct order.
 *
 * <p>If no transaction name has been specified in the {@link TransactionAttribute},
 * the exposed name will be the {@code fully-qualified class name + "." + method name}
 * (by default).
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link PlatformTransactionManager} or
 * {@link ReactiveTransactionManager} implementation will perform the actual transaction
 * management, and a {@link TransactionAttributeSource} (e.g. annotation-based) is used
 * for determining transaction definitions for a particular class or method.
 *
 * <p>A transaction aspect is serializable if its {@code TransactionManager} and
 * {@code TransactionAttributeSource} are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stéphane Nicoll
 * @author Sam Brannen
 * @author Mark Paluch
 * @since 1.1
 * @see PlatformTransactionManager
 * @see ReactiveTransactionManager
 * @see #setTransactionManager
 * @see #setTransactionAttributes
 * @see #setTransactionAttributeSource
 */
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {

	// 命名
	// TransactionAspectSupport = Transaction Aspect Support
	
	// 作用:
	// 为@Transactional的拦截器提供工具方法


	// 用于存储默认的TransactionManager的key
	private static final Object DEFAULT_TRANSACTION_MANAGER_KEY = new Object();

	/**
	 * Vavr library present on the classpath?
	 */
	private static final boolean vavrPresent = ClassUtils.isPresent(
			"io.vavr.control.Try", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Reactive Streams API present on the classpath?
	 */
	private static final boolean reactiveStreamsPresent =
			ClassUtils.isPresent("org.reactivestreams.Publisher", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Holder to support the {@code currentTransactionStatus()} method,
	 * and to support communication between different cooperating advices
	 * (e.g. before and after advice) if the aspect involves more than a
	 * single method (as will be the case for around advice).
	 */
	private static final ThreadLocal<TransactionInfo> transactionInfoHolder = new NamedThreadLocal<>("Current aspect-driven transaction");
	// 当前事务的TransactionInfo捆绑到线程上


	/**
	 * Subclasses can use this to return the current TransactionInfo.
	 * Only subclasses that cannot handle all operations in one method,
	 * such as an AspectJ aspect involving distinct before and after advice,
	 * need to use this mechanism to get at the current TransactionInfo.
	 * An around advice such as an AOP Alliance MethodInterceptor can hold a
	 * reference to the TransactionInfo throughout the aspect method.
	 * <p>A TransactionInfo will be returned even if no transaction was created.
	 * The {@code TransactionInfo.hasTransaction()} method can be used to query this.
	 * <p>To find out about specific transaction characteristics, consider using
	 * TransactionSynchronizationManager's {@code isSynchronizationActive()}
	 * and/or {@code isActualTransactionActive()} methods.
	 * @return the TransactionInfo bound to this thread, or {@code null} if none
	 * @see TransactionInfo#hasTransaction()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isSynchronizationActive()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
	 */
	@Nullable
	protected static TransactionInfo currentTransactionInfo() throws NoTransactionException {
		// 注意此方法是个静态方法  并且是protected的  说明只有子类能够调用，外部并不可以~~~
		return transactionInfoHolder.get();
	}

	/**
	 * Return the transaction status of the current method invocation.
	 * Mainly intended for code that wants to set the current transaction
	 * rollback-only but not throw an application exception.
	 * @throws NoTransactionException if the transaction info cannot be found,
	 * because the method was invoked outside an AOP invocation context
	 */
	public static TransactionStatus currentTransactionStatus() throws NoTransactionException {
		// 外部调用此Static方法，可议获取到当前事务的状态  从而甚至可议手动来提交、回滚事务
		TransactionInfo info = currentTransactionInfo();
		if (info == null || info.transactionStatus == null) {
			throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
		}
		return info.transactionStatus;
	}


	protected final Log logger = LogFactory.getLog(getClass());

	// 在传统的平台式TransactionalManager中,ReactiveAdapterRegistry是一个null值
	@Nullable
	private final ReactiveAdapterRegistry reactiveAdapterRegistry;

	@Nullable
	private String transactionManagerBeanName;

	// 关键 --
	// 事务管理器对象
	@Nullable
	private TransactionManager transactionManager;

	// 关键 --
	// 事务属性源头 -> 用来从类或方法上查找事务属性
	// 一般都是 AnnotationTransactionAttributeSource -> 即将@Transaction解析为相应的TransactionAttribute对象
	@Nullable
	private TransactionAttributeSource transactionAttributeSource; 
	
	// 由于当前TransactionAspectSupport的实现类TransactionInterceptor是被ProxyTransactionManagementConfiguration通过@Bean导入的
	// 所有将加入到ioc容器中,对应的BeanFactoryAware#setBeanFactory()将会生效 -> 因此BeanFactory不会为空
	@Nullable
	private BeanFactory beanFactory;

	// 缓存
	// key 为 TransactionalManager的特征信息,可以是当前TransactionManager在ioc容器的beanName
	// value 就是对应的TransactionManager
	private final ConcurrentMap<Object, TransactionManager> transactionManagerCache = new ConcurrentReferenceHashMap<>(4);

	private final ConcurrentMap<Method, ReactiveTransactionSupport> transactionSupportCache = new ConcurrentReferenceHashMap<>(1024);


	protected TransactionAspectSupport() {
		if (reactiveStreamsPresent) {
			this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
		}
		else {
			this.reactiveAdapterRegistry = null;
		}
	}


	/**
	 * Specify the name of the default transaction manager bean.
	 * <p>This can either point to a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	public void setTransactionManagerBeanName(@Nullable String transactionManagerBeanName) {
		this.transactionManagerBeanName = transactionManagerBeanName;
	}

	/**
	 * Return the name of the default transaction manager bean.
	 */
	@Nullable
	protected final String getTransactionManagerBeanName() {
		return this.transactionManagerBeanName;
	}

	/**
	 * Specify the <em>default</em> transaction manager to use to drive transactions.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 * <p>The default transaction manager will be used if a <em>qualifier</em>
	 * has not been declared for a given transaction or if an explicit name for the
	 * default transaction manager bean has not been specified.
	 * @see #setTransactionManagerBeanName
	 */
	public void setTransactionManager(@Nullable TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Return the default transaction manager, or {@code null} if unknown.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	@Nullable
	public TransactionManager getTransactionManager() {
		return this.transactionManager;
	}

	/**
	 * Set properties with method names as keys and transaction attribute
	 * descriptors (parsed via TransactionAttributeEditor) as values:
	 * e.g. key = "myMethod", value = "PROPAGATION_REQUIRED,readOnly".
	 * <p>Note: Method names are always applied to the target class,
	 * no matter if defined in an interface or the class itself.
	 * <p>Internally, a NameMatchTransactionAttributeSource will be
	 * created from the given properties.
	 * @see #setTransactionAttributeSource
	 * @see TransactionAttributeEditor
	 * @see NameMatchTransactionAttributeSource
	 */
	public void setTransactionAttributes(Properties transactionAttributes) {
		NameMatchTransactionAttributeSource tas = new NameMatchTransactionAttributeSource();
		tas.setProperties(transactionAttributes);
		this.transactionAttributeSource = tas;
	}

	/**
	 * Set multiple transaction attribute sources which are used to find transaction
	 * attributes. Will build a CompositeTransactionAttributeSource for the given sources.
	 * @see CompositeTransactionAttributeSource
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSources(TransactionAttributeSource... transactionAttributeSources) {
		this.transactionAttributeSource = new CompositeTransactionAttributeSource(transactionAttributeSources);
	}

	/**
	 * Set the transaction attribute source which is used to find transaction
	 * attributes. If specifying a String property value, a PropertyEditor
	 * will create a MethodMapTransactionAttributeSource from the value.
	 * @see TransactionAttributeSourceEditor
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSource(@Nullable TransactionAttributeSource transactionAttributeSource) {
		this.transactionAttributeSource = transactionAttributeSource;
	}

	/**
	 * Return the transaction attribute source.
	 */
	@Nullable
	public TransactionAttributeSource getTransactionAttributeSource() {
		return this.transactionAttributeSource;
	}

	/**
	 * Set the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Override
	public void setBeanFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Nullable
	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Check that required properties were set.
	 */
	@Override
	public void afterPropertiesSet() {
		if (getTransactionManager() == null && this.beanFactory == null) {
			throw new IllegalStateException(
					"Set the 'transactionManager' property or make sure to run within a BeanFactory " +
					"containing a TransactionManager bean!");
		}
		if (getTransactionAttributeSource() == null) {
			throw new IllegalStateException(
					"Either 'transactionAttributeSource' or 'transactionAttributes' is required: " +
					"If there are no transactional methods, then don't use a transaction aspect.");
		}
	}


	/**
	 * General delegate for around-advice-based subclasses, delegating to several other template
	 * methods on this class. Able to handle {@link CallbackPreferringPlatformTransactionManager}
	 * as well as regular {@link PlatformTransactionManager} implementations and
	 * {@link ReactiveTransactionManager} implementations for reactive return types.
	 * @param method the Method being invoked
	 * @param targetClass the target class that we're invoking the method on
	 * @param invocation the callback to use for proceeding with the target invocation
	 * @return the return value of the method, if any
	 * @throws Throwable propagated from the target invocation
	 */
	@Nullable
	protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
			final InvocationCallback invocation) throws Throwable {
		// ❗ 将目标targetClass的method在事务下执行

		// 1. 99%的情况就是: AnnotationTransactionAttributeSource -> 专门用来将@Transaction注解分析为TransactionAttribute
		TransactionAttributeSource tas = getTransactionAttributeSource();

		// 2. 获取@Transaction注解对应形成的TransactionAttribute
		//		1. 默认：不允许对非公有的方法进行事务增强，如果是非公有方法将直接return null
		//		2. 检查目标方法上是否有@Transactional注解,有的话,解析为TransactionAttribute后返回 ~~ 50%
		//		3. 检查目标方法的生命类上是否有@Transactional注解,有的话,解析为TransactionAttribute后返回 ~~ 50%
		final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
		// 3. 这个厉害了：就是去找到一个合适的事务管理器（具体策略详见方法~~~) -- 重点[这里一般都是DataSourceTransactionManager]
		//❗️ 说实话,一般在项目中我们都是在数据库的配置类首页通过@Bean引入一个DataSource,然后使用一个@Bean方法区生成一个DataSourceTransactionManager来管理事务
		// 也就是说,实际上大多数情况都是我们向项目中注册一个DataSourceTransactionManager的,但是需要注意如果容器中存在两个TransactionManager,并且在@Transactional的value属性没有设置值
		// 即没有指定TransactionalManager的beanName就会报出异常的哦❗️
		//		1. 如果@Transactional的value/transactionManager属性值不为空，去ioc容器中查找对应beanName且type为TransactionManager的事务管理器并返回
		//		2. 如果用户通过实现TransactionManagementConfigurer接口并定制化返回一个TransactionManager，那就以这个为准
		//		3. 上述1和2都失败，那么就去ioc容器加载唯一的一个TransactionManager类型的bean出来，如果找不到就只有报错 ~~ 99%的情况
		final TransactionManager tm = determineTransactionManager(txAttr);

		// 4. 99%的情况下面的if条件都是不成立的,因为reactiveAdapterRegistry为null值,且tm一般都是PlatformTransactionManager
		if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager) {
			ReactiveTransactionSupport txSupport = this.transactionSupportCache.computeIfAbsent(method, key -> {
				if (KotlinDetector.isKotlinType(method.getDeclaringClass()) && KotlinDelegate.isSuspend(method)) {
					throw new TransactionUsageException(
							"Unsupported annotated transaction on suspending function detected: " + method +
							". Use TransactionalOperator.transactional extensions instead.");
				}
				ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(method.getReturnType());
				if (adapter == null) {
					throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type: " +
							method.getReturnType());
				}
				return new ReactiveTransactionSupport(adapter);
			});
			return txSupport.invokeWithinTransaction(
					method, targetClass, invocation, txAttr, (ReactiveTransactionManager) tm);
		}

		// 5. 强转为 PlatformTransactionManager
		PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
		
		// 6. 拿到目标方法唯一标识
		// 		1. 调用当前类的protected的methodIdentification(method, targetClass)，默认返回null，子类没有重写，因此会继续执行2
		//		2. 调用txAttr.getDescriptor() -> 一般就是method的全限定类名 ~~ 99%的情况, 类.方法 如 com.sdk.service.UserServiceImpl.save
		final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

		// 7. 90%的情况都是DataSourceTransactionManager
		if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
			
			// 7.1 ❗️❗️❗️❗️❗️❗️  核心复杂的方法 -> 涉及到但不限于事务传播特性\如何创建事务对象\如何获取jdbc连接等等
			// 注意createTransactionIfNecessary:这个过程就会完成根据传播特性创建事务哦,事务已经开始啦
			// 简述流程:
			// 1. 大多数情况下新建的txAttr是没有事务的name属性，因此将joinpointIdentification作为其事务名；【joinpointIdentification 99%的情况是方法的全限定类名】
			//2. 调用tm.getTransaction(txAttr)开始创建事务状态TransactionStatus -> [以当前执行方法作为第一个事务方法执行解析，并以DataSourceTransactionManager为例]
			//    2.1 调用doGetTransaction()获取一个新的事务对象 -> DataSourceTransactionManager实现该方法
			//        2.1.1 创建一个新的DataSourceTransactionObject事务对象，由于作为第一个事务方法执行，此刻TransactionSynchronizationManager.getResource(obtainDataSource())是无法找到对应的ConnectionHolder
			//        2.1.2 也就是说新的DataSourceTransactionObject事务对象中的ConnectionHolder实际上为空的
			//    2.2 创建完行的事务后，检查当前线程是否已经存在别的事务 -> 事务的传播特性 -> DataSourceTransactionManager实现isExistingTransaction(transaction)
			//        2.2.1 主要就是检查：txObject事务对象中是否有ConnectionHolder，并且事务对象中的ConnectionHolder是事务激活状态的 [❗️一般只有嵌套事务才会触发❗️]
			//    ----------- 一旦2.2步骤返回true，就表示已存在该事务，此刻会调用 handleExistingTransaction(def, transaction, debugEnabled)，不会向下执行 [后面会讲到这个类的]-----------
			//    ----------- 针对第一次执行的事务方法，将会继续执行2.3 2.4 等步骤-----------
			//    2.3 校验@Transactional的timeOut超时时间是否小于-1，-1表示无超时时间，>0表示有超时时间，<-1是不允许的
			//    2.4 处理事务传播特性 [此时为新事务，没有当前事务]
			//        2.4.1 PROPAGATION_MANDATORY：如果已经存在一个事务，支持当前事务。如果没有一个活动或者说已经存在的事务，则抛出异常 -> 这里抛出异常
			//        2.4.2 PROPAGATION_REQUIRED/PROPAGATION_REQUIRES_NEW/PROPAGATION_NESTED: 需要创建出新的事务 -> 创建出新的事务
			//            2.4.2.1 调用suspend(null)将当前事务即null先挂起，然后调用startTransaction()开启新的事务
			//                a. 创建新的DefaultTransactionStatus = 持有事务对象transaction、且标记为新事务newTransaction、并且要求事务同步，@Transactional要求的只读
			//                b. 事务对象中没有连接，或者事务对象有连接，但没有和当前事务同步 -> 就从DataSource中获取一个连接，并包装为SimpleConnectionHolder设置到事务对象中，同时标记为新的连接Holder,且将连接和事务标记为同步
			//                c. 根据@Transactional注解设置connection连接对象的isReadOnly\隔离级别，并将connection原本的隔离级别保存在txObject事务对象的previousIsolationLevel属性中
			//                d. 查看connection是否为自动提交的，如果是，就需要将其关闭，并将且mustRestorAutoCommit设置为ture，在事务回滚或提交后将connection的自动提交模式恢复回去
			//                e. 将txObject对象的connectionHolder标记为已经激活的状态
			//                f. 根据@Transactional设置的timeout属性，设置ConnectionHolder的timeoutInSeconds属性值
			//                g. 若当前事务对象是持有的一个新的ConnectionHolder -> 就调用TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			//                    g.1 以dataSource作为key,connectHolder作为value,保存到resources这个ThreadLocal中，和当前线程绑定 【❗️❗️❗️❗️❗️❗️ -> 】
			//                h. 向TransactionSynchronizationManager设置当前事务的相关信息： 是否有实际的事务\当前事务的隔离级别\当前事务是否只读\当前事务名
			//                i. 重要的一个步骤： TransactionSynchronizationManager.initSynchronization() -> 向synchronizations这个ThreadLocal中设置new LinkedHashSet<>() ❗️❗️❗️
			//                        【这也就是为什么，如果需要向TransactionSynchronizationManager.registerSynchronization(TransactionSynchronization)注册事务同步器，必须在事务的方法中执行，因为这个时候事务同步器的ThreadLocal才有一个空的集合，才允许用户向其中注册事务同步器】
			//            2.4.2.2 一旦上一个步骤出现任何问题，调用resume()恢复挂起的资源 -> 简单描述resume()的操作【可忽略~~正常情况不到这里】
			//                a. 挂起的资源非空即SuspendedResourceHolder非null，获取其中挂起的资源即 SuspendedResourcesHolder.suspendedResources
			//                b. 当挂起的资源suspendedResources非空时，就执行doResume()进行资源恢复操作，将上一个挂起的事务的相关信息恢复到TransactionSynchronizationManager中
			//                c. b步骤包裹将挂起的事务中的wasActive、隔离级别isolationLevel、只读属性readOnly、事务名name恢复到TransactionSynchronizationManager中
			//                e. b步骤还包括挂起的事务中的同步器给恢复到TransactionSynchronizationManager中 -> initSynchronization()初始话Manager的同步器集合，synchronization.resume()调用同步器的恢复、重新注册到TransactionSynchronizationManager中
			//        2.4.3 PROPAGATION_SUPPORTS\PROPAGATION_NEVER\PROPAGATION_NOT_SUPPORT: 由于没有当前事务，或者本身要求就是非事务的方式执行 -> 以非事务的方式执行目标方法
			//            2.4.3.1 由于是没有事务的执行，因此只需要简单准备一个 TransactionStatus
			//            2.4.3.2 这里的DefaultTransactionStatus的特点是： 有@Transactional对应的TransactionAttribute，但是没有事务对象TransactionObject，挂起的事务也是为null
			//            2.4.3.3 如果当前类的事务同步transactionSynchronization为SYNCHRONIZATION_ALWAYS，表示即使为空事务也需要做事务同步，那么就需要向TransactionSynchronizationManager进行事务信息同步操作的
			// 3. 获取到事务之后，准备一个txInfo，并且将txInfo绑定到线程上去
			//    3.1 txInfo持有 事务管理器tm、事务属性txAttr、目标方法连接点信息joinpointIdentification、事务装填txStatus
			//    3.2 将创建的txInfo绑定到线程ThreadLocal上去
			
			// 0. 在handleExistingTransaction()之前会new一个新的事务对象DataSourceTransactionObject，注意和当前事务对象享用同一个ConnectionHolder哦，且因此这个新的事务对象的ConnectionHolder非新的connection
			// 1. 讲解一下：如果有当前事务时，如果处理，主要集中在handleExistingTransaction(TransactionDefinition definition, Object transaction, boolean debugEnabled)方法
			//    1.1 PROPAGATION_NEVER: 必须以非事务的方式执行,存在当前事务就抛出异常 -> 抛出异常
			//    1.2 PROPAGATION_NOT_SUPPORTED: 以非事务方式执行操作，如果当前存在事务，就把当前事务挂起。
			//        1.2.1 很明显这里是有事务的，因此将新创建的事务对象transaction通过suspend(transaction)挂起
			//            1.2.1.1 针对新创建的事务对象：需要将事务对象中的ConnectionHolder设置为空的，并且从TransactionSynchronizationManager解绑开对应dataSource的resource资源
			//            1.2.1.2 挂起操作包括：将TransactionSynchronizationManager中当前事务的名字、只读属性、隔离级别、活跃性设置到SuspendedResourcesHolder中去
			//        1.2.2 创建一个不拥有事务对象txObject、且非新事务的DefaultTransationStatus空事务状态去执行
			//                note：如果是没有当前事务情况下，PROPAGATION_NOT_SUPPORTED传播特性创出来的事务状态txStatus，虽然也不含有事务对象，但是至少标记为新事务，不想这里标记为非新的事务哦 【❗️❗️❗️】
			//    1.3 PROPAGATION_REQUIRES_NEW: 必须新建事务，如果当前存在事务，把当前事务挂起
			//        1.3.1 调用suspend(transaction)将当前事务挂起返回一个挂起资源持有者SuspendedResourcesHolder
			//        1.3.2 调用startTransaction()开启一个新的事务状态 -> 注意期间如果发生异常，还需要将1.3.1中的挂起的资源恢复哦
			//            1.3.2.1 关于startTransaction()不做过多阐述，上面有描述过，但是需要注意一些不同点
			//                note: 开启的新事务状态txStatus持有的事务对象就是新创建的事务对象，txStatus是被标记为一个新的事务状态
			//    1.4 PROPAGATION_NESTED: 上层方法有当前事务,就在其中嵌套子事务，子事务不影响父事务（父捕获子方法的异常时），父事务影响子事务
			//        1.3.1 标志nestedTransactionAllowed值为false，表示不允许嵌套的事务 -> 抛出异常
			//        1.3.2 如果允许使用保存点来做嵌套事务，就创建一个事务Status，即 prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);
			//              然后调用status.createAndHoldSavepoint()创建一个保存点并持有它
			//        1.3.3 如果不允许使用保存点来做嵌套事务，就开启一个新的事务吧 -> startTransaction(definition, transaction, debugEnabled, null)
			//    1.5 其余传播特性propagation_required\propagation_supports\propagation_mandatory -> 在有当前事务存在的情况下，会加入到当前事务中
			//        1.5.1 标志位validateExistingTransaction为true，表示加入当前事务之前需要验证事务对象
			//            a. 当前方法要求的事务隔离级别如果不是默认值,且当前事务的隔离级别和要求的不相等,抛出异常吧 ❗️❗️❗️
			//            b. 当前方法要求的connection是非只读的,也要报错 ❗️❗️❗️
			//        1.5.2  创建一个新的事务状态：prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null) = 非新事务，事务对象是新建的，持有当前事务的ConnectionHolder，当前方法的事务信息，新的同步
			//    
			//    note：上面的transaction都是在handleExistingTransaction()之前会new一个新的事务对象DataSourceTransactionObject
			//    
			TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);
			// note: 
			// 第一次执行@Transactional注解的方法
			// 		a: 有可能创建新事务 [比如传播特性:PROPAGATION_REQUIRED/PROPAGATION_REQUIRES_NEW/PROPAGATION_NESTED]
			// 		b: 也有可能是以空事务的方式运行 [比如传播特性为PROPAGATION_SUPPORTS\PROPAGATION_NEVER\PROPAGATION_NOT_SUPPORT]

			Object retVal;
			try {
				// 7.2 目标方法的执行 -> 
				// ❗️❗️❗️ note: 这里就有可能触发另一个@Transactional标注的方法,从而触发嵌套的事务/直接加入到当前事务等情况 -> 等等情况都是有可能的哦
				retVal = invocation.proceedWithInvocation();
			}
			catch (Throwable ex) {
				// 7.3 捕获异常,进行判断处理 -> 期间涉及到回滚/事务同步器的执行
				// 简述:
				// 1. txInfo非空，且txInfo有txStatus
				//    1.1 如果txInfo有事务属性TransactionAttribute，且事务的回滚策略即@Transactional注解的指定对该异常回滚 -> 那么txInfo获取tm执行rollback(transactionStatus)
				//        1.1.1 txStatus事务状态已经标记为完成，就不允许回滚，报错
				//        1.1.2 触发事务同步管理器中的同步器的beforeCompletion()方法
				//        1.1.3 检查事务是否有保存点,有就需要进行回滚到持有的保存点,执行完后跳转到1.1.6
				//        1.1.4 没有保存点,且为新事务,就需要doRollback() -> 委托给JDBC的connection对象进行回滚操作即可,执行完后跳转到1.1.6
				//        1.1.5 当前事务参与到更大的事务中，即嵌套事务,执行完
				//            1.1.5.1 txStatus有事务对象，表示当前方法并非是空事务执行
				//                a. 如果事务status标记为局部回滚，或者全局标记为部分事务失败将导致全局也回滚时，就需要去当前持有的事务对象标记为只能回滚 [❗️❗️❗️ 嵌套事务失败后的处理哦]
				//                        note： 如果是PROPAGATION_REQUIRED传播特性，假设当前方法和前一个方法使用相同同一个事务，那么这里当前方法的回滚导致共享事务标记为回滚，将使得前一个方法在后续执行中，不得不去回滚
				//            1.1.5.2 txStatus没有事务对象，表示当前方法是空事务执行，啥也不干
				//            1.1.5.3 如果全局设置为出现异常时要求提前失败时，那么下面会抛出异常 【也就是说任何时候出现异常回滚的时候，就会抛出异常】
				//        1.1.6 触发同步器的afterCompletion()方法
				//        1.1.7 清理事务status数据
				//            1.1.7.1 将事务status标记为已经完成的状态；事务status如果是新的事务同步，对TransactionSynchronizationManager.clear()
				//            1.1.7.2 属于新的事务，还需要做一些额外的清理操作：包括DataSource和ConnectionHolder在TransactionSynchronizationManager中resource的绑定、将connection恢复到未使用的状态、释放连接、清空connectionHolder
				//            1.1.7.3 如果有挂起的资源，就对应进行resume()恢复操作
				//    1.2 如果txInfo中没有事务属性TransactionAttribute或者没有指定是否需要为这个异常回滚
				completeTransactionAfterThrowing(txInfo, ex);
				// 继续抛出异常 -> 提供给上层 -> 并使得finally块之后的代码无效
				throw ex;
			}
			finally {
				// 7.4 method已经执行完毕 -> 
				// 将transactionInfoHolder恢复为之前的oldTxInfo
				cleanupTransactionInfo(txInfo);
			}

			// 8. 忽略~~ vavrPresent 一般都是false
			if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
				// Set rollback-only in case of Vavr failure matching our rollback rules...
				TransactionStatus status = txInfo.getTransactionStatus();
				if (status != null && txAttr != null) {
					retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
				}
			}

			// 9. 事务提交 -- 返回值之后进行事务提交
			// 简述:
			// 1. txInfo不为null,并且TransactionStatus也不为空,执行下面的步骤 -> 除非是空事务[空事务是无法触发commit操作的]
			//    1.1 检查事务是否已经完成,已经完成做commit操作就报错
			//    1.2 如果txStatus已经被标记为局部回滚 -> 也需要进行回滚操作，然后return
			//    1.3 如果txStatus中的tx事务对象已经被标记为回滚的，也就是全局回滚 -> 也需要进行回滚操作，然后return
			//    1.4 处理提交commit
			//        1.4.1 先后执行事务同步器的beforeCommit()和beforeCompletion()操作,下面三个步骤为if-else关系
			//            1.4.1+ txStatus有保存点，需要释放持有的保存点 
			//            1.4.1+ 没有保存点，但是作为新的事务，就可以直接做doCommit()操作
			//            1.4.1+ 不是新事务，也没有保存点，但如果设置有快速失败机制
			//        1.4.2 针对有设置全局回滚标志,是不允许做提交的,就直接报错吧
			//        1.4.3 期间抛出的如果是未知回滚异常后触发afterCompletion()方法
			//        1.4.4 期间抛出的如果是TransactionException，由doCommit()引发的，就触发afterCompletion()方法
			//        1.4.5 没有任何异常,就可以触发afterCommit()和afterCompletion()
			commitTransactionAfterReturning(txInfo);
			return retVal;
		}

		else {
			Object result;
			final ThrowableHolder throwableHolder = new ThrowableHolder();

			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
			try {
				result = ((CallbackPreferringPlatformTransactionManager) ptm).execute(txAttr, status -> {
					TransactionInfo txInfo = prepareTransactionInfo(ptm, txAttr, joinpointIdentification, status);
					try {
						Object retVal = invocation.proceedWithInvocation();
						if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
							// Set rollback-only in case of Vavr failure matching our rollback rules...
							retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
						}
						return retVal;
					}
					catch (Throwable ex) {
						if (txAttr.rollbackOn(ex)) {
							// A RuntimeException: will lead to a rollback.
							if (ex instanceof RuntimeException) {
								throw (RuntimeException) ex;
							}
							else {
								throw new ThrowableHolderException(ex);
							}
						}
						else {
							// A normal return value: will lead to a commit.
							throwableHolder.throwable = ex;
							return null;
						}
					}
					finally {
						cleanupTransactionInfo(txInfo);
					}
				});
			}
			catch (ThrowableHolderException ex) {
				throw ex.getCause();
			}
			catch (TransactionSystemException ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
					ex2.initApplicationException(throwableHolder.throwable);
				}
				throw ex2;
			}
			catch (Throwable ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
				}
				throw ex2;
			}

			// Check result state: It might indicate a Throwable to rethrow.
			if (throwableHolder.throwable != null) {
				throw throwableHolder.throwable;
			}
			return result;
		}
	}

	/**
	 * Clear the transaction manager cache.
	 */
	protected void clearTransactionManagerCache() {
		this.transactionManagerCache.clear();
		this.beanFactory = null;
	}

	/**
	 * Determine the specific transaction manager to use for the given transaction.
	 */
	@Nullable
	protected TransactionManager determineTransactionManager(@Nullable TransactionAttribute txAttr) {
		
		// 1. 如果这两个都没配置，所以肯定是手动设置了PlatformTransactionManager的，那就直接返回即可
		// 一般都是跳过,因为在声明式事务@Transactional中txAttr总是存在的,并且beanFactory也总是存在的哦
		if (txAttr == null || this.beanFactory == null) {
			return getTransactionManager();
		}

		// 2. 从BeanFactory中确定事务管理器TransactionManager

		// 2.1 qualifier就在此处发挥作用了，就相当于beanName,决定使用的TransactionManager
		// 99%情况对应的就是 @Transactional 的value属性
		String qualifier = txAttr.getQualifier();
		if (StringUtils.hasText(qualifier)) {
			return determineQualifiedTransactionManager(this.beanFactory, qualifier);
		}
		// 2.2 若没有指定qualifier,那再看看是否对当前类指定了transactionManagerBeanName
		// transactionManagerBeanName一般没有设置,无法进入这个代码块
		else if (StringUtils.hasText(this.transactionManagerBeanName)) {
			return determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
		}
		
		// 一般使用@Transactional注解的时候,不会指定value或者transactionalManager属性,因此: 都会进入到这里来
		
		else {
			// 3. 若都没指定，先从看看是否在TransactionAspectSupport中设置过tm
			// TransactionAspectSupport中tm是通过 ❗️用户实现TransactionManagementConfigurer接口并定制化一个TransactionManager❗️
			TransactionManager defaultTransactionManager = getTransactionManager();
			if (defaultTransactionManager == null) {
				// 4. 99%的情况,用户都不会通过TransactionManagementConfigurer接口定制化一个TransactionManager,因此会进去到这里
				// 先查看缓存中是否已经加载过默认的TransactionManager
				defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
				if (defaultTransactionManager == null) {
					// 5. ❗️❗️
					// 没有缓存过默认的TransactionManager, 就只从BeanFactory获取唯一的一个TransactionManager即可
					// ❗️❗️❗️ -> 一般情况我们都是用的DataSourceTransactionManager -> 比如一下代码
					//    @Bean(name = "primaryDataSource")
					//    @ConfigurationProperties(prefix = "spring.datasource.dynamic.datasource.primary")
					//    @Primary
					//    public DataSource createDataSource() {
					//        return DataSourceBuilder.create().build();
					//    }
					//
					//    @Bean(name = "primarySqlSessionFactory")
					//    @Primary
					//    public SqlSessionFactory sessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
					//
					//        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
					//        bean.setDataSource(dataSource);
					//        //设置分页插件
					//        bean.setPlugins(mybatisPlusInterceptor);
					//        //多数据源指定对应枚举包名
					//        bean.setTypeEnumsPackage("com.xylink.website.**.constant");
					//        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
					//        return bean.getObject();
					//    }
					//
					//    @Bean(name = "primaryTransactionManager")
					//    @Primary
					//    public DataSourceTransactionManager transactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
					//        return new DataSourceTransactionManager(dataSource);
					//    }
					//
					//    @Bean(name = "primarySqlSessionTemplate")
					//    @Primary
					//    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
					//        return new SqlSessionTemplate(sqlSessionFactory);
					//
					//    }
					defaultTransactionManager = this.beanFactory.getBean(TransactionManager.class);
					// 6. 存入缓存
					this.transactionManagerCache.putIfAbsent(DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
				}
			}
			return defaultTransactionManager;
		}
	}

	private TransactionManager determineQualifiedTransactionManager(BeanFactory beanFactory, String qualifier) {
		// 根据qualifier从ioc容器中获取决定使用的TransactionManager
		
		// 1. 设有一层缓存
		TransactionManager txManager = this.transactionManagerCache.get(qualifier);
		if (txManager == null) {
			txManager = BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, TransactionManager.class, qualifier);
			this.transactionManagerCache.putIfAbsent(qualifier, txManager);
		}
		return txManager;
	}


	@Nullable
	private PlatformTransactionManager asPlatformTransactionManager(@Nullable Object transactionManager) {
		if (transactionManager == null || transactionManager instanceof PlatformTransactionManager) {
			// 一般使用的DataSource属于PlatformTransactionManager的
			return (PlatformTransactionManager) transactionManager;
		}
		else {
			throw new IllegalStateException(
					"Specified transaction manager is not a PlatformTransactionManager: " + transactionManager);
		}
	}

	private String methodIdentification(Method method, @Nullable Class<?> targetClass,
			@Nullable TransactionAttribute txAttr) {
		// methodIdentification = 方法定义
		
		// 1. methodIdentification() 默认返回null,且子类没有重写
		String methodIdentification = methodIdentification(method, targetClass);
		// 2. 99%情况都满足methodIdentification == null
		if (methodIdentification == null) {
			// 3. 在注解驱动的@Transactional模式下,txAttr一版都是RuleBasedTransactionAttribute
			// 因此这个if条件是正确的哦 -> 拿到txAttr.getDescriptor()
			// AbstractFallbackTransactionAttributeSource#getTransactionAttribute()方法中会将method的全限定类名作为其TransactionAttribute的description
			// 所以最终返回的 methodIdentification 就是 当前执行方法的全限定类名
			if (txAttr instanceof DefaultTransactionAttribute) {
				methodIdentification = ((DefaultTransactionAttribute) txAttr).getDescriptor();
			}
			if (methodIdentification == null) {
				methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
			}
		}
		return methodIdentification;
	}

	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * <p>The default implementation returns {@code null}, indicating the
	 * use of {@link DefaultTransactionAttribute#getDescriptor()} instead,
	 * ending up as {@link ClassUtils#getQualifiedMethodName(Method, Class)}.
	 * @param method the method we're interested in
	 * @param targetClass the class that the method is being invoked on
	 * @return a String representation identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	@Nullable
	protected String methodIdentification(Method method, @Nullable Class<?> targetClass) {
		return null;
	}

	/**
	 * Create a transaction if necessary based on the given TransactionAttribute.
	 * <p>Allows callers to perform custom TransactionAttribute lookups through
	 * the TransactionAttributeSource.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 * (used for monitoring and logging purposes)
	 * @return a TransactionInfo object, whether or not a transaction was created.
	 * The {@code hasTransaction()} method on TransactionInfo can be used to
	 * tell if there was a transaction created.
	 * @see #getTransactionAttributeSource()
	 */
	@SuppressWarnings("serial")
	protected TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm, @Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

		// 1. 如果未指定名称，则应用方法标识作为事务名称 -- 方法标识符一般情况都是方法的全限定方法名
		// 该名字可以通过注解@Transactional进行设置的
		if (txAttr != null && txAttr.getName() == null) {
			txAttr = new DelegatingTransactionAttribute(txAttr) { // DelegatingTransactionAttribute 的作用
				@Override
				public String getName() {
					return joinpointIdentification;
				}
			};
		}

		TransactionStatus status = null;
		if (txAttr != null) {
			if (tm != null) {
				// 2. 一般情况: txAttr和tm都不会为null哦
				// 获取事务运行状态 -- 核心:涉及到事务管理器tx的装填设置
				status = tm.getTransaction(txAttr);
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping transactional joinpoint [" + joinpointIdentification +
							"] because no transaction manager has been configured");
				}
			}
		}
		// 3. 准备txInfo/并将txInfo绑定到线程上
		return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
	}

	/**
	 * Prepare a TransactionInfo for the given attribute and status object.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 * (used for monitoring and logging purposes)
	 * @param status the TransactionStatus for the current transaction
	 * @return the prepared TransactionInfo object
	 */
	protected TransactionInfo prepareTransactionInfo(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, String joinpointIdentification,
			@Nullable TransactionStatus status) {

		// 1. 创建txInfo -> 事务管理器tx/事务属性txAttr/目标方法的连接点定义信息joinpointIdentification[一般就是方法的全限定类名]
		TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
		if (txAttr != null) {
			// 1.1 我们需要这个方法的tx
			if (logger.isTraceEnabled()) {
				logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// 1.2 如果不兼容的 tx 已经存在，事务管理器将标记错误。
			txInfo.newTransactionStatus(status);
		}
		// 2. 什么情况下 txAttr 为 null
		else {
			// 2.1 TransactionInfo.hasTransaction() 方法将返回 false。我们创建它只是为了保持此类中维护的 ThreadLocal 堆栈的完整性
			if (logger.isTraceEnabled()) {
				logger.trace("No need to create transaction for [" + joinpointIdentification +
						"]: This method is not transactional.");
			}
		}

		// 2. txInfo绑定到线程上ThreadLocal
		txInfo.bindToThread();
		return txInfo;
	}

	/**
	 * Execute after successful completion of call, but not after an exception was handled.
	 * Do nothing if we didn't create a transaction.
	 * @param txInfo information about the current transaction
	 */
	protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
		// 方法执行完之后进行事务的commit操作
		
		// 1. 大部分情况: txInfo都不为null,并且TransactionStatus也不为空 -> 除非是空事务 [空事务是无法触发commit操作的]
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// 1.1 获取tm,并对TransactionStatus进行commit()操作
			txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
		}
	}

	/**
	 * Handle a throwable, completing the transaction.
	 * We may commit or roll back, depending on the configuration.
	 * @param txInfo information about the current transaction
	 * @param ex throwable encountered
	 */
	protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
		// 捕获到一个throwable，计算transaction。我们可能会commit()或rollback()，具体取决于配置。
		
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
						"] after exception: " + ex);
			}
			//  1.1 @Transactional 的rollbackFor属性指定捕获这个异常
			if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
				try {
					// 1.1.1 指定啦就需要进行回滚这个事务运行状态
					txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus()); // 回滚
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					throw ex2;
				}
			}
			else {
				// We don't roll back on this exception.
				// Will still roll back if TransactionStatus.isRollbackOnly() is true.

				// 1.2 @Transactional 的rollbackFor属性没有指定捕获这个异常，或者noRollbackFor指定不能因为这个异常回滚
				try {
					// 1.2.1 执行commit动作
					txInfo.getTransactionManager().commit(txInfo.getTransactionStatus()); // 提交
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					throw ex2;
				}
			}
		}
	}

	/**
	 * Reset the TransactionInfo ThreadLocal.
	 * <p>Call this in all cases: exception or normal return!
	 * @param txInfo information about the current transaction (may be {@code null})
	 */
	protected void cleanupTransactionInfo(@Nullable TransactionInfo txInfo) {
		// 重置 TransactionInfo ThreadLocal。
		// 在所有情况下都调用它：异常或正常返回！
		
		if (txInfo != null) {
			txInfo.restoreThreadLocalStatus();
		}
	}


	/**
	 * Opaque object used to hold transaction information. Subclasses
	 * must pass it back to methods on this class, but not see its internals.
	 */
	protected static final class TransactionInfo {
		// info类，内部类封装
		// 保存transaction信息的不透明对象。子类必须将其传递回此类上的方法，但看不到其内部。
		// 封装：事务管理器、事务属性、事务运行状态、事务嵌套info

		@Nullable
		private final PlatformTransactionManager transactionManager;

		// @Transaction对应存储Attribute类
		@Nullable
		private final TransactionAttribute transactionAttribute;

		// 连接点的定义
		private final String joinpointIdentification;

		// 事务执行状态
		@Nullable
		private TransactionStatus transactionStatus;

		// 由于在TransactionAspectSupport中TransactionInfo是使用ThreadLocal封装起来的
		// 因此向一个ThreadLocal设置值的时候就存在之前老的oldTransactionInfo -> 需要嵌套保存在TransactionInfo中
		@Nullable
		private TransactionInfo oldTransactionInfo;

		public TransactionInfo(@Nullable PlatformTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public PlatformTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		// 注意这个方法名，新的一个事务status
		public void newTransactionStatus(@Nullable TransactionStatus status) {
			this.transactionStatus = status;
		}

		@Nullable
		public TransactionStatus getTransactionStatus() {
			return this.transactionStatus;
		}

		/**
		 * Return whether a transaction was created by this aspect,
		 * or whether we just have a placeholder to keep ThreadLocal stack integrity.
		 */
		public boolean hasTransaction() {
			return (this.transactionStatus != null);
		}

		// 绑定当前正在处理的事务的所有信息到ThreadLocal
		private void bindToThread() {
			this.oldTransactionInfo = transactionInfoHolder.get();
			transactionInfoHolder.set(this);
		}

		// 当前事务处理完之后，恢复父事务上下文
		private void restoreThreadLocalStatus() {
			transactionInfoHolder.set(this.oldTransactionInfo);
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}


	/**
	 * Simple callback interface for proceeding with the target invocation.
	 * Concrete interceptors/aspects adapt this to their invocation mechanism.
	 */
	@FunctionalInterface
	protected interface InvocationCallback {

		@Nullable
		Object proceedWithInvocation() throws Throwable;
	}


	/**
	 * Internal holder class for a Throwable in a callback transaction model.
	 */
	private static class ThrowableHolder {
		// 回调事务模型中 Throwable 的内部持有者类
		@Nullable
		public Throwable throwable;
	}


	/**
	 * Internal holder class for a Throwable, used as a RuntimeException to be
	 * thrown from a TransactionCallback (and subsequently unwrapped again).
	 */
	@SuppressWarnings("serial")
	private static class ThrowableHolderException extends RuntimeException {

		public ThrowableHolderException(Throwable throwable) {
			super(throwable);
		}

		@Override
		public String toString() {
			return getCause().toString();
		}
	}


	/**
	 * Inner class to avoid a hard dependency on the Vavr library at runtime.
	 */
	private static class VavrDelegate {

		public static boolean isVavrTry(Object retVal) {
			return (retVal instanceof Try);
		}

		public static Object evaluateTryFailure(Object retVal, TransactionAttribute txAttr, TransactionStatus status) {
			return ((Try<?>) retVal).onFailure(ex -> {
				if (txAttr.rollbackOn(ex)) {
					status.setRollbackOnly();
				}
			});
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		static private boolean isSuspend(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return function != null && function.isSuspend();
		}
	}


	/**
	 * Delegate for Reactor-based management of transactional methods with a
	 * reactive return type.
	 */
	private class ReactiveTransactionSupport {

		private final ReactiveAdapter adapter;

		public ReactiveTransactionSupport(ReactiveAdapter adapter) {
			this.adapter = adapter;
		}

		public Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
				InvocationCallback invocation, @Nullable TransactionAttribute txAttr, ReactiveTransactionManager rtm) {

			String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

			// Optimize for Mono
			if (Mono.class.isAssignableFrom(method.getReturnType())) {
				return TransactionContextManager.currentContext().flatMap(context ->
						createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMap(it -> {
							try {
								// Need re-wrapping until we get hold of the exception through usingWhen.
								return Mono.<Object, ReactiveTransactionInfo>usingWhen(
										Mono.just(it),
										txInfo -> {
											try {
												return (Mono<?>) invocation.proceedWithInvocation();
											}
											catch (Throwable ex) {
												return Mono.error(ex);
											}
										},
										this::commitTransactionAfterReturning,
										(txInfo, err) -> Mono.empty(),
										this::commitTransactionAfterReturning)
										.onErrorResume(ex ->
												completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
							}
							catch (Throwable ex) {
								// target invocation exception
								return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
							}
						})).subscriberContext(TransactionContextManager.getOrCreateContext())
						.subscriberContext(TransactionContextManager.getOrCreateContextHolder());
			}

			// Any other reactive type, typically a Flux
			return this.adapter.fromPublisher(TransactionContextManager.currentContext().flatMapMany(context ->
					createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMapMany(it -> {
						try {
							// Need re-wrapping until we get hold of the exception through usingWhen.
							return Flux
									.usingWhen(
											Mono.just(it),
											txInfo -> {
												try {
													return this.adapter.toPublisher(invocation.proceedWithInvocation());
												}
												catch (Throwable ex) {
													return Mono.error(ex);
												}
											},
											this::commitTransactionAfterReturning,
											(txInfo, ex) -> Mono.empty(),
											this::commitTransactionAfterReturning)
									.onErrorResume(ex ->
											completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
						}
						catch (Throwable ex) {
							// target invocation exception
							return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
						}
					})).subscriberContext(TransactionContextManager.getOrCreateContext())
					.subscriberContext(TransactionContextManager.getOrCreateContextHolder()));
		}

		@SuppressWarnings("serial")
		private Mono<ReactiveTransactionInfo> createTransactionIfNecessary(ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

			// If no name specified, apply method identification as transaction name.
			if (txAttr != null && txAttr.getName() == null) {
				txAttr = new DelegatingTransactionAttribute(txAttr) {
					@Override
					public String getName() {
						return joinpointIdentification;
					}
				};
			}

			final TransactionAttribute attrToUse = txAttr;
			Mono<ReactiveTransaction> tx = (attrToUse != null ? tm.getReactiveTransaction(attrToUse) : Mono.empty());
			return tx.map(it -> prepareTransactionInfo(tm, attrToUse, joinpointIdentification, it)).switchIfEmpty(
					Mono.defer(() -> Mono.just(prepareTransactionInfo(tm, attrToUse, joinpointIdentification, null))));
		}

		private ReactiveTransactionInfo prepareTransactionInfo(@Nullable ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, String joinpointIdentification,
				@Nullable ReactiveTransaction transaction) {

			ReactiveTransactionInfo txInfo = new ReactiveTransactionInfo(tm, txAttr, joinpointIdentification);
			if (txAttr != null) {
				// We need a transaction for this method...
				if (logger.isTraceEnabled()) {
					logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				// The transaction manager will flag an error if an incompatible tx already exists.
				txInfo.newReactiveTransaction(transaction);
			}
			else {
				// The TransactionInfo.hasTransaction() method will return false. We created it only
				// to preserve the integrity of the ThreadLocal stack maintained in this class.
				if (logger.isTraceEnabled()) {
					logger.trace("Don't need to create transaction for [" + joinpointIdentification +
							"]: This method isn't transactional.");
				}
			}

			return txInfo;
		}

		private Mono<Void> commitTransactionAfterReturning(@Nullable ReactiveTransactionInfo txInfo) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction());
			}
			return Mono.empty();
		}

		private Mono<Void> completeTransactionAfterThrowing(@Nullable ReactiveTransactionInfo txInfo, Throwable ex) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
							"] after exception: " + ex);
				}
				if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
					return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by rollback exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
				else {
					// We don't roll back on this exception.
					// Will still roll back if TransactionStatus.isRollbackOnly() is true.
					return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by commit exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
			}
			return Mono.empty();
		}
	}


	/**
	 * Opaque object used to hold transaction information for reactive methods.
	 */
	private static final class ReactiveTransactionInfo {

		@Nullable
		private final ReactiveTransactionManager transactionManager;

		@Nullable
		private final TransactionAttribute transactionAttribute;

		private final String joinpointIdentification;

		@Nullable
		private ReactiveTransaction reactiveTransaction;

		public ReactiveTransactionInfo(@Nullable ReactiveTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public ReactiveTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No ReactiveTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		public void newReactiveTransaction(@Nullable ReactiveTransaction transaction) {
			this.reactiveTransaction = transaction;
		}

		@Nullable
		public ReactiveTransaction getReactiveTransaction() {
			return this.reactiveTransaction;
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}

}
