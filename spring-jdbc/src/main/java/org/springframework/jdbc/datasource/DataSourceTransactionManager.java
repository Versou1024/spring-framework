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

package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.transaction.PlatformTransactionManager}
 * implementation for a single JDBC {@link javax.sql.DataSource}. This class is
 * capable of working in any environment with any JDBC driver, as long as the setup
 * uses a {@code javax.sql.DataSource} as its {@code Connection} factory mechanism.
 * Binds a JDBC Connection from the specified DataSource to the current thread,
 * potentially allowing for one thread-bound Connection per DataSource.
 *
 * <p><b>Note: The DataSource that this transaction manager operates on needs
 * to return independent Connections.</b> The Connections may come from a pool
 * (the typical case), but the DataSource must not return thread-scoped /
 * request-scoped Connections or the like. This transaction manager will
 * associate Connections with thread-bound transactions itself, according
 * to the specified propagation behavior. It assumes that a separate,
 * independent Connection can be obtained even during an ongoing transaction.
 *
 * <p>Application code is required to retrieve the JDBC Connection via
 * {@link DataSourceUtils#getConnection(DataSource)} instead of a standard
 * Java EE-style {@link DataSource#getConnection()} call. Spring classes such as
 * {@link org.springframework.jdbc.core.JdbcTemplate} use this strategy implicitly.
 * If not used in combination with this transaction manager, the
 * {@link DataSourceUtils} lookup strategy behaves exactly like the native
 * DataSource lookup; it can thus be used in a portable fashion.
 *
 * <p>Alternatively, you can allow application code to work with the standard
 * Java EE-style lookup pattern {@link DataSource#getConnection()}, for example for
 * legacy code that is not aware of Spring at all. In that case, define a
 * {@link TransactionAwareDataSourceProxy} for your target DataSource, and pass
 * that proxy DataSource to your DAOs, which will automatically participate in
 * Spring-managed transactions when accessing it.
 *
 * <p>Supports custom isolation levels, and timeouts which get applied as
 * appropriate JDBC statement timeouts. To support the latter, application code
 * must either use {@link org.springframework.jdbc.core.JdbcTemplate}, call
 * {@link DataSourceUtils#applyTransactionTimeout} for each created JDBC Statement,
 * or go through a {@link TransactionAwareDataSourceProxy} which will create
 * timeout-aware JDBC Connections and Statements automatically.
 *
 * <p>Consider defining a {@link LazyConnectionDataSourceProxy} for your target
 * DataSource, pointing both this transaction manager and your DAOs to it.
 * This will lead to optimized handling of "empty" transactions, i.e. of transactions
 * without any JDBC statements executed. A LazyConnectionDataSourceProxy will not fetch
 * an actual JDBC Connection from the target DataSource until a Statement gets executed,
 * lazily applying the specified transaction settings to the target Connection.
 *
 * <p>This transaction manager supports nested transactions via the JDBC 3.0
 * {@link java.sql.Savepoint} mechanism. The
 * {@link #setNestedTransactionAllowed "nestedTransactionAllowed"} flag defaults
 * to "true", since nested transactions will work without restrictions on JDBC
 * drivers that support savepoints (such as the Oracle JDBC driver).
 *
 * <p>This transaction manager can be used as a replacement for the
 * {@link org.springframework.transaction.jta.JtaTransactionManager} in the single
 * resource case, as it does not require a container that supports JTA, typically
 * in combination with a locally defined JDBC DataSource (e.g. an Apache Commons
 * DBCP connection pool). Switching between this local strategy and a JTA
 * environment is just a matter of configuration!
 *
 * <p>As of 4.3.4, this transaction manager triggers flush callbacks on registered
 * transaction synchronizations (if synchronization is generally active), assuming
 * resources operating on the underlying JDBC {@code Connection}. This allows for
 * setup analogous to {@code JtaTransactionManager}, in particular with respect to
 * lazily registered ORM resources (e.g. a Hibernate {@code Session}).
 *
 * @author Juergen Hoeller
 * @since 02.05.2003
 * @see #setNestedTransactionAllowed
 * @see java.sql.Savepoint
 * @see DataSourceUtils#getConnection(javax.sql.DataSource)
 * @see DataSourceUtils#applyTransactionTimeout
 * @see DataSourceUtils#releaseConnection
 * @see TransactionAwareDataSourceProxy
 * @see LazyConnectionDataSourceProxy
 * @see org.springframework.jdbc.core.JdbcTemplate
 */
@SuppressWarnings("serial")
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager implements ResourceTransactionManager, InitializingBean {

	// 持有一个DataSource -> 持有数据库的连接信息
	// 一般都不是空的
	@Nullable
	private DataSource dataSource;

	// 是否强制只读 -> 一旦开启,那么@Transactional的readOnly属性不会生效
	private boolean enforceReadOnly = false;


	/**
	 * Create a new DataSourceTransactionManager instance.
	 * A DataSource has to be set to be able to use it.
	 * @see #setDataSource
	 */
	public DataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	/**
	 * Create a new DataSourceTransactionManager instance.
	 * @param dataSource the JDBC DataSource to manage transactions for
	 */
	public DataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}


	/**
	 * Set the JDBC DataSource that this instance should manage transactions for.
	 * <p>This will typically be a locally defined DataSource, for example an
	 * Apache Commons DBCP connection pool. Alternatively, you can also drive
	 * transactions for a non-XA J2EE DataSource fetched from JNDI. For an XA
	 * DataSource, use JtaTransactionManager.
	 * <p>The DataSource specified here should be the target DataSource to manage
	 * transactions for, not a TransactionAwareDataSourceProxy. Only data access
	 * code may work with TransactionAwareDataSourceProxy, while the transaction
	 * manager needs to work on the underlying target DataSource. If there's
	 * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
	 * unwrapped to extract its target DataSource.
	 * <p><b>The DataSource passed in here needs to return independent Connections.</b>
	 * The Connections may come from a pool (the typical case), but the DataSource
	 * must not return thread-scoped / request-scoped Connections or the like.
	 * @see TransactionAwareDataSourceProxy
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public void setDataSource(@Nullable DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC DataSource that this instance manages transactions for.
	 */
	@Nullable
	public DataSource getDataSource() {
		return this.dataSource;
	}

	/**
	 * Obtain the DataSource for actual use.
	 * @return the DataSource (never {@code null})
	 * @throws IllegalStateException in case of no DataSource set
	 * @since 5.0
	 */
	protected DataSource obtainDataSource() {
		DataSource dataSource = getDataSource();
		Assert.state(dataSource != null, "No DataSource set");
		return dataSource;
	}

	/**
	 * Specify whether to enforce the read-only nature of a transaction
	 * (as indicated by {@link TransactionDefinition#isReadOnly()}
	 * through an explicit statement on the transactional connection:
	 * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
	 * <p>The exact treatment, including any SQL statement executed on the connection,
	 * can be customized through {@link #prepareTransactionalConnection}.
	 * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
	 * hint that Spring applies by default. In contrast to that standard JDBC hint,
	 * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
	 * where data manipulation statements are strictly disallowed. Also, on Oracle,
	 * this read-only mode provides read consistency for the entire transaction.
	 * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
	 * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
	 * this strong enforcement needs to be applied explicitly, e.g. through this flag.
	 * @since 4.3.7
	 * @see #prepareTransactionalConnection
	 */
	public void setEnforceReadOnly(boolean enforceReadOnly) {
		this.enforceReadOnly = enforceReadOnly;
	}

	/**
	 * Return whether to enforce the read-only nature of a transaction
	 * through an explicit statement on the transactional connection.
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	public boolean isEnforceReadOnly() {
		return this.enforceReadOnly;
	}

	@Override
	public void afterPropertiesSet() {
		// 1. 检查DataSource
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
	}


	@Override
	public Object getResourceFactory() {
		return obtainDataSource();
	}

	@Override
	protected Object doGetTransaction() {
		// 获取事务对象

		// 1. 创建一个新的DataSource事务对象
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
		// 2. 设置事务对象是否允许保存点
		// isNestedTransactionAllowed()默认为false,表示不允许嵌套事务
		// 表名默认的不支持去使用savePoint的,因为savePoint的本质就是使用嵌套事务罢了
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
		// 3. 使用同步管理器TransactionSynchronizationManager,根据dataSource,获取此dataSource数据源对应的资源Holder,并强转为ConnectionHolder
		ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
		// 4. 设置获取到的连接对象 -- 
		// 注意:一般来说,当方法是第一个执行的事务时,由于此刻还没有向TransactionSynchronizationManager.resources中注册DataSource已经对应的ConnectionHolder,会使得第三步获取到的连接ConnectionHolder是空的
		txObject.setConnectionHolder(conHolder, false);
		return txObject;
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) {
		
		// 1. 当前类使用的事务对象一定是 DataSourceTransactionObject -> 因此直接强转
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		// 2. txObject中持有连接,且连接中的事务处于活跃中
		return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		// 开始根据transaction事务对象进行处理
		
		// 0. DataSourceTransactionManager必须使用DataSourceTransactionObject,直接强转即可
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
			// 1. 事务对象中没有连接 -> 一般是第一个执行的@Transactional方法
			// 或者 事务对象有连接,但是连接没有和事务 -> 同步的意思就是,这个连接是否是从当前事务中获取出来的,如果不是说明非同步的,需要重新获取从连接出来哦
			// 以上情况,都需要从DataSource里获取一个连接,将这个连接用ConnectionHolder包装后设置进去哦
			if (!txObject.hasConnectionHolder() || txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
				Connection newCon = obtainDataSource().getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}

			// 2. 将资源connection标记为与事务transaction对象已经做过同步
			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			con = txObject.getConnectionHolder().getConnection();

			// 2. 根据@Transactional注解的信息设置isReadOnly\隔离级别
			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition); // 根据需要将连接设置为只读的/
			txObject.setPreviousIsolationLevel(previousIsolationLevel); // 将连接之前的持有的隔离级别: previousIsolationLevel 保存下来
			txObject.setReadOnly(definition.isReadOnly());

			// 3. 这里非常的关键，先看看 Connection 是否是自动提交的
			// 如果是,就con.setAutoCommit(false),要不然数据库默认没执行一条SQL都是一个事务，就没法进行事务的管理了
			if (con.getAutoCommit()) {
				// 3.1 标记txObject存储的连接本身是自动提交,在连接commit()或者rollback()后需要恢复
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				// 3.2 将连接设置为
				con.setAutoCommit(false);
			}
			// ====因此从这后面，通过此Connection执行的所有SQL语句只要没有commit就都不会提交给数据库的=====

			// 4. 这个方法特别特别有意思,它自己`Statement stmt = con.createStatement()`拿到一个Statement
			// 然后执行了一句SQL：`stmt.executeUpdate("SET TRANSACTION READ ONLY");`
			// 所以，所以：如果你仅仅只是查询。把事务的属性设置为 readonly=true,
			// Spring对帮你对SQl进行优化的需要注意的是：readonly=true 后，
			//  	(只能读，不能进行dml操作）
			// 		(只能看到设置事物前数据的变化，看不到设置事物后数据的改变）
			prepareTransactionalConnection(con, definition);
			
			// 4.0 将txObject对象标记为已经激活的状态
			txObject.getConnectionHolder().setTransactionActive(true);

			// 4.1 设置超时时间 - 默认的timeout就是-1,表示永不超时
			// 当不等于-1时,需要将ConnectionHolder的timeout超时时间设置上去哦
			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}

			// 5. 这一步：就是把当前的链接 和当前的线程进行绑定~~~~
			// ❗️❗️❗️
			if (txObject.isNewConnectionHolder()) {
				// 5.1 TransactionSynchronizationManager 中都是ThreadLocal的属性,和线程时绑定的
				// 以dataSource作为key,connectHolder作为value,保存到resources这个ThreadLocal中
				TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			}
		}

		catch (Throwable ex) {
			// 6. 出现异常 -> txObject中持有的连接是新的,就允许对其操作
			// 否则这个连接不属于当前txObject,而是上一个方法的连接,应该返回到上一个方法的执行中,交给上层方法决定
			if (txObject.isNewConnectionHolder()) {
				// 6.1 属于自己的新连接,就允许去释放掉jdbc connection
				DataSourceUtils.releaseConnection(con, obtainDataSource());
				// 6.2 并将txObject对应的ConnectionHolder给清空
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		txObject.setConnectionHolder(null);
		return TransactionSynchronizationManager.unbindResource(obtainDataSource());
	}

	@Override
	protected void doResume(@Nullable Object transaction, Object suspendedResources) {
		TransactionSynchronizationManager.bindResource(obtainDataSource(), suspendedResources);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		// 真正提交事务

		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			// 拿到链接  然后直接就commit了
			con.commit();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		// 完成实际的rollback回滚操作
		
		// 1. 拿到事务对象DataSourceTransactionObject,并获取连接
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			// 2. 使用JDNC的connection对象进行回滚
			con.rollback();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
		}
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		// 在超类的中rollback()时有可能触发
		// 即如果本地设置为仅仅回滚,或者全局标记为部分事务失败全局也会回滚时 -> 触发这个 odSetRollbackOnly() 方法
		// if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure())
		// 调用 txObject.setRollbackOnly() 将ConnectionHolder持有的资源事务标记为仅回滚
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
					"] rollback-only");
		}
		txObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		// 在超类的processRollback()的finally块的cleanupAfterCompletion(status) -> 清理事务status数据 -> 对于新的事务：会进行一些额外处理即当前方法doCleanupAfterCompletion(status.getTransaction());
		
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// 1. 解除事务绑定器中,对于dataSource的绑定
		if (txObject.isNewConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(obtainDataSource());
		}

		// 2. 释放connection之前,需要将connect恢复到未使用的状态
		// 将只读标记\自动提交\隔离级别等信息进行恢复
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			// 2.1 必须恢复连接自动提交值
			if (txObject.isMustRestoreAutoCommit()) {
				con.setAutoCommit(true);
			}
			// 2.2 恢复 只读表示\隔离级别
			DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
		}
		catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		// 3.如果是直接给这个事务分配新的connection,而不是传递过来的,就需要释放到dataSource的连接池中
		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			DataSourceUtils.releaseConnection(con, this.dataSource);
		}

		// 4. 清空ConnectionHolder ->
		// 源码:
		//      this.transactionActive = false;		// 事务设置为非活跃状态哦
		//		this.savepointsSupported = null;
		//		this.savepointCounter = 0;
		//		this.synchronizedWithTransaction = false; // 将资源与事务同步标记设为false即关闭,即不同步
		//		this.rollbackOnly = false; // 将事务设置为不回滚状态
		//		this.deadline = null; // 将事务超时的死亡时间改为null
		//      this.referenceCount = 0; // 引用计数清空为0
		txObject.getConnectionHolder().clear();
	}


	/**
	 * Prepare the transactional {@code Connection} right after transaction begin.
	 * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
	 * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
	 * and the transaction definition indicates a read-only transaction.
	 * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
	 * and may work with other databases as well. If you'd like to adapt this treatment,
	 * override this method accordingly.
	 * @param con the transactional JDBC Connection
	 * @param definition the current transaction definition
	 * @throws SQLException if thrown by JDBC API
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
			throws SQLException {
		// enforceReadOnly() -> 是否通过事务连接上的显式语句强制执行事务的只读性质
		// 并且, @Transactional的readOnly属性也为true
		if (isEnforceReadOnly() && definition.isReadOnly()) {
			try (Statement stmt = con.createStatement()) {
				stmt.executeUpdate("SET TRANSACTION READ ONLY");
			}
		}
	}


	/**
	 * DataSource transaction object, representing a ConnectionHolder.
	 * Used as transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {
		// 含义: 
		// DataSource 事务对象，代表一个 ConnectionHolder。由 DataSourceTransactionManager 用作事务对象。

		// 是否为一个新的事务连接对象的持有者
		private boolean newConnectionHolder;

		// 通常是connection本身是自动提交的,然后用户的事务设置为手动提交,因此需要将connection的设置为自动提交
		// 同时将 mustRestoreAutoCommit 设置为true,表示事务commit之后需要将连接connection的自动提交设置回去
		private boolean mustRestoreAutoCommit;
		
		// 只有一个无参构造器 - 需要注意哦
		
		public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			getConnectionHolder().setRollbackOnly();
		}

		@Override
		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}

		@Override
		public void flush() {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationUtils.triggerFlush();
			}
		}
	}

}
