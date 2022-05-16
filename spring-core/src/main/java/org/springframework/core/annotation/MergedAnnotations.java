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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Provides access to a collection of merged annotations, usually obtained
 * from a source such as a {@link Class} or {@link Method}.
 *
 * <p>Each merged annotation represents a view where the attribute values may be
 * "merged" from different source values, typically:
 *
 * <ul>
 * <li>Explicit and Implicit {@link AliasFor @AliasFor} declarations on one or
 * more attributes within the annotation</li>
 * <li>Explicit {@link AliasFor @AliasFor} declarations for a meta-annotation</li>
 * <li>Convention based attribute aliases for a meta-annotation</li>
 * <li>From a meta-annotation declaration</li>
 * </ul>
 *
 * <p>For example, a {@code @PostMapping} annotation might be defined as follows:
 *
 * <pre class="code">
 * &#064;Retention(RetentionPolicy.RUNTIME)
 * &#064;RequestMapping(method = RequestMethod.POST)
 * public &#064;interface PostMapping {
 *
 *     &#064;AliasFor(attribute = "path")
 *     String[] value() default {};
 *
 *     &#064;AliasFor(attribute = "value")
 *     String[] path() default {};
 * }
 * </pre>
 *
 * <p>If a method is annotated with {@code @PostMapping("/home")} it will contain
 * merged annotations for both {@code @PostMapping} and the meta-annotation
 * {@code @RequestMapping}. The merged view of the {@code @RequestMapping}
 * annotation will contain the following attributes:
 *
 * <p><table border="1">
 * <tr>
 * <th>Name</th>
 * <th>Value</th>
 * <th>Source</th>
 * </tr>
 * <tr>
 * <td>value</td>
 * <td>"/home"</td>
 * <td>Declared in {@code @PostMapping}</td>
 * </tr>
 * <tr>
 * <td>path</td>
 * <td>"/home"</td>
 * <td>Explicit {@code @AliasFor}</td>
 * </tr>
 * <tr>
 * <td>method</td>
 * <td>RequestMethod.POST</td>
 * <td>Declared in meta-annotation</td>
 * </tr>
 * </table>
 *
 * <p>{@link MergedAnnotations} can be obtained {@linkplain #from(AnnotatedElement)
 * from} any Java {@link AnnotatedElement}. They may also be used for sources that
 * don't use reflection (such as those that directly parse bytecode).
 *
 * <p>Different {@linkplain SearchStrategy search strategies} can be used to locate
 * related source elements that contain the annotations to be aggregated. For
 * example, {@link SearchStrategy#TYPE_HIERARCHY} will search both superclasses and
 * implemented interfaces.
 *
 * <p>From a {@link MergedAnnotations} instance you can either
 * {@linkplain #get(String) get} a single annotation, or {@linkplain #stream()
 * stream all annotations} or just those that match {@linkplain #stream(String)
 * a specific type}. You can also quickly tell if an annotation
 * {@linkplain #isPresent(String) is present}.
 *
 * <p>Here are some typical examples:
 *
 * <pre class="code">
 * // is an annotation present or meta-present?
 * mergedAnnotations.isPresent(ExampleAnnotation.class);
 *
 * // get the merged "value" attribute of ExampleAnnotation (either directly or
 * // meta-present)
 * mergedAnnotations.get(ExampleAnnotation.class).getString("value");
 *
 * // get all meta-annotations but no directly present annotations
 * mergedAnnotations.stream().filter(MergedAnnotation::isMetaPresent);
 *
 * // get all ExampleAnnotation declarations (including any meta-annotations) and
 * // print the merged "value" attributes
 * mergedAnnotations.stream(ExampleAnnotation.class)
 *     .map(mergedAnnotation -&gt; mergedAnnotation.getString("value"))
 *     .forEach(System.out::println);
 * </pre>
 *
 * <p><b>NOTE: The {@code MergedAnnotations} API and its underlying model have
 * been designed for composable annotations in Spring's common component model,
 * with a focus on attribute aliasing and meta-annotation relationships.</b>
 * There is no support for retrieving plain Java annotations with this API;
 * please use standard Java reflection or Spring's {@link AnnotationUtils}
 * for simple annotation retrieval purposes.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 * @see MergedAnnotation
 * @see MergedAnnotationCollectors
 * @see MergedAnnotationPredicates
 * @see MergedAnnotationSelectors
 */
public interface MergedAnnotations extends Iterable<MergedAnnotation<Annotation>> {
	/*
	 * 5.2对MergeAnnotation提供的一个注解操作类：
	 *
	 * 提供对merge annotation集合的访问，这些注解通常从类class或方法method等源获取。
	 * 每个合并注解表示一个视图，其中视图的属性值可以从不同的源值“合并”，通常：
	 * 		显式和隐式@AliasFor注解作为一个注解中的一个或多个属性声明的
	 * 		显式@AliasFor作为注解的元注解声明
	 * 		元注解基于约定的属性别名
	 * 		来自元注解声明
	 * 例如，@PostMapping注解可以定义如下：
	 * @Retention(RetentionPolicy.RUNTIME)
	 * @RequestMapping(method = RequestMethod.POST)
	 * public @interface PostMapping{
	 *     @AliasFor(attribute = "path")
	 *     String[] value() default {};
	 *     @AliasFor(attribute = "value")
	 *     String[] path() default {};
	 * }
	* 如果一个方法用@PostMapping（“/home”）注解，它将包含@PostMapping和元注解@RequestMapping的合并注解。@RequestMapping注解的合并视图将包含以下属性：
	* Name 		Value 					Source
	* value 	"/home" 				Declared in @PostMapping
	* path 		"/home" 				Explicit @AliasFor
	* method 	RequestMethod.POST 	 v	@RequestMapping
	* merged annotation可以从任何Java AnnotationElement中获得。它们也可以不使用反射（例如直接解析字节码ASM）。
	* 可以使用不同的搜索策略来定位要聚合的注解的相关source元素。例如，MergedAnnotations.SearchStrategy.TYPE_HIERARCHY 将搜索超类和实现的接口。
	* 从MergedAnnotations实例中，您可以获取单个注解，也可以stream传输所有注解，或者只传输与特定类型匹配的注解。您还可以快速判断是否存在注释。
	*  mergedAnnotations.isPresent(ExampleAnnotation.class);
	*  mergedAnnotations.get(ExampleAnnotation.class).getString("value");
	*  mergedAnnotations.stream().filter(MergedAnnotation::isMetaPresent);
	*  mergedAnnotations.stream(ExampleAnnotation.class).map(mergedAnnotation -> mergedAnnotation.getString("value"))  .forEach(System.out::println);
	*/

	/**
	 * Determine if the specified annotation is either directly present or
	 * meta-present.
	 * <p>Equivalent to calling {@code get(annotationType).isPresent()}.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to check
	 * @return {@code true} if the annotation is present
	 */
	boolean isPresent(String annotationType);

	/**
	 * Determine if the specified annotation is directly present.
	 * <p>Equivalent to calling {@code get(annotationType).isDirectlyPresent()}.
	 * @param annotationType the annotation type to check
	 * @return {@code true} if the annotation is directly present
	 */
	<A extends Annotation> boolean isDirectlyPresent(Class<A> annotationType);

	/**
	 * Determine if the specified annotation is directly present.
	 * <p>Equivalent to calling {@code get(annotationType).isDirectlyPresent()}.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to check
	 * @return {@code true} if the annotation is directly present
	 */
	boolean isDirectlyPresent(String annotationType);

	/**
	 * Get the {@linkplain MergedAnnotationSelectors#nearest() nearest} matching
	 * annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the annotation type to get
	 * @return a {@link MergedAnnotation} instance
	 */
	<A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType);

	/**
	 * Get the {@linkplain MergedAnnotationSelectors#nearest() nearest} matching
	 * annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the annotation type to get
	 * @param predicate a predicate that must match, or {@code null} if only
	 * type matching is required
	 * @return a {@link MergedAnnotation} instance
	 * @see MergedAnnotationPredicates
	 */
	<A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate);

	/**
	 * Get a matching annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the annotation type to get
	 * @param predicate a predicate that must match, or {@code null} if only
	 * type matching is required
	 * @param selector a selector used to choose the most appropriate annotation
	 * within an aggregate, or {@code null} to select the
	 * {@linkplain MergedAnnotationSelectors#nearest() nearest}
	 * @return a {@link MergedAnnotation} instance
	 * @see MergedAnnotationPredicates
	 * @see MergedAnnotationSelectors
	 */
	<A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			@Nullable MergedAnnotationSelector<A> selector);

	/**
	 * Get the {@linkplain MergedAnnotationSelectors#nearest() nearest} matching
	 * annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to get
	 * @return a {@link MergedAnnotation} instance
	 */
	<A extends Annotation> MergedAnnotation<A> get(String annotationType);

	/**
	 * Get the {@linkplain MergedAnnotationSelectors#nearest() nearest} matching
	 * annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to get
	 * @param predicate a predicate that must match, or {@code null} if only
	 * type matching is required
	 * @return a {@link MergedAnnotation} instance
	 * @see MergedAnnotationPredicates
	 */
	<A extends Annotation> MergedAnnotation<A> get(String annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate);

	/**
	 * Get a matching annotation or meta-annotation of the specified type, or
	 * {@link MergedAnnotation#missing()} if none is present.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to get
	 * @param predicate a predicate that must match, or {@code null} if only
	 * type matching is required
	 * @param selector a selector used to choose the most appropriate annotation
	 * within an aggregate, or {@code null} to select the
	 * {@linkplain MergedAnnotationSelectors#nearest() nearest}
	 * @return a {@link MergedAnnotation} instance
	 * @see MergedAnnotationPredicates
	 * @see MergedAnnotationSelectors
	 */
	<A extends Annotation> MergedAnnotation<A> get(String annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			@Nullable MergedAnnotationSelector<A> selector);

	/**
	 * Stream all annotations and meta-annotations that match the specified
	 * type. The resulting stream follows the same ordering rules as
	 * {@link #stream()}.
	 * @param annotationType the annotation type to match
	 * @return a stream of matching annotations
	 */
	<A extends Annotation> Stream<MergedAnnotation<A>> stream(Class<A> annotationType);

	/**
	 * Stream all annotations and meta-annotations that match the specified
	 * type. The resulting stream follows the same ordering rules as
	 * {@link #stream()}.
	 * @param annotationType the fully qualified class name of the annotation type
	 * to match
	 * @return a stream of matching annotations
	 */
	<A extends Annotation> Stream<MergedAnnotation<A>> stream(String annotationType); // 匹配指定的annotationType

	/**
	 * Stream all annotations and meta-annotations contained in this collection.
	 * The resulting stream is ordered first by the
	 * {@linkplain MergedAnnotation#getAggregateIndex() aggregate index} and then
	 * by the annotation distance (with the closest annotations first). This ordering
	 * means that, for most use-cases, the most suitable annotations appear
	 * earliest in the stream.
	 * @return a stream of annotations
	 */
	Stream<MergedAnnotation<Annotation>> stream(); // 所有的注解遍历


	/**
	 * Create a new {@link MergedAnnotations} instance containing all
	 * annotations and meta-annotations from the specified element. The
	 * resulting instance will not include any inherited annotations. If you
	 * want to include those as well you should use
	 * {@link #from(AnnotatedElement, SearchStrategy)} with an appropriate
	 * {@link SearchStrategy}.
	 * @param element the source element
	 * @return a {@link MergedAnnotations} instance containing the element's
	 * annotations
	 */
	static MergedAnnotations from(AnnotatedElement element) {
		// 从类上直接查找所有的注解合并
		return from(element, SearchStrategy.DIRECT);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance containing all
	 * annotations and meta-annotations from the specified element and,
	 * depending on the {@link SearchStrategy}, related inherited elements.
	 * @param element the source element
	 * @param searchStrategy the search strategy to use
	 * @return a {@link MergedAnnotations} instance containing the merged
	 * element annotations
	 */
	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy) {
		// 从类上根据搜索策略查找所有的注解合并
		return from(element, searchStrategy, RepeatableContainers.standardRepeatables());
	}

	/**
	 * Create a new {@link MergedAnnotations} instance containing all
	 * annotations and meta-annotations from the specified element and,
	 * depending on the {@link SearchStrategy}, related inherited elements.
	 * @param element the source element
	 * @param searchStrategy the search strategy to use
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the element annotations or the meta-annotations
	 * @return a {@link MergedAnnotations} instance containing the merged
	 * element annotations
	 */
	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy, RepeatableContainers repeatableContainers) {
		// 从类上根据搜索策略查找所有的注解合并，重复容器
		return from(element, searchStrategy, repeatableContainers, AnnotationFilter.PLAIN);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance containing all
	 * annotations and meta-annotations from the specified element and,
	 * depending on the {@link SearchStrategy}, related inherited elements.
	 * @param element the source element
	 * @param searchStrategy the search strategy to use
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the element annotations or the meta-annotations
	 * @param annotationFilter an annotation filter used to restrict the
	 * annotations considered
	 * @return a {@link MergedAnnotations} instance containing the merged
	 * annotations for the supplied element
	 */
	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		Assert.notNull(repeatableContainers, "RepeatableContainers must not be null");
		Assert.notNull(annotationFilter, "AnnotationFilter must not be null");
		return TypeMappedAnnotations.from(element, searchStrategy, repeatableContainers, annotationFilter);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance from the specified
	 * annotations.
	 * @param annotations the annotations to include
	 * @return a {@link MergedAnnotations} instance containing the annotations
	 * @see #from(Object, Annotation...)
	 */
	static MergedAnnotations from(Annotation... annotations) {
		return from(annotations, annotations);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance from the specified
	 * annotations.
	 * @param source the source for the annotations. This source is used only
	 * for information and logging. It does not need to <em>actually</em>
	 * contain the specified annotations, and it will not be searched.
	 * @param annotations the annotations to include
	 * @return a {@link MergedAnnotations} instance containing the annotations
	 * @see #from(Annotation...)
	 * @see #from(AnnotatedElement)
	 */
	static MergedAnnotations from(Object source, Annotation... annotations) {
		return from(source, annotations, RepeatableContainers.standardRepeatables());
	}

	/**
	 * Create a new {@link MergedAnnotations} instance from the specified
	 * annotations.
	 * @param source the source for the annotations. This source is used only
	 * for information and logging. It does not need to <em>actually</em>
	 * contain the specified annotations, and it will not be searched.
	 * @param annotations the annotations to include
	 * @param repeatableContainers the repeatable containers that may be used by
	 * meta-annotations
	 * @return a {@link MergedAnnotations} instance containing the annotations
	 */
	static MergedAnnotations from(Object source, Annotation[] annotations, RepeatableContainers repeatableContainers) {
		return from(source, annotations, repeatableContainers, AnnotationFilter.PLAIN);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance from the specified
	 * annotations.
	 * @param source the source for the annotations. This source is used only
	 * for information and logging. It does not need to <em>actually</em>
	 * contain the specified annotations, and it will not be searched.
	 * @param annotations the annotations to include
	 * @param repeatableContainers the repeatable containers that may be used by
	 * meta-annotations
	 * @param annotationFilter an annotation filter used to restrict the
	 * annotations considered
	 * @return a {@link MergedAnnotations} instance containing the annotations
	 */
	static MergedAnnotations from(Object source, Annotation[] annotations,
			RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		Assert.notNull(repeatableContainers, "RepeatableContainers must not be null");
		Assert.notNull(annotationFilter, "AnnotationFilter must not be null");
		return TypeMappedAnnotations.from(source, annotations, repeatableContainers, annotationFilter);
	}

	/**
	 * Create a new {@link MergedAnnotations} instance from the specified
	 * collection of directly present annotations. This method allows a
	 * {@link MergedAnnotations} instance to be created from annotations that
	 * are not necessarily loaded using reflection. The provided annotations
	 * must all be {@link MergedAnnotation#isDirectlyPresent() directly present}
	 * and must have an {@link MergedAnnotation#getAggregateIndex() aggregate
	 * index} of {@code 0}.
	 * <p>The resulting {@link MergedAnnotations} instance will contain both the
	 * specified annotations, and any meta-annotations that can be read using
	 * reflection.
	 * @param annotations the annotations to include
	 * @return a {@link MergedAnnotations} instance containing the annotations
	 * @see MergedAnnotation#of(ClassLoader, Object, Class, java.util.Map)
	 */
	static MergedAnnotations of(Collection<MergedAnnotation<?>> annotations) {
		// 将一组annotations合并为MergedAnnotations
		return MergedAnnotationsCollection.of(annotations);
	}


	/**
	 * Search strategies supported by
	 * {@link MergedAnnotations#from(AnnotatedElement, SearchStrategy)}.
	 *
	 * <p>Each strategy creates a different set of aggregates that will be
	 * combined to create the final {@link MergedAnnotations}.
	 */
	enum SearchStrategy {
		// 注解搜索策略
		// 直接搜索、搜索可继承的注解、搜索超类的注解
		//  		//仅查找直接注解，不考虑@Inherited，超类或者接口。
		//		DIRECT,
		//         //查找所有直接注解，或者superclass上使用@Inherited的注解。仅适用于Class。该搜索策略不搜索接口
		//		INHERITED_ANNOTATIONS,
		//        //查找所有直接注解，或者超类的注解，与INHERITED_ANNOTATIONS相似，但超类的注解上不需要应用@Inherited，就可以获取。该搜索策略不搜索接口
		//		SUPERCLASS,
		//        // 搜索整个继承体系，包括class、superClass、继承interface三者上所有的注解只和
		//		TYPE_HIERARCHY,
		//        // 在TYPE_HIERARCHY上，同时又查找内部类的注解
		//		TYPE_HIERARCHY_AND_ENCLOSING_CLASSES

		/**
		 * Find only directly declared annotations, without considering
		 * {@link Inherited @Inherited} annotations and without searching
		 * superclasses or implemented interfaces.
		 */
		DIRECT,

		/**
		 * Find all directly declared annotations as well as any
		 * {@link Inherited @Inherited} superclass annotations. This strategy
		 * is only really useful when used with {@link Class} types since the
		 * {@link Inherited @Inherited} annotation is ignored for all other
		 * {@linkplain AnnotatedElement annotated elements}. This strategy does
		 * not search implemented interfaces.
		 */
		INHERITED_ANNOTATIONS,

		/**
		 * Find all directly declared and superclass annotations. This strategy
		 * is similar to {@link #INHERITED_ANNOTATIONS} except the annotations
		 * do not need to be meta-annotated with {@link Inherited @Inherited}.
		 * This strategy does not search implemented interfaces.
		 */
		SUPERCLASS,

		/**
		 * Perform a full search of the entire type hierarchy, including
		 * superclasses and implemented interfaces. Superclass annotations do
		 * not need to be meta-annotated with {@link Inherited @Inherited}.
		 */
		TYPE_HIERARCHY,

		/**
		 * Perform a full search of the entire type hierarchy on the source
		 * <em>and</em> any enclosing classes. This strategy is similar to
		 * {@link #TYPE_HIERARCHY} except that {@linkplain Class#getEnclosingClass()
		 * enclosing classes} are also searched. Superclass annotations do not
		 * need to be meta-annotated with {@link Inherited @Inherited}. When
		 * searching a {@link Method} source, this strategy is identical to
		 * {@link #TYPE_HIERARCHY}.
		 */
		TYPE_HIERARCHY_AND_ENCLOSING_CLASSES
	}

}
