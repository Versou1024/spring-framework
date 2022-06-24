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

package org.springframework.http.converter.json;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.Converter;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

/**
 * Allows for creating Jackson ({@link JsonSerializer}, {@link JsonDeserializer},
 * {@link KeyDeserializer}, {@link TypeResolverBuilder}, {@link TypeIdResolver})
 * beans with autowiring against a Spring {@link ApplicationContext}.
 *
 * <p>As of Spring 4.3, this overrides all factory methods in {@link HandlerInstantiator},
 * including non-abstract ones and recently introduced ones from Jackson 2.4 and 2.5:
 * for {@link ValueInstantiator}, {@link ObjectIdGenerator}, {@link ObjectIdResolver},
 * {@link PropertyNamingStrategy}, {@link Converter}, {@link VirtualBeanPropertyWriter}.
 *
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @since 4.1.3
 * @see Jackson2ObjectMapperBuilder#handlerInstantiator(HandlerInstantiator)
 * @see ApplicationContext#getAutowireCapableBeanFactory()
 * @see HandlerInstantiator
 */
public class SpringHandlerInstantiator extends HandlerInstantiator {
	// HandlerInstantiator -- Handler实例化器
	// SpringHandlerInstantiator -- 在Spring的基础上提供Handler实例化能力

	// 在实际使用中，你完全不必创建自定义Handlerlnstantiator，因为Spring提供的SpringHandlerInstantiator覆写了全部方法。你需要
	// 做的只需要在Springi配置中将它连接到Jackson2 ObjectMapperBuilder./ObjectMapper里就成。

	//	@Test
	//	public void fun3() throws Exception {
	//	    ApplicationContext applicationContext = new AnnotationConfigApplicationContext(HandlerInstantiatorTest.class);
	//	    ObjectMapper mapper = new ObjectMapper();
	//	    mapper.setHandlerInstantiator(new SpringHandlerInstantiator(applicationContext.getAutowireCapableBeanFactory()));
	//	    System.out.println(mapper.writeValueAsString(new Person("YoutBatman", 18)));
	//	}
	// 		注意：PersonSerializer并不需要交给容器管理，也能直接`@Autowired`
	//	class PersonSerializer extends StdSerializer<Person> {
	//	    @Autowired
	//	    ApplicationContext applicationContext; // 虽然没有交给IOC容器管理,但是SpringHandlerInstantiator使用的是AutowireCapableBeanFactory完成自动装配的哦
	//	    ...
	//	}


	// 所以这样是ok的哦
	// 使用Jackson2 ObjectMapperBuilder构建
	// 虽然上面直接构建ObjectMapper也不算复杂，但是需要使用者了解SpringHandlerInstantiator这个API，
	// 所以建议使用 Jackson2ObjectMapperBuilder，它能让代码变得如此优雅：[❗❗️❗️ 我们会发现 Jackson2ObjectMapperBuilder 默认在用户没有设置 HandlerInstantiator 时使用️ SpringHandlerInstantiator]
	//		  public void fun4()throws Exception
	//		  ApplicationContext applicationContext new AnnotationConfigApplicationContext(HandlerInstantiatorTest
	//
	//		  ObjectMapper mapper Jackson20bjectMapperBuilder.json()
	//		      applicationContext(applicationContext)
	//		      build ()
	//		  System.out.println(mapper.writeValueAsString (new Person("YoutBatman",18)));
	//		  }
	//可以看到，我们只需要设置我们熟悉的API applicationContext而完全不用关心HandlerInstantiator。这样子我们这样使用便可：
	// 		  @JsonSerialize(using PersonSerializer.class)
	// 		  public class Person{
	// 		  		private String name;
	// 		  		private Integer age;
	// 		  }
	// 		  //PersonSerializer不用显示交给Spring容器，内部的aAutowired，/@Value将会生效
	// 		  class PersonSerializer extends StdSerializer<Person>{
	// 		   		@Autowired
	// 		   		ApplicationContext applicationContext;
	// 		   }

	private final AutowireCapableBeanFactory beanFactory;


	/**
	 * Create a new SpringHandlerInstantiator for the given BeanFactory.
	 * @param beanFactory the target BeanFactory
	 */
	public SpringHandlerInstantiator(AutowireCapableBeanFactory beanFactory) {
		// 唯一构造函数 -- 必须提供BeanFactory,
		// why 0-- 因为是Handler的实例化,需要介入IOC容器
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	@Override
	public JsonDeserializer<?> deserializerInstance(DeserializationConfig config,
			Annotated annotated, Class<?> implClass) {
		// 关于beanFactory.createBean（）方法一句话说明：它帮你创建Class类型的实例，该实例内可以随意使用Spring容器内的Bean，
		// 但它自己并不放进容器内。
		//（注意: BeanFactory是AutowireCapableBeanFactory的）
		return (JsonDeserializer<?>) this.beanFactory.createBean(implClass);
	}

	@Override
	public KeyDeserializer keyDeserializerInstance(DeserializationConfig config,
			Annotated annotated, Class<?> implClass) {

		return (KeyDeserializer) this.beanFactory.createBean(implClass);
	}

	@Override
	public JsonSerializer<?> serializerInstance(SerializationConfig config,
			Annotated annotated, Class<?> implClass) {

		return (JsonSerializer<?>) this.beanFactory.createBean(implClass);
	}

	@Override
	public TypeResolverBuilder<?> typeResolverBuilderInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (TypeResolverBuilder<?>) this.beanFactory.createBean(implClass);
	}

	@Override
	public TypeIdResolver typeIdResolverInstance(MapperConfig<?> config, Annotated annotated, Class<?> implClass) {
		return (TypeIdResolver) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public ValueInstantiator valueInstantiatorInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (ValueInstantiator) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public ObjectIdGenerator<?> objectIdGeneratorInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (ObjectIdGenerator<?>) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public ObjectIdResolver resolverIdGeneratorInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (ObjectIdResolver) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public PropertyNamingStrategy namingStrategyInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (PropertyNamingStrategy) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public Converter<?, ?> converterInstance(MapperConfig<?> config,
			Annotated annotated, Class<?> implClass) {

		return (Converter<?, ?>) this.beanFactory.createBean(implClass);
	}

	/** @since 4.3 */
	@Override
	public VirtualBeanPropertyWriter virtualPropertyWriterInstance(MapperConfig<?> config, Class<?> implClass) {
		return (VirtualBeanPropertyWriter) this.beanFactory.createBean(implClass);
	}

}
