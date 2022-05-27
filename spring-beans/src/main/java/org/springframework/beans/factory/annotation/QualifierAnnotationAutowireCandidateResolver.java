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

package org.springframework.beans.factory.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.GenericTypeAwareAutowireCandidateResolver;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link AutowireCandidateResolver} implementation that matches bean definition qualifiers
 * against {@link Qualifier qualifier annotations} on the field or parameter to be autowired.
 * Also supports suggested expression values through a {@link Value value} annotation.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.5
 * @see AutowireCandidateQualifier
 * @see Qualifier
 * @see Value
 */
public class QualifierAnnotationAutowireCandidateResolver extends GenericTypeAwareAutowireCandidateResolver {
	// 在 GenericTypeAwareAutowireCandidateResolver 的基础上
	// 额外支持：@Value注解、@Qualifier注解

	private final Set<Class<? extends Annotation>> qualifierTypes = new LinkedHashSet<>(2);

	private Class<? extends Annotation> valueAnnotationType = Value.class;


	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for Spring's standard {@link Qualifier} annotation.
	 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
	 */
	@SuppressWarnings("unchecked")
	public QualifierAnnotationAutowireCandidateResolver() {
		// 构造函数

		// 默认情况：都是使用构造函数，将qualifierTypes配置为Qualifier注解的class
		this.qualifierTypes.add(Qualifier.class);
		try {
			this.qualifierTypes.add((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Qualifier",
							QualifierAnnotationAutowireCandidateResolver.class.getClassLoader()));
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}

	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation type.
	 * @param qualifierType the qualifier annotation to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Class<? extends Annotation> qualifierType) {
		Assert.notNull(qualifierType, "'qualifierType' must not be null");
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation types.
	 * @param qualifierTypes the qualifier annotations to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Set<Class<? extends Annotation>> qualifierTypes) {
		Assert.notNull(qualifierTypes, "'qualifierTypes' must not be null");
		this.qualifierTypes.addAll(qualifierTypes);
	}


