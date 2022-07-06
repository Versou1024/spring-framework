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

package org.springframework.aop.aspectj.autoproxy;

import java.util.Comparator;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJAopUtils;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

/**
 * Orders AspectJ advice/advisors by precedence (<i>not</i> invocation order).
 *
 * <p>Given two pieces of advice, {@code A} and {@code B}:
 * <ul>
 * <li>If {@code A} and {@code B} are defined in different aspects, then the advice
 * in the aspect with the lowest order value has the highest precedence.</li>
 * <li>If {@code A} and {@code B} are defined in the same aspect, if one of
 * {@code A} or {@code B} is a form of <em>after</em> advice, then the advice declared
 * last in the aspect has the highest precedence. If neither {@code A} nor {@code B}
 * is a form of <em>after</em> advice, then the advice declared first in the aspect
 * has the highest precedence.</li>
 * </ul>
 *
 * <p>Important: This comparator is used with AspectJ's
 * {@link org.aspectj.util.PartialOrder PartialOrder} sorting utility. Thus, unlike
 * a normal {@link Comparator}, a return value of {@code 0} from this comparator
 * means we don't care about the ordering, not that the two elements must be sorted
 * identically.
 *
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @since 2.0
 */
class AspectJPrecedenceComparator implements Comparator<Advisor> {
	// 位于  org.springframework.aop.aspectj.autoproxy 包下
	
	// 按优先级排序 AspectJ advice/advisor（不是调用顺序）。
	// 当给出两条advice比如A和B ：
	// 		如果A和B被定义在不同的切面上，那么具有更低order值的切面的中的通知方法具有最高优先级。
	// 		如果A和B定义在同一个切面上，如果A或B中的一个是after advice的形式，则在同一个切面中中after advice的通知增强方法会具有最高优先级。
	// 		如果A和B定义在同一个切面上,但A和B都不是 after advice的一种形式[@After @AfterThrowing @AfterReturning]，则在方面中首先声明的通知具有最高优先级。
	
	// 
	// 重要提示：此比较器与AspectJ的PartialOrder排序实用程序一起使用。
	// 因此，与普通的Comparator不同，此比较器的返回值0意味着我们不关心排序，而不是两个元素必须以相同的方式排序。

	private static final int HIGHER_PRECEDENCE = -1;

	private static final int SAME_PRECEDENCE = 0;

	private static final int LOWER_PRECEDENCE = 1;


	private final Comparator<? super Advisor> advisorComparator;


	/**
	 * Create a default {@code AspectJPrecedenceComparator}.
	 */
	public AspectJPrecedenceComparator() {
		this.advisorComparator = AnnotationAwareOrderComparator.INSTANCE;
	}

	/**
	 * Create an {@code AspectJPrecedenceComparator}, using the given {@link Comparator}
	 * for comparing {@link org.springframework.aop.Advisor} instances.
	 * @param advisorComparator the {@code Comparator} to use for advisors
	 */
	public AspectJPrecedenceComparator(Comparator<? super Advisor> advisorComparator) {
		Assert.notNull(advisorComparator, "Advisor comparator must not be null");
		this.advisorComparator = advisorComparator;
	}


	@Override
	public int compare(Advisor o1, Advisor o2) {
		// 比较方法
		
		// 1. 比较两个Advisor的order顺序,也就是继承的切面的order值
		int advisorPrecedence = this.advisorComparator.compare(o1, o2);
		// 2. 如果连个Advisor也就是继承的切面的order值一样大,就需要检查是否为在同一个切面类中
		// 如果是在同一个切面类,就需要根据是否有后置通知after advice为其提供优先级哦
		if (advisorPrecedence == SAME_PRECEDENCE && declaredInSameAspect(o1, o2)) {
			advisorPrecedence = comparePrecedenceWithinAspect(o1, o2);
		}
		// 3. 否则顺序不变哦
		return advisorPrecedence;
	}

	private int comparePrecedenceWithinAspect(Advisor advisor1, Advisor advisor2) {
		// 用来当Advisor A和B定义在同一个切面上，如果A或B中的一个是after advice的形式，则在同一个切面中中after advice的通知增强方法会具有最高优先级。
		
		boolean oneOrOtherIsAfterAdvice =
				(AspectJAopUtils.isAfterAdvice(advisor1) || AspectJAopUtils.isAfterAdvice(advisor2));
		int adviceDeclarationOrderDelta = getAspectDeclarationOrder(advisor1) - getAspectDeclarationOrder(advisor2);

		if (oneOrOtherIsAfterAdvice) {
			// the advice declared last has higher precedence
			if (adviceDeclarationOrderDelta < 0) {
				// advice1 was declared before advice2
				// so advice1 has lower precedence
				return LOWER_PRECEDENCE;
			}
			else if (adviceDeclarationOrderDelta == 0) {
				return SAME_PRECEDENCE;
			}
			else {
				return HIGHER_PRECEDENCE;
			}
		}
		else {
			// the advice declared first has higher precedence
			if (adviceDeclarationOrderDelta < 0) {
				// advice1 was declared before advice2
				// so advice1 has higher precedence
				return HIGHER_PRECEDENCE;
			}
			else if (adviceDeclarationOrderDelta == 0) {
				return SAME_PRECEDENCE;
			}
			else {
				return LOWER_PRECEDENCE;
			}
		}
	}

	private boolean declaredInSameAspect(Advisor advisor1, Advisor advisor2) {
		// 如果有AspectJPrecedenceInformation -- 一般都有
		// 那就去获取其中的aspectName,也就是@Aspect注解的类的类名是否一样,如果一样,就说明两个Advisor应该是在同一个切面类中不同通知方法
		return (hasAspectName(advisor1) && hasAspectName(advisor2) &&
				getAspectName(advisor1).equals(getAspectName(advisor2)));
	}

	private boolean hasAspectName(Advisor advisor) {
		// 很常见的AspectJAfterAdvice\AspectJAroundAdvice\AspectJAfterReturningAdvice等等都是继承的AspectJPrecedenceInformation
		return (advisor instanceof AspectJPrecedenceInformation ||
				advisor.getAdvice() instanceof AspectJPrecedenceInformation);
	}

	private String getAspectName(Advisor advisor) {
		AspectJPrecedenceInformation precedenceInfo = AspectJAopUtils.getAspectJPrecedenceInformationFor(advisor);
		Assert.state(precedenceInfo != null, () -> "Unresolvable AspectJPrecedenceInformation for " + advisor);
		return precedenceInfo.getAspectName();
	}

	private int getAspectDeclarationOrder(Advisor advisor) {
		AspectJPrecedenceInformation precedenceInfo = AspectJAopUtils.getAspectJPrecedenceInformationFor(advisor);
		return (precedenceInfo != null ? precedenceInfo.getDeclarationOrder() : 0);
	}

}
