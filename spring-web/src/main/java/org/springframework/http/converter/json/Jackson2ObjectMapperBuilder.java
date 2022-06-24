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

package org.springframework.http.converter.json;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.http.HttpLogging;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.StaxUtils;

/**
 * A builder used to create {@link ObjectMapper} instances with a fluent API.
 *
 * <p>It customizes Jackson's default properties with the following ones:
 * <ul>
 * <li>{@link MapperFeature#DEFAULT_VIEW_INCLUSION} is disabled</li>
 * <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} is disabled</li>
 * </ul>
 *
 * <p>It also automatically registers the following well-known modules if they are
 * detected on the classpath:
 * <ul>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-jdk8">jackson-datatype-jdk8</a>:
 * support for other Java 8 types like {@link java.util.Optional}</li>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-jsr310">jackson-datatype-jsr310</a>:
 * support for Java 8 Date & Time API types</li>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-joda">jackson-datatype-joda</a>:
 * support for Joda-Time types</li>
 * <li><a href="https://github.com/FasterXML/jackson-module-kotlin">jackson-module-kotlin</a>:
 * support for Kotlin classes and data classes</li>
 * </ul>
 *
 * <p>Compatible with Jackson 2.6 and higher, as of Spring 4.3.
 *
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @author Tadaya Tsuyukubo
 * @author Eddú Meléndez
 * @since 4.1.1
 * @see #build()
 * @see #configure(ObjectMapper)
 * @see Jackson2ObjectMapperFactoryBean
 */
public class Jackson2ObjectMapperBuilder {

	// 建议在Springweb环境下使用 Jackson2ObjectMapperBuilder 来构造 ObjectMapper 而不是通过 new ObjectMapper()

	// 默认特点
	//	使用Jackson2 ObjectMapperBuilder构建ObjectMapper有如下默认特点：
	//	禁用特征
	//		MapperFeature：#DEFAULT_VIEW_INCLUSION特征被disabled：也就是说只有标注有@JsonView注解的属性才会被序列化进视图里，没有此注解的属性将被忽略
	//		DeserializationFeature#FAIL_ON UNKNOWN_PROPERTIES特征被disabled：也就是说反序列化时遇到不认识的属性也不会抛错（这个属性蛮重要：这就是为何前端多传了N多属性但是spring-web却不抛错的根本原因）
	//	自动注册通用模块（前提是classpath下存在对应的类）
	//		jackson-datatype.-jdk8：建议pom里默认导入相关jar
	//		jackson-datatype-jsr310：建议pom里默认导入相关jar
	//		jackson-datatype-joda：按需（不建议默认导入）
	//		jackson一module-kotlin：按需（不建议默认导入）
	// 说明：可以看到jackson一module-parameter一names这个模块spring-web并没有默认导入，so如果你的JDK版本是以上时，此模
	// 块我也强烈建议你默认导入（当然喽，这个你得手动进行注册才能生效~）。

	private static volatile boolean kotlinWarningLogged = false;

	private final Log logger = HttpLogging.forLogName(getClass());

	// 混合 key->目标类,value->被适配过去的注解
	private final Map<Class<?>, Class<?>> mixIns = new LinkedHashMap<>();

	// 反序列化/序列化指定类型
	private final Map<Class<?>, JsonSerializer<?>> serializers = new LinkedHashMap<>();

	private final Map<Class<?>, JsonDeserializer<?>> deserializers = new LinkedHashMap<>();

	// 可见性级别配置
	// PropertyAccessor 操作级别(Getter/IsGetter/Setter/Creator/Field)
	// Visibility 对应的可见性(NONE/ANY/DEFAULT/PUBLIC_KEY/NON_PRIVATE等)
	private final Map<PropertyAccessor, JsonAutoDetect.Visibility> visibilities = new LinkedHashMap<>();

	// 功能开启情况
	private final Map<Object, Boolean> features = new LinkedHashMap<>();

	private boolean createXmlMapper = false;

	@Nullable
	private JsonFactory factory;

	// DateFormate/Locale/TimeZone

	@Nullable
	private DateFormat dateFormat;

	@Nullable
	private Locale locale;

	@Nullable
	private TimeZone timeZone;

	// 注解内省器
	@Nullable
	private AnnotationIntrospector annotationIntrospector;

	// 属性名发现器
	@Nullable
	private PropertyNamingStrategy propertyNamingStrategy;

	@Nullable
	private TypeResolverBuilder<?> defaultTyping;

	@Nullable
	private JsonInclude.Include serializationInclusion;

	// 属性过滤器
	@Nullable
	private FilterProvider filters;

	// 额外扩展注册的Module
	@Nullable
	private List<Module> modules;

