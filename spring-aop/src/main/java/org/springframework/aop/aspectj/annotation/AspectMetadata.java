/*
 * Copyright 2002-2019 the original author or authors.
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.TypePatternClassFilter;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.ComposablePointcut;

/**
 * Metadata for an AspectJ aspect class, with an additional Spring AOP pointcut
 * for the per clause.
 *
 * <p>Uses AspectJ 5 AJType reflection API, enabling us to work with different
 * AspectJ instantiation models such as "singleton", "pertarget" and "perthis".
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.aop.aspectj.AspectJExpressionPointcut
 */
@SuppressWarnings("serial")
public class AspectMetadata implements Serializable {
	// 表示一个切面的元数据类。
	// 封装有：aspectName、aspectClass、ajType、perClausePointcut
	


	/**
	 * The name of this aspect as defined to Spring (the bean name) -
	 * allows us to determine if two pieces of advice come from the
	 * same aspect and hence their relative precedence.
	 */
	private final String aspectName; // 切面在Spring容器中的名字 -- 允许我们去定义,如果有两个advice是否是来自同一个切面

	/**
	 * The aspect class, stored separately for re-resolution of the
	 * corresponding AjType on deserialization.
	 */
	private final Class<?> aspectClass; // 切面的Class,单独存储,用于在反序列化时重新解析相应的AjTypw

	/**
	 * AspectJ reflection information (AspectJ 5 / Java 5 specific).
	 * Re-resolved on deserialization since it isn't serializable itself.
	 */
	private transient AjType<?> ajType; // AjType这个字段非常的关键，它表示有非常非常多得关于这个切面的一些数据、方法（位于org.aspectj下）

	/**
	 * Spring AOP pointcut corresponding to the per clause of the
	 * aspect. Will be the Pointcut.TRUE canonical instance in the
	 * case of a singleton, otherwise an AspectJExpressionPointcut.
	 */
	private final Pointcut perClausePointcut;
	// 解析切入点表达式用的，但是真正的解析工作为委托给`org.aspectj.weaver.tools.PointcutExpression`来解析的
	// 若是PerClause的kind：
	// a: 如果是SINGLETON, 则perClausePointcut=Pointcut.TRUE
	// b: 如果非SINGLETON, 则为AspectJExpressionPointcut
	// 具体见AspectMetadata的构造器


