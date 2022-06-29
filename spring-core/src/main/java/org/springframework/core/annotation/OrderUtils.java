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

package org.springframework.core.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * General utility for determining the order of an object based on its type declaration.
 * Handles Spring's {@link Order} annotation as well as {@link javax.annotation.Priority}.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 * @see Order
 * @see javax.annotation.Priority
 */
public abstract class OrderUtils {
	// 根据对象类型声明确定对象顺序的通用实用程序。处理 Spring 的Order注释以及javax.annotation.Priority

	/** Cache marker for a non-annotated Class. */
	private static final Object NOT_ANNOTATED = new Object(); // 标记对象 -- 表示没有对应的缓存

	private static final String JAVAX_PRIORITY_ANNOTATION = "javax.annotation.Priority";

	/** Cache for @Order value (or NOT_ANNOTATED marker) per Class. */
	private static final Map<AnnotatedElement, Object> orderCache = new ConcurrentReferenceHashMap<>(64);
	// 全局缓存


	/**
	 * Return the order on the specified {@code type}, or the specified
	 * default value if none can be found.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the priority value, or the specified default order if none can be found
	 * @since 5.0
	 * @see #getPriority(Class)
	 */
	public static int getOrder(Class<?> type, int defaultOrder) {
		Integer order = getOrder(type);
		return (order != null ? order : defaultOrder);
	}

	/**
	 * Return the order on the specified {@code type}, or the specified
	 * default value if none can be found.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the priority value, or the specified default order if none can be found
	 * @see #getPriority(Class)
	 */
	@Nullable
	public static Integer getOrder(Class<?> type, @Nullable Integer defaultOrder) {
		Integer order = getOrder(type);
		return (order != null ? order : defaultOrder);
	}

	/**
	 * Return the order on the specified {@code type}.
	 * <p>Takes care of {@link Order @Order} and {@code @javax.annotation.Priority}.
	 * @param type the type to handle
	 * @return the order value, or {@code null} if none can be found
	 * @see #getPriority(Class)
	 */
	@Nullable
	public static Integer getOrder(Class<?> type) {
		return getOrderFromAnnotations(type, MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY));
	}

	/**
	 * Return the order from the specified annotations collection.
	 * <p>Takes care of {@link Order @Order} and
	 * {@code @javax.annotation.Priority}.
	 * @param element the source element
	 * @param annotations the annotation to consider
	 * @return the order value, or {@code null} if none can be found
	 */
	@Nullable
	static Integer getOrderFromAnnotations(AnnotatedElement element, MergedAnnotations annotations) {
		if (!(element instanceof Class)) {
			return findOrder(annotations);
		}
		// 2. 缓存命中,强转后返回 -- 强转的原因
		// 无@Order注解时存储的是NOT_ANNOTATED,有@Order注解时存储的是对应的Integer
		// 因为Map结构不允许存储null值,就只能使用标记对象Object类型的NOT_ANNOTATED
		Object cached = orderCache.get(element);
		if (cached != null) {
			return (cached instanceof Integer ? (Integer) cached : null);
		}
		// 3. 缓存未命中,尝试findOrder(annotations),并存入缓存
		Integer result = findOrder(annotations);
		orderCache.put(element, result != null ? result : NOT_ANNOTATED);
		return result;
	}

	@Nullable
	private static Integer findOrder(MergedAnnotations annotations) {
		// @Order注解 > @Priority 的优先级
		
		// 1. 获取其中的@Order接口
		MergedAnnotation<Order> orderAnnotation = annotations.get(Order.class);
		if (orderAnnotation.isPresent()) {
			return orderAnnotation.getInt(MergedAnnotation.VALUE);
		}
		// 2. 获取其中的@javax.annotation.Priority注解
		MergedAnnotation<?> priorityAnnotation = annotations.get(JAVAX_PRIORITY_ANNOTATION);
		if (priorityAnnotation.isPresent()) {
			return priorityAnnotation.getInt(MergedAnnotation.VALUE);
		}
		return null;
	}

	/**
	 * Return the value of the {@code javax.annotation.Priority} annotation
	 * declared on the specified type, or {@code null} if none.
	 * @param type the type to handle
	 * @return the priority value if the annotation is declared, or {@code null} if none
	 */
	@Nullable
	public static Integer getPriority(Class<?> type) {
		return MergedAnnotations.from(type, SearchStrategy.TYPE_HIERARCHY).get(JAVAX_PRIORITY_ANNOTATION)
				.getValue(MergedAnnotation.VALUE, Integer.class).orElse(null);
	}

}