	/**
	 * Register the given type to be used as a qualifier when autowiring.
	 * <p>This identifies qualifier annotations for direct use (on fields,
	 * method parameters and constructor parameters) as well as meta
	 * annotations that in turn identify actual qualifier annotations.
	 * <p>This implementation only supports annotations as qualifier types.
	 * The default is Spring's {@link Qualifier} annotation which serves
	 * as a qualifier for direct use and also as a meta annotation.
	 * @param qualifierType the annotation type to register
	 */
	public void addQualifierType(Class<? extends Annotation> qualifierType) {
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * Set the 'value' annotation type, to be used on fields, method parameters
	 * and constructor parameters.
	 * <p>The default value annotation type is the Spring-provided
	 * {@link Value} annotation.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate a default value
	 * expression for a specific argument.
	 */
	public void setValueAnnotationType(Class<? extends Annotation> valueAnnotationType) {
		this.valueAnnotationType = valueAnnotationType;
	}


	/**
	 * Determine whether the provided bean definition is an autowire candidate.
	 * <p>To be considered a candidate the bean's <em>autowire-candidate</em>
	 * attribute must not have been set to 'false'. Also, if an annotation on
	 * the field or parameter to be autowired is recognized by this bean factory
	 * as a <em>qualifier</em>, the bean must 'match' against the annotation as
	 * well as any attributes it may contain. The bean definition must contain
	 * the same qualifier or match by meta attributes. A "value" attribute will
	 * fallback to match against the bean name or an alias if a qualifier or
	 * attribute does not match.
	 * @see Qualifier
	 */
	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		// 该方法用来 -- 确定提供的 bean 定义是否是自动装配候选者。
		// 要被视为候选，bean 的autowire-candidate属性不得设置为“false”。
		// 此外，如果此 bean 工厂将要自动装配的字段或参数上的注释识别为qualifier ，
		// 则 bean 必须“匹配”该注释以及它可能包含的任何属性。
		// bean 定义必须包含相同的限定符或元属性匹配。
		// 如果限定符或属性不匹配，“值”属性将回退以匹配 bean 名称或别名。

		// 会一直向上依次调用
		// 调用 SimpleAutowireCandidateResolver 的 isAutowireCandidate 查看 BeanDefinition 中的 AutowireCandidate 是否为true
		// 调用 GenericTypeAwareAutowireCandidateResolver 的  isAutowireCandidate 查看 是否为泛型，在泛型的情况下是否能够满足匹配呢
		boolean match = super.isAutowireCandidate(bdHolder, descriptor);
		// 如果是false，说明beanDefinition.autowireCandidate 或者 依赖上泛型没有匹配上(那就不用继续往下走了) -- 依赖中泛型通常是 @Autowrite List<XxxInterface> list;
		// 即不负好 XxxInterface 这个泛型实现类
		// 如果是true，那就继续，解析@Qualifier注解啦
		// 所以若你标记了@Qualifier注解，也是需要对应上
		if (match) {
			// 这个逻辑比较简单，看看有没有标注@Qualifier注解(没有标注也是返回true~~)
			// 需要注意的是，Spring这里支持自己的@Qualifier，也支持javax.inject.Qualifier
			// checkQualifiers() 这个方法有一些有意思的处理，因此还是决定讲解一下，请参见下面的解析~~~~~
			match = checkQualifiers(bdHolder, descriptor.getAnnotations());
			if (match) {
				// 兼容到方法形参级别的注入~~~~~~~~~~~~~
				// 因为 @Autowrited 支持在方法形参上使用
				MethodParameter methodParam = descriptor.getMethodParameter();
				if (methodParam != null) {
					Method method = methodParam.getMethod();
					// 但是有要求 @Autowrite 标注的方法,其返回值必须是Void的哦
					if (method == null || void.class == method.getReturnType()) {
						match = checkQualifiers(bdHolder, methodParam.getMethodAnnotations());
					}
				}
			}
		}
		// 最终返回是否支持
		return match;
	}
	/**
	 * Match the given qualifier annotations against the candidate bean definition.
	 * 将给定的@Qualifier注解与候选bean定义匹配~~~（简单的书就是看看类型已匹配上的，@Qualifier是否还能匹配上）
	 */
	protected boolean checkQualifiers(BeanDefinitionHolder bdHolder, Annotation[] annotationsToSearch) {

		// 1. 被搜索的注解 annotationsToSearch 为空的
		if (ObjectUtils.isEmpty(annotationsToSearch)) {
			return true;
		}
		// 2. TypeConverter
		SimpleTypeConverter typeConverter = new SimpleTypeConverter();
		for (Annotation annotation : annotationsToSearch) {
			Class<? extends Annotation> type = annotation.annotationType();
			boolean checkMeta = true;
			boolean fallbackToMeta = false;

			// 2.1 isQualifier：判断注解type类型是不是@Qualifier注解以及 JSR330的`javax.inject.Qualifier`注解也是支持的
			// 这里是检查type是@Qualifier的注解
			if (isQualifier(type)) {
				// checkQualifier 最重要的方法就是这个了，它是个重载方法。。。它的内容非常长，大致我在这里解析步骤如下：
				//1、bd.getQualifier 看看Bean定义里是否已经定义过Qualifier们(但是经过我的跟踪，Bean定义得这个字段：private final Map<String, AutowireCandidateQualifier> qualifiers;永远不会被赋值 如有人知道，请告知我 了能事Spring预留得吧)
				//2、该Bean定义得AnnotatedElement qualifiedElement的这个属性上是否有指定的注解，有就拿出这个Annotation，否则继续下一步
				//3、resolvedFactoryMethod工厂方法上是否有这个注解，否则进行下一步（下一步事关键。。。）
				//4、Look for matching annotation on the target class  JavaDoc得意思备注也很清晰，就是去具体的类上面，看有没有有对应的注解，有就拿出来。
				//（有个细节）：即使这个类被代理了，也是能拿到标注在它上面的注解的  因为： AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type)
				//5、到这里，若获得了对应的@Qualifier注解，那就会比较。如果value值也相同，那就return true，否则继续往下走
				//6、接下来拿到这个注解的attributes，然后判断若@Qualifier没有value值或者是空串，就只return false了  否则继续看
				//7、最终会和Bean上面那个注解（一般都是@Component等注解）的value值和@Qualifier中value值进行比较，若相等, 就最终返回true勒（请注意：此处Bean的alias别名若相等也是会返回true）
				//8、======就这样，我们就完成了Bean定义和@Qualifier得一个匹配过程======
				if (!checkQualifier(bdHolder, annotation, typeConverter)) {
					fallbackToMeta = true;
				}
				else {
					// 是否需要去检查元注解
					checkMeta = false;
				}
			}

			// 3. 这一步非常有效：相当于支持到了组合注解的情况。 它连注解的注解都会解析
			// 比如我们@MyAnno上面还有@Qualifier注解，仍然会被这里解析到的  内部有一个递归
			if (checkMeta) {
				boolean foundMeta = false;
				for (Annotation metaAnn : type.getAnnotations()) {
					Class<? extends Annotation> metaType = metaAnn.annotationType();
					if (isQualifier(metaType)) {
						foundMeta = true;
						// Only accept fallback match if @Qualifier annotation has a value...
						// Otherwise it is just a marker for a custom qualifier annotation.
						if ((fallbackToMeta && StringUtils.isEmpty(AnnotationUtils.getValue(metaAnn))) ||
								!checkQualifier(bdHolder, metaAnn, typeConverter)) {
							return false;
						}
					}
				}
				if (fallbackToMeta && !foundMeta) {
					return false;
				}
			}
		}
		return true;
	}


