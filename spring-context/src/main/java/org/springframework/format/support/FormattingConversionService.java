/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.format.support;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * A {@link org.springframework.core.convert.ConversionService} implementation
 * designed to be configured as a {@link FormatterRegistry}.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
public class FormattingConversionService extends GenericConversionService implements FormatterRegistry, EmbeddedValueResolverAware {
	// 注意: 一下继承关系哈
	// FormatterRegistry extends ConverterRegistry
	// ConfigurableConversionService extends ConversionService, ConverterRegistry
	// GenericConversionService implements ConfigurableConversionService
	// FormattingConversionService extends GenericConversionService implements FormatterRegistry,
	// 因此 FormattingConversionService 继承的GenericConversionService已经有转换器管理功能\并且提供转换的能力啦
	// 需要完成FormatterRegistry的格式化器注册功能即可
	// 那FormattingConversionService需要类似ConversionService这种接口的功能嘛?[以此提供转换器的转换功能]
	// 不需要这里将Printer\Pasrser\Formatter都转换为了GenericConverter,然后委托给了GenericConversionService处理注册
	// 因此实际Formatter的format和parse的功能都被适配器PrinterConverter以及ParserConvert给代替到Converter的convert功能上啦

	// @since 3.0  它继承自GenericConversionService ，所以它能对Converter进行一系列的操作~~~
	// 实现了接口FormatterRegistry，所以它也可以注册格式化器了
	// 实现了EmbeddedValueResolverAware，所以它还能有非常强大的功能：处理占位

	@Nullable
	private StringValueResolver embeddedValueResolver; // 解析占位符

	private final Map<AnnotationConverterKey, GenericConverter> cachedPrinters = new ConcurrentHashMap<>(64);