	/**
	 * Create a new AspectMetadata instance for the given aspect class.
	 * @param aspectClass the aspect class
	 * @param aspectName the name of the aspect
	 */
	public AspectMetadata(Class<?> aspectClass, String aspectName) {
		// 1. aspectName
		this.aspectName = aspectName;

		Class<?> currClass = aspectClass;
		AjType<?> ajType = null; // 用于接受有@Aspect注解的切面类的AjType值
		
		// 2. 此处会一直遍历到顶层直到Object,找到有一个是Aspect切面就行,因此我们的切面写在父类上也ok的
		// 这里就是为每个currClass创建对应的AjType信息,然后查看这个currClass是否为切面类,使用了@Aspect注解
		while (currClass != Object.class) {
			AjType<?> ajTypeToCheck = AjTypeSystem.getAjType(currClass);
			if (ajTypeToCheck.isAspect()) {
				ajType = ajTypeToCheck;
				break;
			}
			currClass = currClass.getSuperclass(); // 一直找到currClass上有@AspecJ或者确定是一个切面类即可
		}
		
		// 3.由此可见，我们传进来的Class必须是个切面或者切面的子类的~~~
		if (ajType == null) { // 对应的AspectJType
			throw new IllegalArgumentException("Class '" + aspectClass.getName() + "' is not an @AspectJ aspect");
		}
		// 4. 显然Spring AOP目前也不支持优先级的声明。。。
		if (ajType.getDeclarePrecedence().length > 0) {
			throw new IllegalArgumentException("DeclarePrecedence not presently supported in Spring AOP");
		}
		// 5. 获取切面类对应的javaType,并设置类上的ajType值
		this.aspectClass = ajType.getJavaClass();
		this.ajType = ajType;

		// 6. PerClause表示使用的场景
		// singleton：即切面类只会有一个实例；
		// perthis：每个切入点表达式匹配的连接点即@PointCut匹配的方法的说声明的类创建出来的的AOP对象（代理对象）都会创建一个新切面实例；
		// pertarget：每个切入点表达式匹配的连接点对应的目标对象都会创建一个新的切面实例
		// pertypewithin：默认是singleton实例化模型，Schema风格只支持singleton实例化模型，而@AspectJ风格支持这三种实例化模型
		// 切面的处在类型：PerClauseKind  由此可议看出，Spring的AOP目前只支持下面4种
		
		// 用户如何使用:
		// a: singleton - 直接使用@Aspect()指定，即默认就是单例实例化模式，在此就不演示示例了
		// b: perthis - 每个切入点表达式匹配的连接点对应的AOP代理对象都会创建一个新的切面实例，使用@Aspect("perthis(this(com.fsx.HelloService))") 指定切入点表达式；
		//			他将为每个被切入点表达式匹配上的代理对象，都创建一个新的切面实例（此处允许HelloService是接口）
		// c: pertarget：每个切入点表达式匹配的连接点对应的目标对象都会创建一个新的切面实例，使用@Aspect("pertarget(切入点表达式)")指定切入点表达式；  此处要求HelloService不能是接口
		//
		// 另外需要注意一点：若在Spring内要使用perthis和pertarget，请把切面的Scope定义为：prototype
		switch (this.ajType.getPerClause().getKind()) {
			case SINGLETON:
				// 单例，这个表达式返回这个常量 -- 一般默认都是这个值
				this.perClausePointcut = Pointcut.TRUE;
				return;
			case PERTARGET:
			case PERTHIS:
				// PERTARGET和PERTHIS处理方式一样  返回的是AspectJExpressionPointcut
				AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut();
				ajexp.setLocation(aspectClass.getName());
				ajexp.setExpression(findPerClause(aspectClass));
				ajexp.setPointcutDeclarationScope(aspectClass);
				this.perClausePointcut = ajexp;
				return;
			case PERTYPEWITHIN:
				// Works with a type pattern
				// 组成的、合成得切点表达式~~~
				this.perClausePointcut = new ComposablePointcut(new TypePatternClassFilter(findPerClause(aspectClass)));
				return;
			default:
				// 其余AspectJ的perClause的Spring AOP暂时不支持
				throw new AopConfigException("PerClause " + ajType.getPerClause().getKind() + " not supported by Spring AOP for " + aspectClass);
		}
	}

	/**
	 * Extract contents from String of form {@code pertarget(contents)}.
	 */
	private String findPerClause(Class<?> aspectClass) {
		// 1. 获取切面类上的@Aspect中的值
		// 只有 perthis("xx") 或 pertarget("xx") 中的值等等
		// 
		String str = aspectClass.getAnnotation(Aspect.class).value();
		int beginIndex = str.indexOf('(') + 1;
		int endIndex = str.length() - 1;
		return str.substring(beginIndex, endIndex);
	}


	/**
	 * Return AspectJ reflection information.
	 */
	public AjType<?> getAjType() {
		return this.ajType;
	}

	/**
	 * Return the aspect class.
	 */
	public Class<?> getAspectClass() {
		return this.aspectClass;
	}

	/**
	 * Return the aspect name.
	 */
	public String getAspectName() {
		return this.aspectName;
	}

	/**
	 * Return a Spring pointcut expression for a singleton aspect.
	 * (e.g. {@code Pointcut.TRUE} if it's a singleton).
	 */
	public Pointcut getPerClausePointcut() {
		return this.perClausePointcut;
	}

	/**
	 * Return whether the aspect is defined as "perthis" or "pertarget".
	 */
	public boolean isPerThisOrPerTarget() {
		// 判断perThis或者perTarger，最单实例、多实例处理
		PerClauseKind kind = getAjType().getPerClause().getKind();
		return (kind == PerClauseKind.PERTARGET || kind == PerClauseKind.PERTHIS);
	}

	/**
	 * Return whether the aspect is defined as "pertypewithin".
	 */
	public boolean isPerTypeWithin() {
		// 是否是within的
		PerClauseKind kind = getAjType().getPerClause().getKind();
		return (kind == PerClauseKind.PERTYPEWITHIN);
	}

	/**
	 * Return whether the aspect needs to be lazily instantiated.
	 */
	public boolean isLazilyInstantiated() {
		// 只要不是单例的，就都属于Lazy懒加载，延迟实例化的类型~~~~
		return (isPerThisOrPerTarget() || isPerTypeWithin());
	}


	private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
		inputStream.defaultReadObject();
		this.ajType = AjTypeSystem.getAjType(this.aspectClass);
	}

}