	// 额外扩展注册的Module的classes
	@Nullable
	private Class<? extends Module>[] moduleClasses;

	//该类默认并不会通过ServiceLoader方式去找到模块们
	//若是true：便会调用ObjectMapper，findModules这个方法通过SPI方式自动去找
	//我倒建议：手动控制比较好，所以这里默认值是false是很合理的
	private boolean findModulesViaServiceLoader = false;

	private boolean findWellKnownModules = true;

	private ClassLoader moduleClassLoader = getClass().getClassLoader();

	// ❗️❗️❗️
	// HandlerInstantiator接口Spring给出了唯一实现实例：SpringHandlerInstantiator
	// 这是整合Spring的重点，下面会详细介绍它
	@Nullable
	private HandlerInstantiator handlerInstantiator;

	// spring上下文
	@Nullable
	private ApplicationContext applicationContext;

	@Nullable
	private Boolean defaultUseWrapper;

	// 下面都是系列builder方法 -- 不过多阐述



	/**
	 * If set to {@code true}, an {@link XmlMapper} will be created using its
	 * default constructor. This is only applicable to {@link #build()} calls,
	 * not to {@link #configure} calls.
	 */
	public Jackson2ObjectMapperBuilder createXmlMapper(boolean createXmlMapper) {
		this.createXmlMapper = createXmlMapper;
		return this;
	}

	/**
	 * Define the {@link JsonFactory} to be used to create the {@link ObjectMapper}
	 * instance.
	 * @since 5.0
	 */
	public Jackson2ObjectMapperBuilder factory(JsonFactory factory) {
		this.factory = factory;
		return this;
	}

	/**
	 * Define the format for date/time with the given {@link DateFormat}.
	 * <p>Note: Setting this property makes the exposed {@link ObjectMapper}
	 * non-thread-safe, according to Jackson's thread safety rules.
	 * @see #simpleDateFormat(String)
	 */
	public Jackson2ObjectMapperBuilder dateFormat(DateFormat dateFormat) {
		this.dateFormat = dateFormat;
		return this;
	}

	/**
	 * Define the date/time format with a {@link SimpleDateFormat}.
	 * <p>Note: Setting this property makes the exposed {@link ObjectMapper}
	 * non-thread-safe, according to Jackson's thread safety rules.
	 * @see #dateFormat(DateFormat)
	 */
	public Jackson2ObjectMapperBuilder simpleDateFormat(String format) {
		this.dateFormat = new SimpleDateFormat(format);
		return this;
	}

	/**
	 * Override the default {@link Locale} to use for formatting.
	 * Default value used is {@link Locale#getDefault()}.
	 * @since 4.1.5
	 */
	public Jackson2ObjectMapperBuilder locale(Locale locale) {
		this.locale = locale;
		return this;
	}

	/**
	 * Override the default {@link Locale} to use for formatting.
	 * Default value used is {@link Locale#getDefault()}.
	 * @param localeString the locale ID as a String representation
	 * @since 4.1.5
	 */
	public Jackson2ObjectMapperBuilder locale(String localeString) {
		this.locale = StringUtils.parseLocale(localeString);
		return this;
	}

