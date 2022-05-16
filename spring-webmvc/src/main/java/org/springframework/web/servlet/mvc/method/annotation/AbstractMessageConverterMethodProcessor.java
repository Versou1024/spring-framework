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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Extends {@link AbstractMessageConverterMethodArgumentResolver} with the ability to handle method
 * return values by writing to the response with {@link HttpMessageConverter HttpMessageConverters}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractMessageConverterMethodProcessor extends AbstractMessageConverterMethodArgumentResolver implements HandlerMethodReturnValueHandler {
	// AbstractMessageConverterMethodProcessor 主要是负责写出来
	// 其超类: AbstractMessageConverterMethodArgumentResolver 和形参有点关,主要是负责读出来

	/* Extensions associated with the built-in message converters */
	private static final Set<String> SAFE_EXTENSIONS = new HashSet<>(Arrays.asList(
			"txt", "text", "yml", "properties", "csv",
			"json", "xml", "atom", "rss",
			"png", "jpe", "jpeg", "jpg", "gif", "wbmp", "bmp"));
	// 与内置消息转换器关联的扩展

	private static final Set<String> SAFE_MEDIA_BASE_TYPES = new HashSet<>(
			Arrays.asList("audio", "image", "video"));
	// 安全媒体库类型

	private static final List<MediaType> ALL_APPLICATION_MEDIA_TYPES =
			Arrays.asList(MediaType.ALL, new MediaType("application"));
	// 所有应用媒体类型

	private static final Type RESOURCE_REGION_LIST_TYPE =
			new ParameterizedTypeReference<List<ResourceRegion>>() { }.getType();
	// 资源区域列表类型


	private final ContentNegotiationManager contentNegotiationManager;
	// 内容协商

	private final Set<String> safeExtensions = new HashSet<>();
	// 安全扩展名


	/**
	 * Constructor with list of converters only.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters) {
		this(converters, null, null);
	}

	/**
	 * Constructor with list of converters and ContentNegotiationManager.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager contentNegotiationManager) {

		this(converters, contentNegotiationManager, null);
	}

	/**
	 * Constructor with list of converters and ContentNegotiationManager as well
	 * as request/response body advice instances.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, requestResponseBodyAdvice);

		this.contentNegotiationManager = (manager != null ? manager : new ContentNegotiationManager());

		// 安全扩展名支持 -- contentNegotiationManager的扩展名 + 默认扩展名
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		this.safeExtensions.addAll(SAFE_EXTENSIONS);
	}


	/**
	 * Creates a new {@link HttpOutputMessage} from the given {@link NativeWebRequest}.
	 * @param webRequest the web request to create an output message from
	 * @return the output message
	 */
	protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		Assert.state(response != null, "No HttpServletResponse");
		return new ServletServerHttpResponse(response);
	}

	/**
	 * Writes the given return value to the given web request. Delegates to
	 * {@link #writeWithMessageConverters(Object, MethodParameter, ServletServerHttpRequest, ServletServerHttpResponse)}
	 */
	protected <T> void writeWithMessageConverters(T value, MethodParameter returnType, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		writeWithMessageConverters(value, returnType, inputMessage, outputMessage);
	}

	/**
	 * Writes the given return type to the given output message.
	 * @param value the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated
	 * by the {@code Accept} header on the request cannot be met by the message converters
	 * @throws HttpMessageNotWritableException thrown if a given message cannot
	 * be written by a converter, or if the content-type chosen by the server
	 * has no compatible converter.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	protected <T> void writeWithMessageConverters(@Nullable T value, MethodParameter returnType,
			ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
		// 此处我们只关注它处理返回值的和信访方法
		// 从JavaDoc解释可以看出，它的作用很“单一“：就是把返回值写进output message~~~

		Object body; // 接受Controller返回值
		Class<?> valueType; // 简单的返回值类型 -- 不携带泛型
		Type targetType; // 持有泛型信息的返回值类型

		// 1. 简单的字符串类型，无须换砖，http本身就是传输字符串的
		// 注意此处的特殊处理，相当于把所有的CharSequence类型的，都最终当作String类型处理的~
		if (value instanceof CharSequence) {
			body = value.toString();
			valueType = String.class;
			targetType = String.class;
		} else {
			// 2. 非String类型,解析 valueType\targetTye
			body = value;
			valueType = getReturnValueType(body, returnType); // 获取 (value != null ? value.getClass() : returnType.getParameterType())
			targetType = GenericTypeResolver.resolveType(getGenericType(returnType), returnType.getContainingClass()); // 这是泛型类型
		}

		// 3. 若返回值是个org.springframework.core.io.Resource  就走这里, 一般可以此处忽略~~
		if (isResourceType(value, returnType)) {
			outputMessage.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
			if (value != null && inputMessage.getHeaders().getFirst(HttpHeaders.RANGE) != null &&
					outputMessage.getServletResponse().getStatus() == 200) {
				Resource resource = (Resource) value;
				try {
					List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
					outputMessage.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value());
					body = HttpRange.toResourceRegions(httpRanges, resource);
					valueType = body.getClass();
					targetType = RESOURCE_REGION_LIST_TYPE;
				}
				catch (IllegalArgumentException ex) {
					outputMessage.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
					outputMessage.getServletResponse().setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
				}
			}
		}

		// 4. 找到一个合适的MediaType

		// selectedMediaType表示最终被选中的MediaType，展示在响应头中的
		// 毕竟请求体的accept放可能是接受N多种MediaType的~~~
		MediaType selectedMediaType = null;
		// 一般情况下 很少会直接指定response的contentType~~~
		// 如果请求方法指定了，就以它的为准，就相当于selectedMediaType直接被找到
		// 一般都是就靠系统自己去寻找到一个最为合适的~~~
		MediaType contentType = outputMessage.getHeaders().getContentType();
		// 4.1 是否已经存在contentType,如果存在 selectedMediaType = contentType
		boolean isContentTypePreset = contentType != null && contentType.isConcrete();
		if (isContentTypePreset) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found 'Content-Type:" + contentType + "' in response");
			}
			selectedMediaType = contentType;
		}else {
			// 4.2 一般情况进入这里
			HttpServletRequest request = inputMessage.getServletRequest(); // 获取HttpServletRequest
			// 4.2.1 前面我们说了 若是谷歌浏览器  默认它的accept为：text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/s
			// 所以此处数组解析出来有7对
			List<MediaType> acceptableTypes = getAcceptableMediaTypes(request); // 获取Accept-Type
			// 4.2.2 这个方法就是从所有已经注册的转换器里面去找，看看哪些转换器支持canWrite，然后把他们所支持的MediaType都加入进来~~~
			// 比如此例只能匹配到MappingJackson2HttpMessageConverter，所以匹配上的有application/json、application/*+json两个
			List<MediaType> producibleTypes = getProducibleMediaTypes(request, valueType, targetType); // 获取Content-Type,正是我们在@RequestMapping中配置的produces
			// 4.2.3 这个异常应该我们经常碰到：有body体，但是并没有能够支持解析这种返回值class的转换器，
			if (body != null && producibleTypes.isEmpty()) {
				throw new HttpMessageNotWritableException("No converter found for return value of type: " + valueType);
			}
			// 4.2.4 下面相当于从浏览器可议接受的MediaType里面，最终抉择出N个来
			// 上面4.2.2只是找到支持这种返回值的,还没有根据MediaType做一些过滤
			// 原理也非常简单：你能接受的isCompatibleWith上了我能处理的，那咱们就好说，处理就完了
			List<MediaType> mediaTypesToUse = new ArrayList<>();
			for (MediaType requestedType : acceptableTypes) {
				for (MediaType producibleType : producibleTypes) {
					// 检查请求中Accept-Type是否支持当前输出的类型，比如application/json子类的
					if (requestedType.isCompatibleWith(producibleType)) { // 指示此媒体类型是否与给定的媒体类型兼容。例如，text/*与text/plain、text/html兼容，反之亦然。
						// 从两个中选择一个最匹配的  主要是根据q值来比较  排序
						// 比如此例，最终匹配上的有两个：application/json;q=0.8和application/*+json;q=0.8
						mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
					}
				}
			}
			// 4.2.5 找到支持解析这种返回值valueClass的转换器,但是转换器的SupportMedia却不被请求的accept所支持
			// 就会抛出这个异常了：HttpMediaTypeNotAcceptableException：Could not find acceptable representation
			if (mediaTypesToUse.isEmpty()) {
				if (body != null) {
					// 如果body不为空，但是对应的生产类型时没有在请求头Accept-Type中支持就报错
					throw new HttpMediaTypeNotAcceptableException(producibleTypes);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("No match for " + acceptableTypes + ", supported: " + producibleTypes);
				}
				return;
			}
			// 4.2.6 至此检查结束,选择最终合适的mediaType吧
			// 对给定的MediaType对象列表进行排序,例如输出application/json实际上被请求头的 Accept-Type:*,*,application/json 两个都支持，需要按照精准度排序
			// 根据Q值进行排序：
			MediaType.sortBySpecificityAndQuality(mediaTypesToUse);
			// 遍历检查
			for (MediaType mediaType : mediaTypesToUse) {
				// isConcrete检查mediaType是否是具体，不能是application/*，而是Application/*
				if (mediaType.isConcrete()) {
					selectedMediaType = mediaType;
					break;
				}
				// isPresentIn方法仅检查类型和子类型，否则将忽略参数
				else if (mediaType.isPresentIn(ALL_APPLICATION_MEDIA_TYPES)) {
					selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
					break;
				}
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Using '" + selectedMediaType + "', given " +
						acceptableTypes + " and supported " + producibleTypes);
			}
		}

		// 5. mediaType 和 valueClass 被选定,就可以找适合这个的消息转换器,然后调用write方法

		// selectedMediaType被选中的MediaType类型
		// 最终的最终 都会找到一个决定write的类型，selectedMediaType此处为：application/json;q=0.8
		// 因为最终会决策出来一个MediaType，所以此处就是要根据此MediaType找到一个合适的消息转换器，把body向outPutStream写进去~~~
		// 注意此处：是RequestResponseBodyAdviceChain执行之处~~~~
		if (selectedMediaType != null) {
			// 去掉MediaType的精准值
			selectedMediaType = selectedMediaType.removeQualityValue();
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				// 是否属于通用转换HttpMessage实现类的转换器，否则为null
				GenericHttpMessageConverter genericConverter = (converter instanceof GenericHttpMessageConverter ? (GenericHttpMessageConverter<?>) converter : null);
				// 如果是通用HttpMessage转换器，判断是否可以write，否则使用普通的canWrite -- 一般是使用 MappingJackson2SmileHttpMessageConverter
				if (genericConverter != null ?
						((GenericHttpMessageConverter) converter).canWrite(targetType, valueType, selectedMediaType) :
						converter.canWrite(valueType, selectedMediaType)) {
					// 在写body之前执行~~~~  会调用我们注册的所有的合适的ResponseBodyAdvice#beforeBodyWrite方法
					// 相当于在写之前，我们可以介入对body体进行处理
					body = getAdvice().beforeBodyWrite(body, returnType, selectedMediaType,
							(Class<? extends HttpMessageConverter<?>>) converter.getClass(),
							inputMessage, outputMessage);
					if (body != null) {
						Object theBody = body;
						LogFormatUtils.traceDebug(logger, traceOn ->
								"Writing [" + LogFormatUtils.formatValue(theBody, !traceOn) + "]");
						// 给响应Response设置一个Content-Disposition的请求头（若需要的话）  若之前已经设置过了，此处将什么都不做
						// 比如我们常见的：response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fileName, "UTF-8"));
						// Content-disposition 是 MIME 协议的扩展，MIME 协议指示 MIME 用户代理如何显示附加的文件。
						// 当 Internet Explorer 接收到头时，它会激活文件下载对话框，它的文件名框自动填充了头中指定的文件名
						addContentDispositionHeader(inputMessage, outputMessage);

						// genericConverter.write()或converter.write开始写入输出
						if (genericConverter != null) {
							genericConverter.write(body, targetType, selectedMediaType, outputMessage);
						}
						else {
							((HttpMessageConverter) converter).write(body, selectedMediaType, outputMessage);
						}
					}
					else {
						if (logger.isDebugEnabled()) {
							logger.debug("Nothing to write: null body");
						}
					}

					// 这一句表示：只要有一个消息转换器处理了，就立马停止~~~~
					return;
				}
			}
		}

		if (body != null) {
			Set<MediaType> producibleMediaTypes =
					(Set<MediaType>) inputMessage.getServletRequest()
							.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

			if (isContentTypePreset || !CollectionUtils.isEmpty(producibleMediaTypes)) {
				throw new HttpMessageNotWritableException(
						"No converter for [" + valueType + "] with preset Content-Type '" + contentType + "'");
			}
			throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
		}
	}

	/**
	 * Return the type of the value to be written to the response. Typically this is
	 * a simple check via getClass on the value but if the value is null, then the
	 * return type needs to be examined possibly including generic type determination
	 * (e.g. {@code ResponseEntity<T>}).
	 */
	protected Class<?> getReturnValueType(@Nullable Object value, MethodParameter returnType) {
		// 返回要写入响应的值的类型。
		// 通常这是通过 getClass 对值进行的简单检查，但如果值为 null，则需要检查返回类型，可能包括泛型类型确定（例如ResponseEntity<T> ）

		// 1. 主要就是对value为null时,进行一个额外处理
		return (value != null ? value.getClass() : returnType.getParameterType());
	}

	/**
	 * Return whether the returned value or the declared return type extends {@link Resource}.
	 */
	protected boolean isResourceType(@Nullable Object value, MethodParameter returnType) {
		Class<?> clazz = getReturnValueType(value, returnType);
		return clazz != InputStreamResource.class && Resource.class.isAssignableFrom(clazz);
	}

	/**
	 * Return the generic type of the {@code returnType} (or of the nested type
	 * if it is an {@link HttpEntity}).
	 */
	private Type getGenericType(MethodParameter returnType) {
		if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
			return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric().getType();
		}
		else {
			return returnType.getGenericParameterType();
		}
	}

	/**
	 * Returns the media types that can be produced.
	 * @see #getProducibleMediaTypes(HttpServletRequest, Class, Type)
	 */
	@SuppressWarnings("unused")
	protected List<MediaType> getProducibleMediaTypes(HttpServletRequest request, Class<?> valueClass) {
		return getProducibleMediaTypes(request, valueClass, null);
	}

	/**
	 * Returns the media types that can be produced. The resulting media types are:
	 * <ul>
	 * <li>The producible media types specified in the request mappings, or
	 * <li>Media types of configured converters that can write the specific return value, or
	 * <li>{@link MediaType#ALL}
	 * </ul>
	 * @since 4.2
	 */
	@SuppressWarnings("unchecked")
	protected List<MediaType> getProducibleMediaTypes(
			HttpServletRequest request, Class<?> valueClass, @Nullable Type targetType) {
		// 返回可以生成的媒体类型。生成的媒体类型为：
		// 请求映射中指定的可生产媒体类型，或
		// 可以写入特定返回值的已配置转换器的媒体类型，或
		// MediaType.ALL

		// 忽略
		Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			return new ArrayList<>(mediaTypes);
		}
		// allSupportedMediaTypes字段是超类在消息转换器中获取的
		// 1. 只要HttpMessageConverter支持canWrite这个valueClass或者targetType\valueClass
		// 就将这个Converter的支持的MediaType存入到result中
		else if (!this.allSupportedMediaTypes.isEmpty()) {
			List<MediaType> result = new ArrayList<>();
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				if (converter instanceof GenericHttpMessageConverter && targetType != null) {
					if (((GenericHttpMessageConverter<?>) converter).canWrite(targetType, valueClass, null)) {
						result.addAll(converter.getSupportedMediaTypes());
					}
				}
				else if (converter.canWrite(valueClass, null)) {
					result.addAll(converter.getSupportedMediaTypes());
				}
			}
			return result;
		}
		else {
			return Collections.singletonList(MediaType.ALL);
		}
	}

	private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
			throws HttpMediaTypeNotAcceptableException {

		return this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
	}

	/**
	 * Return the more specific of the acceptable and the producible media types
	 * with the q-value of the former.
	 */
	private MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
		MediaType produceTypeToUse = produceType.copyQualityValue(acceptType);
		return (MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceTypeToUse) <= 0 ? acceptType : produceTypeToUse);
	}

	/**
	 * Check if the path has a file extension and whether the extension is either
	 * on the list of {@link #SAFE_EXTENSIONS safe extensions} or explicitly
	 * {@link ContentNegotiationManager#getAllFileExtensions() registered}.
	 * If not, and the status is in the 2xx range, a 'Content-Disposition'
	 * header with a safe attachment file name ("f.txt") is added to prevent
	 * RFD exploits.
	 */
	private void addContentDispositionHeader(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		if (headers.containsKey(HttpHeaders.CONTENT_DISPOSITION)) {
			return;
		}

		try {
			int status = response.getServletResponse().getStatus();
			if (status < 200 || (status > 299 && status < 400)) {
				return;
			}
		}
		catch (Throwable ex) {
			// ignore
		}

		HttpServletRequest servletRequest = request.getServletRequest();
		String requestUri = UrlPathHelper.rawPathInstance.getOriginatingRequestUri(servletRequest);

		int index = requestUri.lastIndexOf('/') + 1;
		String filename = requestUri.substring(index);
		String pathParams = "";

		index = filename.indexOf(';');
		if (index != -1) {
			pathParams = filename.substring(index);
			filename = filename.substring(0, index);
		}

		filename = UrlPathHelper.defaultInstance.decodeRequestString(servletRequest, filename);
		String ext = StringUtils.getFilenameExtension(filename);

		pathParams = UrlPathHelper.defaultInstance.decodeRequestString(servletRequest, pathParams);
		String extInPathParams = StringUtils.getFilenameExtension(pathParams);

		if (!safeExtension(servletRequest, ext) || !safeExtension(servletRequest, extInPathParams)) {
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
		}
	}

	@SuppressWarnings("unchecked")
	private boolean safeExtension(HttpServletRequest request, @Nullable String extension) {
		if (!StringUtils.hasText(extension)) {
			return true;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		if (this.safeExtensions.contains(extension)) {
			return true;
		}
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null && pattern.endsWith("." + extension)) {
			return true;
		}
		if (extension.equals("html")) {
			String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
			Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(name);
			if (!CollectionUtils.isEmpty(mediaTypes) && mediaTypes.contains(MediaType.TEXT_HTML)) {
				return true;
			}
		}
		MediaType mediaType = resolveMediaType(request, extension);
		return (mediaType != null && (safeMediaType(mediaType)));
	}

	@Nullable
	private MediaType resolveMediaType(ServletRequest request, String extension) {
		MediaType result = null;
		String rawMimeType = request.getServletContext().getMimeType("file." + extension);
		if (StringUtils.hasText(rawMimeType)) {
			result = MediaType.parseMediaType(rawMimeType);
		}
		if (result == null || MediaType.APPLICATION_OCTET_STREAM.equals(result)) {
			result = MediaTypeFactory.getMediaType("file." + extension).orElse(null);
		}
		return result;
	}

	private boolean safeMediaType(MediaType mediaType) {
		return (SAFE_MEDIA_BASE_TYPES.contains(mediaType.getType()) ||
				mediaType.getSubtype().endsWith("+xml"));
	}

}