	/**
	 * Checks whether the given annotation type is a recognized qualifier type.
	 */
	protected boolean isQualifier(Class<? extends Annotation> annotationType) {
		for (Class<? extends Annotation> qualifierType : this.qualifierTypes) {
			if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Match the given qualifier annotation against the candidate bean definition.
	 */
	protected boolean checkQualifier(
			BeanDefinitionHolder bdHolder, Annotation annotation, TypeConverter typeConverter) {

		Class<? extends Annotation> type = annotation.annotationType();
		RootBeanDefinition bd = (RootBeanDefinition) bdHolder.getBeanDefinition();

		AutowireCandidateQualifier qualifier = bd.getQualifier(type.getName());
		if (qualifier == null) {
			qualifier = bd.getQualifier(ClassUtils.getShortName(type));
		}
		if (qualifier == null) {
			// First, check annotation on qualified element, if any
			Annotation targetAnnotation = getQualifiedElementAnnotation(bd, type);
			// Then, check annotation on factory method, if applicable
			if (targetAnnotation == null) {
				targetAnnotation = getFactoryMethodAnnotation(bd, type);
			}
			if (targetAnnotation == null) {
				RootBeanDefinition dbd = getResolvedDecoratedDefinition(bd);
				if (dbd != null) {
					targetAnnotation = getFactoryMethodAnnotation(dbd, type);
				}
			}
			if (targetAnnotation == null) {
				// Look for matching annotation on the target class
				if (getBeanFactory() != null) {
					try {
						Class<?> beanType = getBeanFactory().getType(bdHolder.getBeanName());
						if (beanType != null) {
							targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(beanType), type);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Not the usual case - simply forget about the type check...
					}
				}
				if (targetAnnotation == null && bd.hasBeanClass()) {
					targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type);
				}
			}
			if (targetAnnotation != null && targetAnnotation.equals(annotation)) {
				return true;
			}
		}

		Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
		if (attributes.isEmpty() && qualifier == null) {
			// If no attributes, the qualifier must be present
			return false;
		}
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attributeName = entry.getKey();
			Object expectedValue = entry.getValue();
			Object actualValue = null;
			// Check qualifier first
			if (qualifier != null) {
				actualValue = qualifier.getAttribute(attributeName);
			}
			if (actualValue == null) {
				// Fall back on bean definition attribute
				actualValue = bd.getAttribute(attributeName);
			}
			if (actualValue == null && attributeName.equals(AutowireCandidateQualifier.VALUE_KEY) &&
					expectedValue instanceof String && bdHolder.matchesName((String) expectedValue)) {
				// Fall back on bean name (or alias) match
				continue;
			}
			if (actualValue == null && qualifier != null) {
				// Fall back on default, but only if the qualifier is present
				actualValue = AnnotationUtils.getDefaultValue(annotation, attributeName);
			}
			if (actualValue != null) {
				actualValue = typeConverter.convertIfNecessary(actualValue, expectedValue.getClass());
			}
			if (!expectedValue.equals(actualValue)) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	protected Annotation getQualifiedElementAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		AnnotatedElement qualifiedElement = bd.getQualifiedElement();
		return (qualifiedElement != null ? AnnotationUtils.getAnnotation(qualifiedElement, type) : null);
	}

	@Nullable
	protected Annotation getFactoryMethodAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		Method resolvedFactoryMethod = bd.getResolvedFactoryMethod();
		return (resolvedFactoryMethod != null ? AnnotationUtils.getAnnotation(resolvedFactoryMethod, type) : null);
	}


