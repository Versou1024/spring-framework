/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.format;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * A factory that creates formatters to format values of fields annotated with a particular
 * {@link Annotation}.
 *
 * <p>For example, a {@code DateTimeFormatAnnotationFormatterFactory} might create a formatter
 * that formats {@code Date} values set on fields annotated with {@code @DateTimeFormat}.
 *
 * @author Keith Donald
 * @since 3.0
 * @param <A> the annotation type that should trigger formatting
 */
public interface AnnotationFormatterFactory<A extends Annotation> {
	// 它是一个工厂，专门创建出处理（格式化）指定字段field上标注有指定注解的。（Spring内助了两个常用注解：@DateTimeFormat和@NumberFormat）
	// 我们常说的，要自定义注解来处理参数的格式化，就需要实现接口来自定义一个处理类。

	/**
	 * The types of fields that may be annotated with the &lt;A&gt; annotation.
	 */
	Set<Class<?>> getFieldTypes();
	// 注解 A 可以注解的的Class类型是哪些

	/**
	 * Get the Printer to print the value of a field of {@code fieldType} annotated with
	 * {@code annotation}.
	 * <p>If the type T the printer accepts is not assignable to {@code fieldType}, a
	 * coercion from {@code fieldType} to T will be attempted before the Printer is invoked.
	 * @param annotation the annotation instance
	 * @param fieldType the type of field that was annotated
	 * @return the printer
	 */
	Printer<?> getPrinter(A annotation, Class<?> fieldType);
	//	参数：
	//		annotation - 注解实例
	//		fieldType – 被注解的字段类型
	//  对标注有指定注解的字段进行格式化输出~~

	/**
	 * Get the Parser to parse a submitted value for a field of {@code fieldType}
	 * annotated with {@code annotation}.
	 * <p>If the object the parser returns is not assignable to {@code fieldType},
	 * a coercion to {@code fieldType} will be attempted before the field is set.
	 * @param annotation the annotation instance
	 * @param fieldType the type of field that was annotated
	 * @return the parser
	 */
	Parser<?> getParser(A annotation, Class<?> fieldType);
	// 对标注有指定注解的字段进行格式化解析~~~

}