	private final Map<AnnotationConverterKey, GenericConverter> cachedParsers = new ConcurrentHashMap<>(64);


	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}


	@Override
	public void addPrinter(Printer<?> printer) {
		// 1. 获取printer中的泛型的参数化class值
		Class<?> fieldType = getFieldType(printer, Printer.class);
		// 2. 转换为适配器PrinterConverter,然后调用超类GenericConversionService#addConverter(),加入到ConverterReigistry中
		addConverter(new PrinterConverter(fieldType, printer, this));
	}

	@Override
	public void addParser(Parser<?> parser) {
		Class<?> fieldType = getFieldType(parser, Parser.class);
		addConverter(new ParserConverter(fieldType, parser, this));
	}

	@Override
	public void addFormatter(Formatter<?> formatter) {
		addFormatterForFieldType(getFieldType(formatter), formatter);
	}

	@Override
	public void addFormatterForFieldType(Class<?> fieldType, Formatter<?> formatter) {
		// 对于 formatter 需要转换为 Printer 以及 Parser 两种都支持
		addConverter(new PrinterConverter(fieldType, formatter, this));
		addConverter(new ParserConverter(fieldType, formatter, this));
	}

	@Override
	public void addFormatterForFieldType(Class<?> fieldType, Printer<?> printer, Parser<?> parser) {
		addConverter(new PrinterConverter(fieldType, printer, this));
		addConverter(new ParserConverter(fieldType, parser, this));
	}

	// 哪怕你是一个AnnotationFormatterFactory，最终也是被适配成了GenericConverter（ConditionalGenericConverter）
	@Override
	public void addFormatterForFieldAnnotation(AnnotationFormatterFactory<? extends Annotation> annotationFormatterFactory) {
		// 1. 解析 annotationFormatterFactory 中的参数泛型 -- 注意:是一个注解
		Class<? extends Annotation> annotationType = getAnnotationType(annotationFormatterFactory);
		// 2. 如果 AnnotationFormatterFactory 实现了 EmbeddedValueResolverAware,就需要将embeddedValueResolver设置进去
		if (this.embeddedValueResolver != null && annotationFormatterFactory instanceof EmbeddedValueResolverAware) {
			((EmbeddedValueResolverAware) annotationFormatterFactory).setEmbeddedValueResolver(this.embeddedValueResolver);
		}
		// 3. 获取这个注解 annotationType 在 annotationFormatterFactory 支持注解的class集合
		Set<Class<?>> fieldTypes = annotationFormatterFactory.getFieldTypes();
		for (Class<?> fieldType : fieldTypes) {
			// 4. 为其分别生成AnnotationPrinterConverter\AnnotationParserConverter
			addConverter(new AnnotationPrinterConverter(annotationType, annotationFormatterFactory, fieldType)); // sourceType 为 fieldType
			addConverter(new AnnotationParserConverter(annotationType, annotationFormatterFactory, fieldType)); // targetType 为 fieldType
		}
	}


	static Class<?> getFieldType(Formatter<?> formatter) {
		return getFieldType(formatter, Formatter.class);
	}

	private static <T> Class<?> getFieldType(T instance, Class<T> genericInterface) {
		Class<?> fieldType = GenericTypeResolver.resolveTypeArgument(instance.getClass(), genericInterface);
		if (fieldType == null && instance instanceof DecoratingProxy) {
			fieldType = GenericTypeResolver.resolveTypeArgument(
					((DecoratingProxy) instance).getDecoratedClass(), genericInterface);
		}
		Assert.notNull(fieldType, () -> "Unable to extract the parameterized field type from " +
					ClassUtils.getShortName(genericInterface) + " [" + instance.getClass().getName() +
					"]; does the class parameterize the <T> generic type?");
		return fieldType;
	}

	@SuppressWarnings("unchecked")
	static Class<? extends Annotation> getAnnotationType(AnnotationFormatterFactory<? extends Annotation> factory) {
		Class<? extends Annotation> annotationType = (Class<? extends Annotation>)
				GenericTypeResolver.resolveTypeArgument(factory.getClass(), AnnotationFormatterFactory.class);
		if (annotationType == null) {
			throw new IllegalArgumentException("Unable to extract parameterized Annotation type argument from " +
					"AnnotationFormatterFactory [" + factory.getClass().getName() +
					"]; does the factory parameterize the <A extends Annotation> generic type?");
		}
		return annotationType;
	}


	private static class PrinterConverter implements GenericConverter {
		// 将 Printer 转换为 GenericConverter
		// Printer 是将 fieldType 类型转换为 String 类型的

		private final Class<?> fieldType;

		private final TypeDescriptor printerObjectType;

		@SuppressWarnings("rawtypes")
		private final Printer printer;

		private final ConversionService conversionService;

		public PrinterConverter(Class<?> fieldType, Printer<?> printer, ConversionService conversionService) {
			this.fieldType = fieldType;
			this.printerObjectType = TypeDescriptor.valueOf(resolvePrinterObjectType(printer));
			this.printer = printer;
			this.conversionService = conversionService;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			// source = fieldType
			// target = String
			return Collections.singleton(new ConvertiblePair(this.fieldType, String.class));
		}

		@Override
		@SuppressWarnings("unchecked")
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 如果 sourceType 不是 parser 中的参数化泛型的超类
			if (!sourceType.isAssignableTo(this.printerObjectType)) {
				// 直接使用 conversionService 继续做转换
				// 会忽略掉原本的 printer
				source = this.conversionService.convert(source, sourceType, this.printerObjectType);
			}
			// 如果 sourceType 就是这个 printer 可以格式化的类型
			if (source == null) {
				return "";
			}
			// 调用print方法
			return this.printer.print(source, LocaleContextHolder.getLocale());
		}

		@Nullable
		private Class<?> resolvePrinterObjectType(Printer<?> printer) {
			// 解析处 Printer<?> 中唯一的泛型值
			return GenericTypeResolver.resolveTypeArgument(printer.getClass(), Printer.class);
		}

		@Override
		public String toString() {
			return (this.fieldType.getName() + " -> " + String.class.getName() + " : " + this.printer);
		}
	}


	private static class ParserConverter implements GenericConverter {
		// 适配器 -- 将Parser适配为Converter类型的

		private final Class<?> fieldType;

		private final Parser<?> parser;

		private final ConversionService conversionService;

		public ParserConverter(Class<?> fieldType, Parser<?> parser, ConversionService conversionService) {
			this.fieldType = fieldType;
			this.parser = parser;
			this.conversionService = conversionService;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			// sourceType = String
			// 她让getType = fieldType
			return Collections.singleton(new ConvertiblePair(String.class, this.fieldType));
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 强转为String
			String text = (String) source;
			if (!StringUtils.hasText(text)) {
				return null;
			}
			Object result;
			try {
				// 调用parser进行解析
				result = this.parser.parse(text, LocaleContextHolder.getLocale());
			}
			catch (IllegalArgumentException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Parse attempt failed for value [" + text + "]", ex);
			}
			TypeDescriptor resultType = TypeDescriptor.valueOf(result.getClass());
			// resultType 并不是目标类型的
			if (!resultType.isAssignableTo(targetType)) {
				// 调用 conversionService 对格式化器parser处理过后的 result 继续转换
				result = this.conversionService.convert(result, resultType, targetType);
			}
			return result;
		}

		@Override
		public String toString() {
			return (String.class.getName() + " -> " + this.fieldType.getName() + ": " + this.parser);
		}
	}


	private class AnnotationPrinterConverter implements ConditionalGenericConverter {

		// annotationFormatterFactory 支持的足迹
		private final Class<? extends Annotation> annotationType;

		@SuppressWarnings("rawtypes")
		private final AnnotationFormatterFactory annotationFormatterFactory;

		private final Class<?> fieldType;

		public AnnotationPrinterConverter(Class<? extends Annotation> annotationType,
				AnnotationFormatterFactory<?> annotationFormatterFactory, Class<?> fieldType) {

			this.annotationType = annotationType;
			this.annotationFormatterFactory = annotationFormatterFactory;
			this.fieldType = fieldType;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(new ConvertiblePair(this.fieldType, String.class));
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 匹配条件 -- sourceType上有annotationType注解
			return sourceType.hasAnnotation(this.annotationType);
		}

		@Override
		@SuppressWarnings("unchecked")
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 1. 检查sourceType上是否指定注解
			Annotation ann = sourceType.getAnnotation(this.annotationType);
			if (ann == null) {
				throw new IllegalStateException(
						"Expected [" + this.annotationType.getName() + "] to be present on " + sourceType);
			}
			// 2. 缓存
			AnnotationConverterKey converterKey = new AnnotationConverterKey(ann, sourceType.getObjectType());
			GenericConverter converter = cachedPrinters.get(converterKey);
			if (converter == null) {
				// 2.1 缓存未命中,使用annotationFormatterFactory获取printer
				// 需要传入 注解annotation\filedType
				Printer<?> printer = this.annotationFormatterFactory.getPrinter(
						converterKey.getAnnotation(), converterKey.getFieldType());
				// 2.2 注意:获取到的printer并不会立即被使用
				// 而是转换为适配器 PrinterConverter 后 加入到缓存中
				converter = new PrinterConverter(this.fieldType, printer, FormattingConversionService.this);
				cachedPrinters.put(converterKey, converter);
			}
			// 2.3 然后开始做convert实际就是上面的 printer的print()方法
			return converter.convert(source, sourceType, targetType);
		}

		@Override
		public String toString() {
			return ("@" + this.annotationType.getName() + " " + this.fieldType.getName() + " -> " +
					String.class.getName() + ": " + this.annotationFormatterFactory);
		}
	}


	private class AnnotationParserConverter implements ConditionalGenericConverter {

		private final Class<? extends Annotation> annotationType;

		@SuppressWarnings("rawtypes")
		private final AnnotationFormatterFactory annotationFormatterFactory;

		private final Class<?> fieldType;

		public AnnotationParserConverter(Class<? extends Annotation> annotationType,
				AnnotationFormatterFactory<?> annotationFormatterFactory, Class<?> fieldType) {

			this.annotationType = annotationType;
			this.annotationFormatterFactory = annotationFormatterFactory;
			this.fieldType = fieldType;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(new ConvertiblePair(String.class, this.fieldType));
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			return targetType.hasAnnotation(this.annotationType);
		}

		@Override
		@SuppressWarnings("unchecked")
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			Annotation ann = targetType.getAnnotation(this.annotationType);
			if (ann == null) {
				throw new IllegalStateException(
						"Expected [" + this.annotationType.getName() + "] to be present on " + targetType);
			}
			AnnotationConverterKey converterKey = new AnnotationConverterKey(ann, targetType.getObjectType());
			GenericConverter converter = cachedParsers.get(converterKey);
			if (converter == null) {
				Parser<?> parser = this.annotationFormatterFactory.getParser(
						converterKey.getAnnotation(), converterKey.getFieldType());
				converter = new ParserConverter(this.fieldType, parser, FormattingConversionService.this);
				cachedParsers.put(converterKey, converter);
			}
			return converter.convert(source, sourceType, targetType);
		}

		@Override
		public String toString() {
			return (String.class.getName() + " -> @" + this.annotationType.getName() + " " +
					this.fieldType.getName() + ": " + this.annotationFormatterFactory);
		}
	}


	private static class AnnotationConverterKey {

		private final Annotation annotation;

		private final Class<?> fieldType;

		public AnnotationConverterKey(Annotation annotation, Class<?> fieldType) {
			this.annotation = annotation;
			this.fieldType = fieldType;
		}

		public Annotation getAnnotation() {
			return this.annotation;
		}

		public Class<?> getFieldType() {
			return this.fieldType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AnnotationConverterKey)) {
				return false;
			}
			AnnotationConverterKey otherKey = (AnnotationConverterKey) other;
			return (this.fieldType == otherKey.fieldType && this.annotation.equals(otherKey.annotation));
		}

		@Override
		public int hashCode() {
			return (this.fieldType.hashCode() * 29 + this.annotation.hashCode());
		}
	}

}
