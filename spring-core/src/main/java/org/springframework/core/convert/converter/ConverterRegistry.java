/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.core.convert.converter;

/**
 * For registering converters with a type conversion system.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
public interface ConverterRegistry {
	// 使用ConverterRegistry可以使我们对类型转换器做一个统一的注册。正如前言所说的，
	// 要实现自己的类型转换逻辑我们可以实现Converter接口、ConverterFactory接口和GenericConverter接口，
	// ConverterRegistry接口就分别为这三种类型提供了对应的注册方法，至于里面的逻辑就可以发挥自己的设计能力进行设计实现了。
	//
	// 通过ConverterAdapter或者ConverterFactoryAdapter最后都会转化成GenericConverter，
	// 我想应该是因为这种converter是最通用的原因吧
	// 一般而言：我们在实现ConversionService接口的时候也会实现ConverterRegistry接口

	/**
	 * Add a plain converter to this registry.
	 * The convertible source/target type pair is derived from the Converter's parameterized types.
	 * @throws IllegalArgumentException if the parameterized types could not be resolved
	 */
	void addConverter(Converter<?, ?> converter);

	/**
	 * Add a plain converter to this registry.
	 * The convertible source/target type pair is specified explicitly.
	 * <p>Allows for a Converter to be reused for multiple distinct pairs without
	 * having to create a Converter class for each pair.
	 * @since 3.1
	 */
	<S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter);

	/**
	 * Add a generic converter to this registry.
	 */
	void addConverter(GenericConverter converter);

	/**
	 * Add a ranged converter factory to this registry.
	 * The convertible source/target type pair is derived from the ConverterFactory's parameterized types.
	 * @throws IllegalArgumentException if the parameterized types could not be resolved
	 */
	void addConverterFactory(ConverterFactory<?, ?> factory);

	/**
	 * Remove any converters from {@code sourceType} to {@code targetType}.
	 * @param sourceType the source type
	 * @param targetType the target type
	 */
	void removeConvertible(Class<?> sourceType, Class<?> targetType);

}
