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

package org.springframework.web.method.annotation;

import java.beans.PropertyEditor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.UriComponentsContributor;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves method arguments annotated with @{@link RequestParam}, arguments of
 * type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver}
 * abstraction, and arguments of type {@code javax.servlet.http.Part} in conjunction
 * with Servlet 3.0 multipart requests. This resolver can also be created in default
 * resolution mode in which simple types (int, long, etc.) not annotated with
 * {@link RequestParam @RequestParam} are also treated as request parameters with
 * the parameter name derived from the argument name.
 *
 * <p>If the method parameter type is {@link Map}, the name specified in the
 * annotation is used to resolve the request parameter String value. The value is
 * then converted to a {@link Map} via type conversion assuming a suitable
 * {@link Converter} or {@link PropertyEditor} has been registered.
 * Or if a request parameter name is not specified the
 * {@link RequestParamMapMethodArgumentResolver} is used instead to provide
 * access to all request parameters in the form of a map.
 *
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved request
 * header values that don't yet match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.1
 * @see RequestParamMapMethodArgumentResolver
 */
public class RequestParamMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver
		implements UriComponentsContributor {
	// 支持 @RequestParam 注解 / @RequestPart 注解

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);

	// 是否对没有标注@RequestParam的简单类型的形参也支持做解析操作
	// 这个参数老重要了：
	// true：表示参数类型是基本类型 参考BeanUtils#isSimpleProperty(什么Enum、Number、Date、URL、包装类型、以上类型的数组类型等等)
	// 如果是基本类型，即使你不写@RequestParam注解，它也是会走进来处理的~~~(这个@PathVariable可不会哟~)
	// fasle：除上以外的。  要想它处理就必须标注注解才行哦，比如List等~
	// 默认值是false
	private final boolean useDefaultResolution;


	/**
	 * Create a new {@link RequestParamMethodArgumentResolver} instance.
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 */
	public RequestParamMethodArgumentResolver(boolean useDefaultResolution) {
		this.useDefaultResolution = useDefaultResolution;
	}

	/**
	 * Create a new {@link RequestParamMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory used for resolving  ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 */
	public RequestParamMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory,
			boolean useDefaultResolution) {

		// 传入了ConfigurableBeanFactory ，所以它支持处理占位符${...} 并且支持SpEL了
		// 此构造都在RequestMappingHandlerAdapter里调用，最后都会传入true来Catch-all Case  这种设计挺有意思的
		super(beanFactory);
		this.useDefaultResolution = useDefaultResolution;
	}


	/**
	 * Supports the following:
	 * <ul>
	 * <li>@RequestParam-annotated method arguments.
	 * This excludes {@link Map} params where the annotation does not specify a name.
	 * See {@link RequestParamMapMethodArgumentResolver} instead for such params.
	 * <li>Arguments of type {@link MultipartFile} unless annotated with @{@link RequestPart}.
	 * <li>Arguments of type {@code Part} unless annotated with @{@link RequestPart}.
	 * <li>In default resolution mode, simple type arguments even if not with @{@link RequestParam}.
	 * </ul>
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 此处理器能处理如下Case：
		// 1、所有标注有@RequestParam注解的类型（非Map）/ 注解指定了value值的Map类型（自己提供转换器哦）
		// ======下面都表示没有标注@RequestParam注解了的=======
		// 1、不能标注有@RequestPart注解，否则直接不处理了
		// 2、是上传的request：isMultipartArgument() = true（MultipartFile类型或者对应的集合/数组类型  或者javax.servlet.http.Part对应结合/数组类型）
		// 3、useDefaultResolution=true情况下，"基本类型"也会处理

		// 1. 标有@RequestParam注解
		if (parameter.hasParameterAnnotation(RequestParam.class)) {
			if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
				// 1.1 标有@RequestParam注解,是Map类型的,就需要检查name
				RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
				return (requestParam != null && StringUtils.hasText(requestParam.name()));
			}
			else {
				// 1.2 标有@RequestParam注解,非Map类型返回true -- 因为可以以形参的形参名作为NameValueInfo的name
				// 而 Map 类型标注的 @RequestParam注解 就必须提供name属性
				return true;
			}
		}
		// 2. 不含有@RequestParam注解
		else {
			// 2.1 @RequestPart注解存在,不支持
			if (parameter.hasParameterAnnotation(RequestPart.class)) {
				return false;
			}
			parameter = parameter.nestedIfOptional();
			// 2.2 属于Multipart类型的参数,是支持解析的
			if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
				return true;
			}
			// 2.3 不属于Multipart,且开启对简单类型的支持,就检查是否为简单类型
			else if (this.useDefaultResolution) {
				return BeanUtils.isSimpleProperty(parameter.getNestedParameterType());
			}
			// 2.4 不支持
			else {
				return false;
			}
		}
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		// 从这也可以看出：即使木有@RequestParam注解，也是可以创建出一个NamedValueInfo来的
		RequestParam ann = parameter.getParameterAnnotation(RequestParam.class);
		return (ann != null ? new RequestParamNamedValueInfo(ann) : new RequestParamNamedValueInfo());
	}

	@Override
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		// 核心方法：根据Name 获取值（普通/文件上传）
		// 并且还有集合、数组等情况

		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);

		// 这块解析出来的是个MultipartFile或者其集合/数组
		if (servletRequest != null) {
			// name 作为文件名
			Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
			if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) {
				return mpArg;
			}
		}

		Object arg = null;
		MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
		if (multipartRequest != null) {
			List<MultipartFile> files = multipartRequest.getFiles(name);
			if (!files.isEmpty()) {
				arg = (files.size() == 1 ? files.get(0) : files);
			}
		}
		// 若解析出来值仍旧为null，那处理完文件上传里木有，那就去参数里取吧
		// 由此可见：文件上传的优先级是高于请求参数的
		if (arg == null) {
			//小知识点：getParameter()其实本质是getParameterNames()[0]的效果
			// 强调一遍：?ids=1,2,3 结果是["1,2,3"]（兼容方式，不建议使用。注意：只能是逗号分隔）
			// ?ids=1&ids=2&ids=3  结果是[1,2,3]（标准的传值方式，建议使用）
			// 但是Spring MVC这两种都能用List接收  请务必注意他们的区别~~~
			String[] paramValues = request.getParameterValues(name);
			if (paramValues != null) {
				arg = (paramValues.length == 1 ? paramValues[0] : paramValues);
			}
		}
		return arg;

		// ArgumentResolver处理器还是很强大的：不仅能处理标注了@RequestParam的参数，还能接收文件上传参数。甚至那些你平时使用中不标注该注解的封装也是它来兜底完成的。
		// 至于它如何兜底的，可以参见下面这个骚操作：

		// public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter implements BeanFactoryAware, InitializingBean {
		//	...
		//	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		//		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
		//		// Annotation-based argument resolution
		//		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		//		...
		//		// Catch-all  兜底
		//		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true)); // 这里兜底处理了简单类型
		//		resolvers.add(new ServletModelAttributeMethodProcessor(true)); // 这里兜底处理复杂类型
 		//
		//		return resolvers;
		//	}
		//	...
		//}
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
		if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
			if (servletRequest == null || !MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
				throw new MultipartException("Current request is not a multipart request");
			}
			else {
				throw new MissingServletRequestPartException(name);
			}
		}
		else {
			throw new MissingServletRequestParameterException(name,
					parameter.getNestedParameterType().getSimpleName());
		}
	}

	@Override
	public void contributeMethodArgument(MethodParameter parameter, @Nullable Object value,
			UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService) {

		Class<?> paramType = parameter.getNestedParameterType();
		if (Map.class.isAssignableFrom(paramType) || MultipartFile.class == paramType || Part.class == paramType) {
			return;
		}

		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		String name = (requestParam != null && StringUtils.hasLength(requestParam.name()) ?
				requestParam.name() : parameter.getParameterName());
		Assert.state(name != null, "Unresolvable parameter name");

		parameter = parameter.nestedIfOptional();
		if (value instanceof Optional) {
			value = ((Optional<?>) value).orElse(null);
		}

		if (value == null) {
			if (requestParam != null &&
					(!requestParam.required() || !requestParam.defaultValue().equals(ValueConstants.DEFAULT_NONE))) {
				return;
			}
			builder.queryParam(name);
		}
		else if (value instanceof Collection) {
			for (Object element : (Collection<?>) value) {
				element = formatUriValue(conversionService, TypeDescriptor.nested(parameter, 1), element);
				builder.queryParam(name, element);
			}
		}
		else {
			builder.queryParam(name, formatUriValue(conversionService, new TypeDescriptor(parameter), value));
		}
	}

	@Nullable
	protected String formatUriValue(
			@Nullable ConversionService cs, @Nullable TypeDescriptor sourceType, @Nullable Object value) {

		if (value == null) {
			return null;
		}
		else if (value instanceof String) {
			return (String) value;
		}
		else if (cs != null) {
			return (String) cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR);
		}
		else {
			return value.toString();
		}
	}


	private static class RequestParamNamedValueInfo extends NamedValueInfo {
		// 内部类

		// 请注意这个默认值：如果你不写@RequestParam，那么就会用这个默认值
		// 注意：required = false的哟（若写了注解，required默认可是true，请务必注意区分）
		// 因为不写注解的情况下，
		// 若是简单类型参数都是交给此处理器处理的。所以这个机制需要明白
		// 复杂类型（非简单类型）默认是ModelAttributeMethodProcessor处理的
		public RequestParamNamedValueInfo() {
			super("", false, ValueConstants.DEFAULT_NONE);
		}

		public RequestParamNamedValueInfo(RequestParam annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
