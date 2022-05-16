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

package org.springframework.core.type;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

/**
 * Defines access to the annotations of a specific type ({@link AnnotationMetadata class}
 * or {@link MethodMetadata method}), in a form that does not necessarily require the
 * class-loading.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Mark Pollack
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 4.0
 * @see AnnotationMetadata
 * @see MethodMetadata
 */
public interface AnnotatedTypeMetadata {
	/*
	 * Spring元数据的顶层接口：AnnotatedTypeMetadata：对注解元素的封装适配
	 * 主要就是获取注解上的属性值： Map<String, Object>、MultiValueMap<String, Object>、
	 *
	 * // 根据“全类名”判断是否被指定 直接注解或元注解 标注
	 * boolean isAnnotated(String annotationName);
	 * // 根据”全类名“获取所有注解属性（包括元注解）
	 * Map<String, Object> getAnnotationAttributes(String annotationName);
	 * // 同上，但是第二个参数传 true 时会把属性中对应值为 Class 的值转为 字符串，避免需要预先加载对应 Class
	 * Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString);
	 * // 同上，MultiValueMap 是一个 key 可以对应多个 value 的变种 map
	 * MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName);
	 * MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString);
	 *
	 * AnnotatedTypeMetadata 用于修饰带有注解的元素的元数据
	 * 因此被AnnotationMetadata、MethodMetadata 所使用，其中AnnotationMetadata就继承了ClassMetadata
	 */

	/**
	 * Return annotation details based on the direct annotations of the
	 * underlying element.
	 * @return merged annotations based on the direct annotations
	 * @since 5.2
	 */
	MergedAnnotations getAnnotations();
	// 返回注解的详细细节，基于元素的直接元素

	/**
	 * Determine whether the underlying element has an annotation or meta-annotation
	 * of the given type defined.
	 * <p>If this method returns {@code true}, then
	 * {@link #getAnnotationAttributes} will return a non-null Map.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return whether a matching annotation is defined
	 */
	default boolean isAnnotated(String annotationName) {
		// 此元素是否标注有此注解 [从注解、元注解上搜索]
		// annotationName：注解全类名
		return getAnnotations().isPresent(annotationName);
	}

	/**
	 * Retrieve the attributes of the annotation of the given type, if any (i.e. if
	 * defined on the underlying element, as direct annotation or meta-annotation),
	 * also taking attribute overrides on composed annotations into account.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return a Map of attributes, with the attribute name as key (e.g. "value")
	 * and the defined attribute value as Map value. This return value will be
	 * {@code null} if no matching annotation is defined.
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName) {
		// 这个就厉害了：取得指定类型注解的所有的属性 - 值（k-v） [ 从注解和元注解获取]
		// annotationName：注解全类名
		// classValuesAsString：若是true表示 Class用它的字符串的全类名来表示。这样可以避免Class被提前加载
		return getAnnotationAttributes(annotationName, false);
	}

	/**
	 * Retrieve the attributes of the annotation of the given type, if any (i.e. if
	 * defined on the underlying element, as direct annotation or meta-annotation),
	 * also taking attribute overrides on composed annotations into account.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @param classValuesAsString whether to convert class references to String
	 * class names for exposure as values in the returned Map, instead of Class
	 * references which might potentially have to be loaded first
	 * @return a Map of attributes, with the attribute name as key (e.g. "value")
	 * and the defined attribute value as Map value. This return value will be
	 * {@code null} if no matching annotation is defined.
	 */
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		// 获取指定类型的注解的属性，（前提是如果有的话，会从直接的注解、以及元注解中获取）
		// 注意：这个方法还考虑了组合属性的属性覆盖

		MergedAnnotation<Annotation> annotation = getAnnotations().get(annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared());
		if (!annotation.isPresent()) {
			return null;
		}
		return annotation.asAnnotationAttributes(Adapt.values(classValuesAsString, true));
	}

	/**
	 * Retrieve all attributes of all annotations of the given type, if any (i.e. if
	 * defined on the underlying element, as direct annotation or meta-annotation).
	 * Note that this variant does <i>not</i> take attribute overrides into account.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return a MultiMap of attributes, with the attribute name as key (e.g. "value")
	 * and a list of the defined attribute values as Map value. This return value will
	 * be {@code null} if no matching annotation is defined.
	 * @see #getAllAnnotationAttributes(String, boolean)
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName) {
		// 获取所有的属性对于给定注解名字的所有注解，(前提是如果有的话，不管元注解还是直接注解都会取出来）
		// 请注意：此变体方法不考虑属性覆盖哦

		return getAllAnnotationAttributes(annotationName, false);
	}

	/**
	 * Retrieve all attributes of all annotations of the given type, if any (i.e. if
	 * defined on the underlying element, as direct annotation or meta-annotation).
	 * Note that this variant does <i>not</i> take attribute overrides into account.
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @param classValuesAsString  whether to convert class references to String
	 * @return a MultiMap of attributes, with the attribute name as key (e.g. "value")
	 * and a list of the defined attribute values as Map value. This return value will
	 * be {@code null} if no matching annotation is defined.
	 * @see #getAllAnnotationAttributes(String)
	 */
	@Nullable
	default MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		// 获取所有的属性对于给定注解名字的所有注解，(前提是如果有的话，不管元注解还是直接注解都会取出来）
		// 请注意：此变体方法不考虑属性覆盖哦，因为是MultiValueMap格式

		Adapt[] adaptations = Adapt.values(classValuesAsString, true);
		return getAnnotations().stream(annotationName)
				.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
				.map(MergedAnnotation::withNonMergedAttributes)
				.collect(MergedAnnotationCollectors.toMultiValueMap(map ->
						map.isEmpty() ? null : map, adaptations));
	}

}