	/**
	 * Override the default {@link TimeZone} to use for formatting.
	 * Default value used is UTC (NOT local timezone).
	 * @since 4.1.5
	 */
	public Jackson2ObjectMapperBuilder timeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
		return this;
	}

	/**
	 * Override the default {@link TimeZone} to use for formatting.
	 * Default value used is UTC (NOT local timezone).
	 * @param timeZoneString the zone ID as a String representation
	 * @since 4.1.5
	 */
	public Jackson2ObjectMapperBuilder timeZone(String timeZoneString) {
		this.timeZone = StringUtils.parseTimeZoneString(timeZoneString);
		return this;
	}

	/**
	 * Set an {@link AnnotationIntrospector} for both serialization and deserialization.
	 */
	public Jackson2ObjectMapperBuilder annotationIntrospector(AnnotationIntrospector annotationIntrospector) {
		this.annotationIntrospector = annotationIntrospector;
		return this;
	}

	/**
	 * Alternative to {@link #annotationIntrospector(AnnotationIntrospector)}
	 * that allows combining with rather than replacing the currently set
	 * introspector, e.g. via
	 * {@link AnnotationIntrospectorPair#pair(AnnotationIntrospector, AnnotationIntrospector)}.
	 * @param pairingFunction a function to apply to the currently set
	 * introspector (possibly {@code null}); the result of the function becomes
	 * the new introspector.
	 * @since 5.2.4
	 */
	public Jackson2ObjectMapperBuilder annotationIntrospector(
			Function<AnnotationIntrospector, AnnotationIntrospector> pairingFunction) {

		this.annotationIntrospector = pairingFunction.apply(this.annotationIntrospector);
		return this;
	}

	/**
	 * Specify a {@link com.fasterxml.jackson.databind.PropertyNamingStrategy} to
	 * configure the {@link ObjectMapper} with.
	 */
	public Jackson2ObjectMapperBuilder propertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
		this.propertyNamingStrategy = propertyNamingStrategy;
		return this;
	}

	/**
	 * Specify a {@link TypeResolverBuilder} to use for Jackson's default typing.
	 * @since 4.2.2
	 */
	public Jackson2ObjectMapperBuilder defaultTyping(TypeResolverBuilder<?> typeResolverBuilder) {
		this.defaultTyping = typeResolverBuilder;
		return this;
	}

	/**
	 * Set a custom inclusion strategy for serialization.
	 * @see com.fasterxml.jackson.annotation.JsonInclude.Include
	 */
	public Jackson2ObjectMapperBuilder serializationInclusion(JsonInclude.Include serializationInclusion) {
		this.serializationInclusion = serializationInclusion;
		return this;
	}

	/**
	 * Set the global filters to use in order to support {@link JsonFilter @JsonFilter} annotated POJO.
	 * @since 4.2
	 * @see MappingJacksonValue#setFilters(FilterProvider)
	 */
	public Jackson2ObjectMapperBuilder filters(FilterProvider filters) {
		this.filters = filters;
		return this;
	}

	/**
	 * Add mix-in annotations to use for augmenting specified class or interface.
	 * @param target class (or interface) whose annotations to effectively override
	 * @param mixinSource class (or interface) whose annotations are to be "added"
	 * to target's annotations as value
	 * @since 4.1.2
	 * @see com.fasterxml.jackson.databind.ObjectMapper#addMixIn(Class, Class)
	 */
	public Jackson2ObjectMapperBuilder mixIn(Class<?> target, Class<?> mixinSource) {
		this.mixIns.put(target, mixinSource);
		return this;
	}

	/**
	 * Add mix-in annotations to use for augmenting specified class or interface.
	 * @param mixIns a Map of entries with target classes (or interface) whose annotations
	 * to effectively override as key and mix-in classes (or interface) whose
	 * annotations are to be "added" to target's annotations as value.
	 * @since 4.1.2
	 * @see com.fasterxml.jackson.databind.ObjectMapper#addMixIn(Class, Class)
	 */
	public Jackson2ObjectMapperBuilder mixIns(Map<Class<?>, Class<?>> mixIns) {
		this.mixIns.putAll(mixIns);
		return this;
	}

	/**
	 * Configure custom serializers. Each serializer is registered for the type
	 * returned by {@link JsonSerializer#handledType()}, which must not be {@code null}.
	 * @see #serializersByType(Map)
	 */
	public Jackson2ObjectMapperBuilder serializers(JsonSerializer<?>... serializers) {
		for (JsonSerializer<?> serializer : serializers) {
			Class<?> handledType = serializer.handledType();
			if (handledType == null || handledType == Object.class) {
				throw new IllegalArgumentException("Unknown handled type in " + serializer.getClass().getName());
			}
			this.serializers.put(serializer.handledType(), serializer);
		}
		return this;
	}

	/**
	 * Configure a custom serializer for the given type.
	 * @since 4.1.2
	 * @see #serializers(JsonSerializer...)
	 */
	public Jackson2ObjectMapperBuilder serializerByType(Class<?> type, JsonSerializer<?> serializer) {
		this.serializers.put(type, serializer);
		return this;
	}

	/**
	 * Configure custom serializers for the given types.
	 * @see #serializers(JsonSerializer...)
	 */
	public Jackson2ObjectMapperBuilder serializersByType(Map<Class<?>, JsonSerializer<?>> serializers) {
		this.serializers.putAll(serializers);
		return this;
	}

	/**
	 * Configure custom deserializers. Each deserializer is registered for the type
	 * returned by {@link JsonDeserializer#handledType()}, which must not be {@code null}.
	 * @since 4.3
	 * @see #deserializersByType(Map)
	 */
	public Jackson2ObjectMapperBuilder deserializers(JsonDeserializer<?>... deserializers) {
		for (JsonDeserializer<?> deserializer : deserializers) {
			Class<?> handledType = deserializer.handledType();
			if (handledType == null || handledType == Object.class) {
				throw new IllegalArgumentException("Unknown handled type in " + deserializer.getClass().getName());
			}
			this.deserializers.put(deserializer.handledType(), deserializer);
		}
		return this;
	}

	/**
	 * Configure a custom deserializer for the given type.
	 * @since 4.1.2
	 */
	public Jackson2ObjectMapperBuilder deserializerByType(Class<?> type, JsonDeserializer<?> deserializer) {
		this.deserializers.put(type, deserializer);
		return this;
	}

	/**
	 * Configure custom deserializers for the given types.
	 */
	public Jackson2ObjectMapperBuilder deserializersByType(Map<Class<?>, JsonDeserializer<?>> deserializers) {
		this.deserializers.putAll(deserializers);
		return this;
	}

	/**
	 * Shortcut for {@link MapperFeature#AUTO_DETECT_FIELDS} option.
	 */
	public Jackson2ObjectMapperBuilder autoDetectFields(boolean autoDetectFields) {
		this.features.put(MapperFeature.AUTO_DETECT_FIELDS, autoDetectFields);
		return this;
	}

	/**
	 * Shortcut for {@link MapperFeature#AUTO_DETECT_SETTERS}/
	 * {@link MapperFeature#AUTO_DETECT_GETTERS}/{@link MapperFeature#AUTO_DETECT_IS_GETTERS}
	 * options.
	 */
	public Jackson2ObjectMapperBuilder autoDetectGettersSetters(boolean autoDetectGettersSetters) {
		this.features.put(MapperFeature.AUTO_DETECT_GETTERS, autoDetectGettersSetters);
		this.features.put(MapperFeature.AUTO_DETECT_SETTERS, autoDetectGettersSetters);
		this.features.put(MapperFeature.AUTO_DETECT_IS_GETTERS, autoDetectGettersSetters);
		return this;
	}

	/**
	 * Shortcut for {@link MapperFeature#DEFAULT_VIEW_INCLUSION} option.
	 */
	public Jackson2ObjectMapperBuilder defaultViewInclusion(boolean defaultViewInclusion) {
		this.features.put(MapperFeature.DEFAULT_VIEW_INCLUSION, defaultViewInclusion);
		return this;
	}

	/**
	 * Shortcut for {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} option.
	 */
	public Jackson2ObjectMapperBuilder failOnUnknownProperties(boolean failOnUnknownProperties) {
		this.features.put(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
		return this;
	}

	/**
	 * Shortcut for {@link SerializationFeature#FAIL_ON_EMPTY_BEANS} option.
	 */
	public Jackson2ObjectMapperBuilder failOnEmptyBeans(boolean failOnEmptyBeans) {
		this.features.put(SerializationFeature.FAIL_ON_EMPTY_BEANS, failOnEmptyBeans);
		return this;
	}

	/**
	 * Shortcut for {@link SerializationFeature#INDENT_OUTPUT} option.
	 */
	public Jackson2ObjectMapperBuilder indentOutput(boolean indentOutput) {
		this.features.put(SerializationFeature.INDENT_OUTPUT, indentOutput);
		return this;
	}

	/**
	 * Define if a wrapper will be used for indexed (List, array) properties or not by
	 * default (only applies to {@link XmlMapper}).
	 * @since 4.3
	 */
	public Jackson2ObjectMapperBuilder defaultUseWrapper(boolean defaultUseWrapper) {
		this.defaultUseWrapper = defaultUseWrapper;
		return this;
	}

	/**
	 * Specify visibility to limit what kind of properties are auto-detected.
	 * @since 5.1
	 * @see com.fasterxml.jackson.annotation.PropertyAccessor
	 * @see com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
	 */
	public Jackson2ObjectMapperBuilder visibility(PropertyAccessor accessor, JsonAutoDetect.Visibility visibility) {
		this.visibilities.put(accessor, visibility);
		return this;
	}

	/**
	 * Specify features to enable.
	 * @see com.fasterxml.jackson.core.JsonParser.Feature
	 * @see com.fasterxml.jackson.core.JsonGenerator.Feature
	 * @see com.fasterxml.jackson.databind.SerializationFeature
	 * @see com.fasterxml.jackson.databind.DeserializationFeature
	 * @see com.fasterxml.jackson.databind.MapperFeature
	 */
	public Jackson2ObjectMapperBuilder featuresToEnable(Object... featuresToEnable) {
		for (Object feature : featuresToEnable) {
			this.features.put(feature, Boolean.TRUE);
		}
		return this;
	}

	/**
	 * Specify features to disable.
	 * @see com.fasterxml.jackson.core.JsonParser.Feature
	 * @see com.fasterxml.jackson.core.JsonGenerator.Feature
	 * @see com.fasterxml.jackson.databind.SerializationFeature
	 * @see com.fasterxml.jackson.databind.DeserializationFeature
	 * @see com.fasterxml.jackson.databind.MapperFeature
	 */
	public Jackson2ObjectMapperBuilder featuresToDisable(Object... featuresToDisable) {
		for (Object feature : featuresToDisable) {
			this.features.put(feature, Boolean.FALSE);
		}
		return this;
	}

	/**
	 * Specify one or more modules to be registered with the {@link ObjectMapper}.
	 * Multiple invocations are not additive, the last one defines the modules to
	 * register.
	 * <p>Note: If this is set, no finding of modules is going to happen - not by
	 * Jackson, and not by Spring either (see {@link #findModulesViaServiceLoader}).
	 * As a consequence, specifying an empty list here will suppress any kind of
	 * module detection.
	 * <p>Specify either this or {@link #modulesToInstall}, not both.
	 * @since 4.1.5
	 * @see #modules(List)
	 * @see com.fasterxml.jackson.databind.Module
	 */
	public Jackson2ObjectMapperBuilder modules(Module... modules) {
		return modules(Arrays.asList(modules));
	}

	/**
	 * Set a complete list of modules to be registered with the {@link ObjectMapper}.
	 * Multiple invocations are not additive, the last one defines the modules to
	 * register.
	 * <p>Note: If this is set, no finding of modules is going to happen - not by
	 * Jackson, and not by Spring either (see {@link #findModulesViaServiceLoader}).
	 * As a consequence, specifying an empty list here will suppress any kind of
	 * module detection.
	 * <p>Specify either this or {@link #modulesToInstall}, not both.
	 * @see #modules(Module...)
	 * @see com.fasterxml.jackson.databind.Module
	 */
	public Jackson2ObjectMapperBuilder modules(List<Module> modules) {
		this.modules = new LinkedList<>(modules);
		this.findModulesViaServiceLoader = false;
		this.findWellKnownModules = false;
		return this;
	}

	/**
	 * Specify one or more modules to be registered with the {@link ObjectMapper}.
	 * Multiple invocations are not additive, the last one defines the modules
	 * to register.
	 * <p>Modules specified here will be registered after
	 * Spring's autodetection of JSR-310 and Joda-Time, or Jackson's
	 * finding of modules (see {@link #findModulesViaServiceLoader}),
	 * allowing to eventually override their configuration.
	 * <p>Specify either this or {@link #modules}, not both.
	 * @since 4.1.5
	 * @see com.fasterxml.jackson.databind.Module
	 */
	public Jackson2ObjectMapperBuilder modulesToInstall(Module... modules) {
		this.modules = Arrays.asList(modules);
		this.findWellKnownModules = true;
		return this;
	}

	/**
	 * Specify one or more modules by class to be registered with
	 * the {@link ObjectMapper}. Multiple invocations are not additive,
	 * the last one defines the modules to register.
	 * <p>Modules specified here will be registered after
	 * Spring's autodetection of JSR-310 and Joda-Time, or Jackson's
	 * finding of modules (see {@link #findModulesViaServiceLoader}),
	 * allowing to eventually override their configuration.
	 * <p>Specify either this or {@link #modules}, not both.
	 * @see #modulesToInstall(Module...)
	 * @see com.fasterxml.jackson.databind.Module
	 */
	@SuppressWarnings("unchecked")
	public Jackson2ObjectMapperBuilder modulesToInstall(Class<? extends Module>... modules) {
		this.moduleClasses = modules;
		this.findWellKnownModules = true;
		return this;
	}

	/**
	 * Set whether to let Jackson find available modules via the JDK ServiceLoader,
	 * based on META-INF metadata in the classpath.
	 * <p>If this mode is not set, Spring's Jackson2ObjectMapperBuilder itself
	 * will try to find the JSR-310 and Joda-Time support modules on the classpath -
	 * provided that Java 8 and Joda-Time themselves are available, respectively.
	 * @see com.fasterxml.jackson.databind.ObjectMapper#findModules()
	 */
	public Jackson2ObjectMapperBuilder findModulesViaServiceLoader(boolean findModules) {
		this.findModulesViaServiceLoader = findModules;
		return this;
	}

	/**
	 * Set the ClassLoader to use for loading Jackson extension modules.
	 */
	public Jackson2ObjectMapperBuilder moduleClassLoader(ClassLoader moduleClassLoader) {
		this.moduleClassLoader = moduleClassLoader;
		return this;
	}

	/**
	 * Customize the construction of Jackson handlers ({@link JsonSerializer}, {@link JsonDeserializer},
	 * {@link KeyDeserializer}, {@code TypeResolverBuilder} and {@code TypeIdResolver}).
	 * @since 4.1.3
	 * @see Jackson2ObjectMapperBuilder#applicationContext(ApplicationContext)
	 */
	public Jackson2ObjectMapperBuilder handlerInstantiator(HandlerInstantiator handlerInstantiator) {
		this.handlerInstantiator = handlerInstantiator;
		return this;
	}

	/**
	 * Set the Spring {@link ApplicationContext} in order to autowire Jackson handlers ({@link JsonSerializer},
	 * {@link JsonDeserializer}, {@link KeyDeserializer}, {@code TypeResolverBuilder} and {@code TypeIdResolver}).
	 * @since 4.1.3
	 * @see SpringHandlerInstantiator
	 */
	public Jackson2ObjectMapperBuilder applicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		return this;
	}

	// 构建方法 build()

	/**
	 * Build a new {@link ObjectMapper} instance.
	 * <p>Each build operation produces an independent {@link ObjectMapper} instance.
	 * The builder's settings can get modified, with a subsequent build operation
	 * then producing a new {@link ObjectMapper} based on the most recent settings.
	 * @return the newly built ObjectMapper
	 */
	@SuppressWarnings("unchecked")
	public <T extends ObjectMapper> T build() {
		ObjectMapper mapper;
		if (this.createXmlMapper) {
			mapper = (this.defaultUseWrapper != null ?
					new XmlObjectMapperInitializer().create(this.defaultUseWrapper, this.factory) :
					new XmlObjectMapperInitializer().create(this.factory));
		}
		// 99%的情况都是JsonMapper哦
		else {
			mapper = (this.factory != null ? new ObjectMapper(this.factory) : new ObjectMapper());
		}
		// 将buidler中设置好的各种配置设置进去
		configure(mapper);
		return (T) mapper;
	}

	/**
	 * Configure an existing {@link ObjectMapper} instance with this builder's
	 * settings. This can be applied to any number of {@code ObjectMappers}.
	 * @param objectMapper the ObjectMapper to configure
	 */
	public void configure(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");

		MultiValueMap<Object, Module> modulesToRegister = new LinkedMultiValueMap<>();
		// 特别注意：findModulesViaServiceLoader和findWellKnownModules是互的，不能同时生效，使用时请务必注意
		if (this.findModulesViaServiceLoader) {
			// 通过SPI查找
			ObjectMapper.findModules(this.moduleClassLoader).forEach(module -> registerModule(module, modulesToRegister));
		}
		else if (this.findWellKnownModules) {
			// 注册常见的module,前提是用户导入了相关的jar包
			registerWellKnownModulesIfAvailable(modulesToRegister);
		}

		// 注入用户手动注册到builder中的module
		if (this.modules != null) {
			this.modules.forEach(module -> registerModule(module, modulesToRegister));
		}
		// 用户也可以是注入builder中moudle的class,交给BeanUtils来实例化
		if (this.moduleClasses != null) {
			for (Class<? extends Module> moduleClass : this.moduleClasses) {
				registerModule(BeanUtils.instantiateClass(moduleClass), modulesToRegister);
			}
		}
		// 完成最终的注册modules
		List<Module> modules = new ArrayList<>();
		for (List<Module> nestedModules : modulesToRegister.values()) {
			modules.addAll(nestedModules);
		}
		objectMapper.registerModules(modules);

		// ObjectMapper中常见组件的设置

		if (this.dateFormat != null) {
			objectMapper.setDateFormat(this.dateFormat);
		}
		if (this.locale != null) {
			objectMapper.setLocale(this.locale);
		}
		if (this.timeZone != null) {
			objectMapper.setTimeZone(this.timeZone);
		}

		if (this.annotationIntrospector != null) {
			objectMapper.setAnnotationIntrospector(this.annotationIntrospector);
		}
		if (this.propertyNamingStrategy != null) {
			objectMapper.setPropertyNamingStrategy(this.propertyNamingStrategy);
		}
		if (this.defaultTyping != null) {
			objectMapper.setDefaultTyping(this.defaultTyping);
		}
		if (this.serializationInclusion != null) {
			objectMapper.setSerializationInclusion(this.serializationInclusion);
		}

		if (this.filters != null) {
			objectMapper.setFilterProvider(this.filters);
		}

		this.mixIns.forEach(objectMapper::addMixIn);

		//由此可见：你配置的序列化器、反序列化器最终都是通过模块的方式注册进去的
		//所有的序列化器一次性通过一个SimpleModule注册进去
		if (!this.serializers.isEmpty() || !this.deserializers.isEmpty()) {
			SimpleModule module = new SimpleModule();
			addSerializers(module);
			addDeserializers(module);
			objectMapper.registerModule(module);
		}

		this.visibilities.forEach(objectMapper::setVisibility);

		//定制默认的特征们（Spring内置动作，此方法为orivate，子类复写不了）
		//做你没指定对应的Featrue的话，默认禁用掉MapperFeature.DEFAULT VIEW INCLUSION
		//和DeserializationFeature.FAIL ON UNKNOWN PROPERTIES
		customizeDefaultFeatures(objectMapper);
		this.features.forEach((feature, enabled) -> configureFeature(objectMapper, feature, enabled));
		// ❗️❗️❗️
		// 绝大部分情况下，使用SpringHandlerInstantiator就够了
		// 它的特点：能够使用Spring容器里面的Bean，从而达到和Spring深度整合的目的
		//  该API是理解Jackson整合进Spring容器的切入点，问下会详细介绍
		//  当然它的前提条件是：this.applicationContext！=null喽
		// 而`applicationContext`是怎么来的？就是通过set方法设置进来的呗
		if (this.handlerInstantiator != null) {
			objectMapper.setHandlerInstantiator(this.handlerInstantiator);
		}
		else if (this.applicationContext != null) {
			objectMapper.setHandlerInstantiator(
					new SpringHandlerInstantiator(this.applicationContext.getAutowireCapableBeanFactory()));
		}

		// 最为重要的build（）方法核心源码如上，其配置ObjectMapper的步骤总结如下：
		//	1.注册Module们：包括WellKnownModules以及通过Builder配置进来的Modules们
		//		1.默认会注册那4大WellKnownModules，前提是classpath下存在对应的类
		//	2.注册所有的自定义的序列化器/反序列化器们（通过一个SimpleModule完成注册）
		//		1.因为通过Module模块把序列化器放进去是唯一方式（另一种方式是使用注解@JsonSerialize方式，不过那是局部生效）
		//	3.让自定义的特征值生效（含Springl的默认处理逻辑）
		//	4.使用SpringHandlerInstantiator和Spring容器进行深度整合（当然这不是必须的）
	}

	private void registerModule(Module module, MultiValueMap<Object, Module> modulesToRegister) {
		if (module.getTypeId() == null) {
			modulesToRegister.add(SimpleModule.class.getName(), module);
		}
		else {
			modulesToRegister.set(module.getTypeId(), module);
		}
	}


	// Any change to this method should be also applied to spring-jms and spring-messaging
	// MappingJackson2MessageConverter default constructors
	private void customizeDefaultFeatures(ObjectMapper objectMapper) {
		// 如果用户没有特别的对以下两个功能开启
		// Spring将特别的将ObjectMapper中的 -- DEFAULT_VIEW_INCLUSION\FAIL_ON_UNKNOWN_PROPERTIES 两个feature关闭
		if (!this.features.containsKey(MapperFeature.DEFAULT_VIEW_INCLUSION)) {
			configureFeature(objectMapper, MapperFeature.DEFAULT_VIEW_INCLUSION, false);
		}
		if (!this.features.containsKey(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
			configureFeature(objectMapper, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void addSerializers(SimpleModule module) {
		this.serializers.forEach((type, serializer) ->
				module.addSerializer((Class<? extends T>) type, (JsonSerializer<T>) serializer));
	}

	@SuppressWarnings("unchecked")
	private <T> void addDeserializers(SimpleModule module) {
		this.deserializers.forEach((type, deserializer) ->
				module.addDeserializer((Class<T>) type, (JsonDeserializer<? extends T>) deserializer));
	}

	private void configureFeature(ObjectMapper objectMapper, Object feature, boolean enabled) {
		if (feature instanceof JsonParser.Feature) {
			objectMapper.configure((JsonParser.Feature) feature, enabled);
		}
		else if (feature instanceof JsonGenerator.Feature) {
			objectMapper.configure((JsonGenerator.Feature) feature, enabled);
		}
		else if (feature instanceof SerializationFeature) {
			objectMapper.configure((SerializationFeature) feature, enabled);
		}
		else if (feature instanceof DeserializationFeature) {
			objectMapper.configure((DeserializationFeature) feature, enabled);
		}
		else if (feature instanceof MapperFeature) {
			objectMapper.configure((MapperFeature) feature, enabled);
		}
		else {
			throw new FatalBeanException("Unknown feature class: " + feature.getClass().getName());
		}
	}

	@SuppressWarnings("unchecked")
	private void registerWellKnownModulesIfAvailable(MultiValueMap<Object, Module> modulesToRegister) {
		// 根据类路径检查用户是否导入了对应的class的jar包
		// 比如用户导入了 com.fasterxml.jackson.datatype.jdk8.Jdk8Module 那么就允许向ObjectMapper中注入Jdk8Module
		// 这样构造函数就可以在不使用@JsonCreator和@JsonProperty时即可反序列上去
		// 一共有4中额外扩展的module
		// Jdk8Module
		// JavaTimeModule
		// JodaModule
		// KotlinModule

		try {
			Class<? extends Module> jdk8ModuleClass = (Class<? extends Module>)
					ClassUtils.forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module", this.moduleClassLoader);
			Module jdk8Module = BeanUtils.instantiateClass(jdk8ModuleClass);
			modulesToRegister.set(jdk8Module.getTypeId(), jdk8Module);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jdk8 not available
		}

		try {
			Class<? extends Module> javaTimeModuleClass = (Class<? extends Module>)
					ClassUtils.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", this.moduleClassLoader);
			Module javaTimeModule = BeanUtils.instantiateClass(javaTimeModuleClass);
			modulesToRegister.set(javaTimeModule.getTypeId(), javaTimeModule);
		}
		catch (ClassNotFoundException ex) {
			// jackson-datatype-jsr310 not available
		}

		// Joda-Time 2.x present?
		if (ClassUtils.isPresent("org.joda.time.YearMonth", this.moduleClassLoader)) {
			try {
				Class<? extends Module> jodaModuleClass = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.datatype.joda.JodaModule", this.moduleClassLoader);
				Module jodaModule = BeanUtils.instantiateClass(jodaModuleClass);
				modulesToRegister.set(jodaModule.getTypeId(), jodaModule);
			}
			catch (ClassNotFoundException ex) {
				// jackson-datatype-joda not available
			}
		}

		// Kotlin present?
		if (KotlinDetector.isKotlinPresent()) {
			try {
				Class<? extends Module> kotlinModuleClass = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.module.kotlin.KotlinModule", this.moduleClassLoader);
				Module kotlinModule = BeanUtils.instantiateClass(kotlinModuleClass);
				modulesToRegister.set(kotlinModule.getTypeId(), kotlinModule);
			}
			catch (ClassNotFoundException ex) {
				if (!kotlinWarningLogged) {
					kotlinWarningLogged = true;
					logger.warn("For Jackson Kotlin classes support please add " +
							"\"com.fasterxml.jackson.module:jackson-module-kotlin\" to the classpath");
				}
			}
		}
	}


	// Convenience factory methods
	// 工厂方法 --

	/**
	 * Obtain a {@link Jackson2ObjectMapperBuilder} instance in order to
	 * build a regular JSON {@link ObjectMapper} instance.
	 */
	public static Jackson2ObjectMapperBuilder json() {
		// 最常见的方法构造方法 -- 直接使用默认值创建一个builder,然后可以对builder中配置后调用build()
		return new Jackson2ObjectMapperBuilder();
	}

	/**
	 * Obtain a {@link Jackson2ObjectMapperBuilder} instance in order to
	 * build an {@link XmlMapper} instance.
	 */
	public static Jackson2ObjectMapperBuilder xml() {
		return new Jackson2ObjectMapperBuilder().createXmlMapper(true);
	}

	/**
	 * Obtain a {@link Jackson2ObjectMapperBuilder} instance in order to
	 * build a Smile data format {@link ObjectMapper} instance.
	 * @since 5.0
	 */
	public static Jackson2ObjectMapperBuilder smile() {
		return new Jackson2ObjectMapperBuilder().factory(new SmileFactoryInitializer().create());
	}

	/**
	 * Obtain a {@link Jackson2ObjectMapperBuilder} instance in order to
	 * build a CBOR data format {@link ObjectMapper} instance.
	 * @since 5.0
	 */
	public static Jackson2ObjectMapperBuilder cbor() {
		return new Jackson2ObjectMapperBuilder().factory(new CborFactoryInitializer().create());
	}


	private static class XmlObjectMapperInitializer {

		public ObjectMapper create(@Nullable JsonFactory factory) {
			if (factory != null) {
				return new XmlMapper((XmlFactory) factory);
			}
			else {
				return new XmlMapper(StaxUtils.createDefensiveInputFactory());
			}
		}

		public ObjectMapper create(boolean defaultUseWrapper, @Nullable JsonFactory factory) {
			JacksonXmlModule module = new JacksonXmlModule();
			module.setDefaultUseWrapper(defaultUseWrapper);
			if (factory != null) {
				return new XmlMapper((XmlFactory) factory, module);
			}
			else {
				return new XmlMapper(new XmlFactory(StaxUtils.createDefensiveInputFactory()), module);
			}
		}
	}


	private static class SmileFactoryInitializer {

		public JsonFactory create() {
			return new SmileFactory();
		}
	}


	private static class CborFactoryInitializer {

		public JsonFactory create() {
			return new CBORFactory();
		}
	}

}