	/**
	 * Determine whether the given dependency declares an autowired annotation,
	 * checking its required flag.
	 * @see Autowired#required()
	 */
	@Override
	public boolean isRequired(DependencyDescriptor descriptor) {
		if (!super.isRequired(descriptor)) {
			return false;
		}
		Autowired autowired = descriptor.getAnnotation(Autowired.class);
		return (autowired == null || autowired.required());
	}

	/**
	 * Determine whether the given dependency declares a qualifier annotation.
	 * @see #isQualifier(Class)
	 * @see Qualifier
	 */
	@Override
	public boolean hasQualifier(DependencyDescriptor descriptor) {
		// 获取依赖注入上的注解，检查是否有@Qualifier注解
		for (Annotation ann : descriptor.getAnnotations()) {
			if (isQualifier(ann.annotationType())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether the given dependency declares a value annotation.
	 * @see Value
	 */
	@Override
	@Nullable
	public Object getSuggestedValue(DependencyDescriptor descriptor) {

		// 1. 拿到value注解的这个属性（若标注有@Value注解的话）
		Object value = findValue(descriptor.getAnnotations());
		if (value == null) {
			// 2. 字段没有使用@Value，就看形参方法上有没有(前提是注入的形参，而不是类字段，常见注入的形参就是@Bean标注的方法的形参)
			MethodParameter methodParam = descriptor.getMethodParameter();
			if (methodParam != null) {
				value = findValue(methodParam.getMethodAnnotations());
			}
		}
		// 3. 获取到@Value的value属性值，注意属性值是没有经过spel解析或占位符解析的哦,是原生的字符串
		return value;
	}

	/**
	 * Determine a suggested value from any of the given candidate annotations.
	 */
	@Nullable
	protected Object findValue(Annotation[] annotationsToSearch) {
		if (annotationsToSearch.length > 0) {   // qualifier annotations have to be local
			// 1. 对于给定的annotationsToSearch注解生成一个AnnotationElement元素，这样AnnotatedElementUtils才可以会使用
			AnnotationAttributes attr = AnnotatedElementUtils.getMergedAnnotationAttributes(AnnotatedElementUtils.forAnnotations(annotationsToSearch), this.valueAnnotationType);
			if (attr != null) {
				// 2. 提取@value属性的value值
				return extractValue(attr);
			}
		}
		return null;
	}

	/**
	 * Extract the value attribute from the given annotation.
	 * @since 4.3
	 */
	protected Object extractValue(AnnotationAttributes attr) {
		Object value = attr.get(AnnotationUtils.VALUE);
		if (value == null) {
			throw new IllegalStateException("Value annotation must have a value attribute");
		}
		return value;
	}

}
