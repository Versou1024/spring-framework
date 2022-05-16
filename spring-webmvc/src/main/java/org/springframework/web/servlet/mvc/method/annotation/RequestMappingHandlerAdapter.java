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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ErrorsMethodArgumentResolver;
import org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.annotation.SessionAttributesHandler;
import org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

/**
 * Extension of {@link AbstractHandlerMethodAdapter} that supports
 * {@link RequestMapping @RequestMapping} annotated {@link HandlerMethod HandlerMethods}.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers},
 * or alternatively, to re-configure all argument and return value types,
 * use {@link #setArgumentResolvers} and {@link #setReturnValueHandlers}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter implements BeanFactoryAware, InitializingBean {

	/**
	 * 实际的HandlerAdapter，功能十分强大
	 *
	 * 1、聚合BeanFactory，能够从ioc容器查找Bean
	 * 2、实现InitializingBean，完成初始化操作
	 * 3、聚合有
	 * 		参数解析器HandlerMethodArgumentResolver、
	 * 		返回值解析器HandlerMethodReturnValueHandler、
	 * 		模型和视图解析器ModelAndViewResolver
	 * 		消息转换器HttpMessageConverter
	 * 		数据绑定工厂webBindingInitializer
	 *
	 * 关注重点：
	 * 1、初始化过程
	 * 2、AbstractHandlerMethodAdapter 的 supportInternal、handleInternal 两个protected抽象方法
	 */

	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 */
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, InitBinder.class);
	// 方法过滤器 -- 用于过滤带有@InitBinder注解的属性

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) &&
					AnnotatedElementUtils.hasAnnotation(method, ModelAttribute.class));
	// 方法过滤器 -- 用于过滤带有@RequestMapping且@ModelAttribute


	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;

	// 这里保存在用户自定义的一些处理器，大部分情况下无需自定义~~~
	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	// 保存着所有的默认的返回值处理器~~~~同时上面custom自定义的最终也会放进来，放在尾部
	// 从它的命名似乎可议看出，它就是汇总~~~
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	// ModelAndViewResolver木有内置实现，可自定义实现来参与到返回值到ModelAndView的过程（自定义返回值处理）
	// 一般不怎么使用，我个人也不太推荐使用
	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;

	// 内容协商管理器  默认就是它喽（使用的协商策略是HeaderContentNegotiationStrategy）
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	// 消息转换器。使用@Bean定义的时候，记得set进来，否则默认只会有4个（不支持json）
	// 若@EnableWebMvc后默认是有8个的，一般都够用了
	private List<HttpMessageConverter<?>> messageConverters;

	//  装载RequestBodyAdvice和ResponseBodyAdvice的实现类们~
	private final List<Object> requestResponseBodyAdvice = new ArrayList<>();

	// 它在数据绑定初始化的时候会被使用到，调用其initBinder()方法
	// 只不过，现在一般都使用@InitBinder注解来处理了，所以使用较少
	// 说明：它作用域是全局的，对所有的HandlerMethod都生效~~~~~
	@Nullable
	private WebBindingInitializer webBindingInitializer;

	// 默认使用的SimpleAsyncTaskExecutor：每次执行客户提交给它的任务时，它会启动新的线程
	// 并允许开发者控制并发线程的上限（concurrencyLimit），从而起到一定的资源节流作用（默认值是-1，表示不限流）
	// @EnableWebMvc时可通过复写接口的WebMvcConfigurer.getTaskExecutor()自定义提供一个线程池
	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	// invokeHandlerMethod()执行目标方法时若需要异步执行，超时时间可自定义（默认不超时）
	// 使用上面的taskExecutor以及下面的callableInterceptors/deferredResultInterceptors参与异步的执行
	@Nullable
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	// 对应ModelAndViewContainer.setIgnoreDefaultModelOnRedirect()属性
	// 重定向时redirect时,是否忽略defaultModel 默认值是false：不忽略
	private boolean ignoreDefaultModelOnRedirect = false;

	// 返回内容缓存多久（默认不缓存）  参考类：WebContentGenerator
	private int cacheSecondsForSessionAttributeHandlers = 0;

	// 执行目标方法HandlerMethod时是否要在同一个Session内同步执行？？？
	// 也就是同一个会话时，控制器方法全部同步执行（加互斥锁）
	// 使用场景：对同一用户同一Session的所有访问，必须串行化~~~~~~
	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Nullable
	private ConfigurableBeanFactory beanFactory;

	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	// 存储标注了@InitBinder注解的方法的缓存~~~~
	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	// 存储标注了@ModelAttribute注解的方法的缓存~~~~
	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();


	public RequestMappingHandlerAdapter() {
		// 唯一构造函数
		// 开启@EnableWebMvc后此默认行为会被setMessageConverters()方法覆盖
		this.messageConverters = new ArrayList<>(4);
		// 默认的转换器包括：httpMessage 转为 字节数组、String、Source类、
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		try {
			this.messageConverters.add(new SourceHttpMessageConverter<>());
		}
		catch (Error err) {
			// Ignore when no TransformerFactory implementation is available
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver ModelAndViewResolvers}.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver ModelAndViewResolvers}, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		// 首先，它可能会添加ResponseBody建议bean
		initControllerAdviceCache();

		// 这三大部分，可是 "参数自动组装" 相关的组件~~~~每一份都非常的重要
		if (this.argumentResolvers == null) {
			// 这个getDefaultReturnValueHandlers()会装载15个左右的返回值处理器，可以说覆盖我们日常开发的所有
			// 若你自己自定义了custom的，放进了customReturnValueHandlers里，最终也会被加进来放进去~~~~ 放在末尾~~~~
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		// 初始化 @ControllerAdvice 注解的类
		// 步骤:
		// 	1. 找到容器内（包括父容器）所有的标注有@ControllerAdvice注解的Bean们缓存起来，然后一个个解析此种Bean
		//	2. 找到该Advice Bean内所有的标注有@ModelAttribute但没标注@RequestMapping的方法们，缓存到modelAttributeAdviceCache里对全局生效
		//	3. 找到该Advice Bean内所有的标注有@InitBinder的方法们，缓存到initBinderAdviceCache里对全局生效
		//	4. 找到该Advice Bean内所有实现了接口RequestBodyAdvice/ResponseBodyAdvice们，最终放入缓存requestResponseBodyAdvice的头部，他们会介入请求body和返回body

		if (getApplicationContext() == null) {
			return;
		}
		// 1. 查看@ControllerAdvice注解的类，并封装为ControllerAdviceBean，然后获取排好序的集合结果
		// 拿到容器内所有的标注有@ControllerAdvice的组件们
		// BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, Object.class)
		// .filter(name -> context.findAnnotationOnBean(name, ControllerAdvice.class) != null)
		// .map(name -> new ControllerAdviceBean(name, context)) // 使用ControllerAdviceBean包装起来，持有name的引用（还木实例化哟）
		// .collect(Collectors.toList());

		// 因为@ControllerAdvice注解可以指定包名等属性，具体可参见HandlerTypePredicate的判断逻辑，是否生效
		// 注意：@RestControllerAdvice是@ControllerAdvice和@ResponseBody的结合体，所以此处也会被找出来
		// 最后Ordered排序
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		// 2. 临时存储RequestBodyAdvice和ResponseBodyAdvice的实现类
		// 它哥俩是必须配合@ControllerAdvice一起使用的~
		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		// 3. 开始遍历
		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			// 3.1 检查@ControllerAdvice标注的类，是否有@ModeAttribute注解的方法
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods); // modelAttribute增强方法的容器
			}
			// 3.2 检查@ControllerAdvice标注的类，是否有@InitBinder注解的方法
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods); // initBinder增强方法的容器
			}
			// 3.3 不但带有@ControllerAdvice注解，同时还是RequestBodyAdvice或ResponseBodyAdvice全局增强接口的实现类
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}

		// 4. 这个意思是，放在该list的头部。
		// 因为requestResponseBodyAdvice有可能通过set方法进来已经有值了~~~所以此处放在头部
		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		// 5. 日志打印忽略
		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

	// Count all advice, including explicit registrations..

	private int getBodyAdviceCount(Class<?> adviceType) {
		List<Object> advice = this.requestResponseBodyAdvice;
		return RequestBodyAdvice.class.isAssignableFrom(adviceType) ?
				RequestResponseBodyAdviceChain.getAdviceByType(advice, RequestBodyAdvice.class).size() :
				RequestResponseBodyAdviceChain.getAdviceByType(advice, ResponseBodyAdvice.class).size();
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		// 注册默认的参数解析器

		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		// 1. Annotation-based argument resolution -- 基于注解的参数解析器
		// 常见注解：@RequestParam、@RequestParamMap、@PathVariable、@PathVariableMap
		// @MatrixVariable、@MatrixVariableMap、@ServletModelAttribute、@RequestBody...
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// 2. Type-based argument resolution -- 基于方法参数Class的参数解析器，
		// 方法形参为：ServletRequest、ServletResponse、Model、Errors、SessionStatus..
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());

		// 3. Custom arguments -- 用户定制的参数解析器
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers()); // 可通过WebMvcSupport中添加定制参数解析器
		}

		// 4. 滴水不漏: 兜底方案：这就是为何很多时候不写注解参数也能够被自动封装的原因
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * Return the list of argument resolvers to use for {@code @InitBinder}
	 * methods including built-in and custom resolvers.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		// 返回@InitBinder方法使用的参数解析程序列表，包括内置和自定义解析程序。
		// 这一步和getDefaultArgumentResolvers()相似，也是对方法参数的处理，
		// 不过它针对的是@InitBinder这个注解标注的方法。可简单理解为它是@RequestMapping的阉割版：支持的参数类型、注解都没那么多，支持内容如下

		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		// 1. Annotation-based argument resolution -- 基于注解的解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// 2. Type-based argument resolution -- 基于类型Class的解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// 3. Custom arguments -- 扩展自定义解析器
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// 4. Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		// 返回要使用的ReturnValueHandlers，包括通过setReturnValueHandlers提供的内置和自定义处理程序。
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		// 1. Single-purpose return value types -- 单一用途返回类型
		// 例如：返回ModelAndView、Callable[web异步支持的]、
		handlers.add(new ModelAndViewMethodReturnValueHandler()); // 专门处理ModelAndView
		handlers.add(new ModelMethodProcessor()); // 专门处理Model
		handlers.add(new ViewMethodReturnValueHandler()); // 处理返回值是View类型的,由于View类型时固定用途,因此优先级比较高,防止被@ModelAttribute或@ResponseBody进行注解后被接管
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(), this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.contentNegotiationManager, this.requestResponseBodyAdvice)); // 返回值是HttpEntity
		handlers.add(new HttpHeadersReturnValueHandler()); // 单一用途,用以处理HttpHeaders类型
		handlers.add(new CallableMethodReturnValueHandler()); // 单一用途,用以处理Callable类型
		handlers.add(new DeferredResultMethodReturnValueHandler()); // 异步
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory)); // 异步

		// 2. Annotation-based return value types -- 带有注解标注的返回值
		// 单一用途的返回值处理器是比注解类型处理器优先级高,而多用途类型的处理器作为最后的兜底方案
		// 当标注有@ModelAttribute或者@ResponseBody的时候  这里来处理。显然也用到了消息转换器~
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.contentNegotiationManager, this.requestResponseBodyAdvice)); // ❗️❗️❗️
		// 要想@ResponseBody生效,前提就是返回值不能被单一用途的返回值处理器给拦截\以及@ModelAttribute拦截

		// 3. Multi-purpose return value types -- 多用途的返回值处理器
		// 当返回的是个字符串/Map时候，这时候就可能有多个目的了（Multi-purpose）
		// 比如字符串：可能重定向redirect、或者直接到某个view
		handlers.add(new ViewNameMethodReturnValueHandler()); // 对String的兜底处理
		handlers.add(new MapMethodProcessor()); // 对Map的兜底处理

		// 4. Custom return value types -- 自定义返回值处理器
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			// 需要从 getModelAndViewResolvers() 获取 List<ModelAndViewResolver>,然后才可以引入ModelAndViewResolverMethodReturnValueHandler
			// 使用定制的 ModelAndViewResolver 处理返回值
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		// 核心：直接返回true
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
		// ❗️❗️❗️ 核心：处理HandlerMethod的地方

		ModelAndView mav;
		// 1. 利用 webContentGenerator 判断请求方法是否为被我们Http所支持
		checkRequest(request);

		// 2. 同一个Session下是否要串行，显然一般都是不需要的。直接看invokeHandlerMethod()方法吧
		// false，跳过
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// No HttpSession available -> no mutex necessary
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		} else {
			// 3. 尝试执行HandlerMethod -- 核心
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		// 4. 处理Cache-Control这个请求头~~~~~~~~~（若你自己木有set的话）
		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}
		// 返回modelAndView
		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler type
	 * (never {@code null}).
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		// 如果Controller缺少对应的会话属性处理器，就需要创建并存入数据库中
		return this.sessionAttributesHandlerCache.computeIfAbsent(
				handlerMethod.getBeanType(),
				type -> new SessionAttributesHandler(type, this.sessionAttributeStore));
	}

	/**
	 * Invoke the {@link RequestMapping} handler method preparing a {@link ModelAndView}
	 * if view resolution is required.
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
		// 它的作用顾名思义：就是调用我们的目标方法，在这里会组织各个组件进行数据绑定、数据校验、内容协商等等操作控制

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			// 1. 最终创建的是一个ServletRequestDataBinderFactory，持有所有@InitBinder的method方法们
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
			// 2. 创建一个ModelFactory，@ModelAttribute啥的方法就会被引用进来
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			// 3. 把HandlerMethod包装为ServletInvocableHandlerMethod，具有invoke执行的能力喽
			// 下面这几部便是一直给invocableMethod的各大属性赋值~~~
			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
			if (this.argumentResolvers != null) {
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers); // 设置复合参数解析器
			}
			if (this.returnValueHandlers != null) {
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);// 设置复合返回值处理器
			}
			invocableMethod.setDataBinderFactory(binderFactory); // 设置数据工厂
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer); // 设置参数名发现器

			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			// 4. 把上个request里的值放进来到本request里
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			// 初始化中主要完成：将session相关属性、相关@ModelAttribute方法得出的属性等写入mavContainer
			// model工厂：把它里面的Model值放进mavContainer容器内（此处@ModelAttribute/@SessionAttribute啥的生效）
			modelFactory.initModel(webRequest, mavContainer, invocableMethod); // modelFactory初始化 -- 主要完成Model的数据初始化填充以及Controller执行完后的数据更新任务
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect); // 默认为false

			// 5. 忽略 -- SpringMVC的异步功能暂不考虑
			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);
			// 它不管是不是异步请求都先用AsyncWebRequest 包装了一下，但是若是同步请求
			// asyncManager.hasConcurrentResult()肯定是为false的~~~
			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(result, !traceOn);
					return "Resume with async result [" + formatted + "]";
				});
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}

			// 6. 核心❗️ -- 即将开始执行方法
			// 此处其实就是调用ServletInvocableHandlerMethod#invokeAndHandle()方法喽
			// 关于它你可以来这里：https://fangshixiang.blog.csdn.net/article/details/98385163
			// 注意哦：任何HandlerMethod执行完后都是把结果放在了mavContainer里（它可能有Model，可能有View，可能啥都木有~~）
			// 因此最后的getModelAndView()又得一看
			invocableMethod.invokeAndHandle(webRequest, mavContainer);// 核心方法 -- 开始执行目标方法
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}
			// 7. 从mac容器中获取ModelAndView，并利用modelFactory更新model
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {
			webRequest.requestCompleted();
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given {@link HandlerMethod} definition.
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		// 获取处理SessionAttrbute的处理器
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		// 查找当前Controller中是否有指定的@RequestMapping和@ModeAttribute注解方法
		Class<?> handlerType = handlerMethod.getBeanType();
		// 尝试从缓存中查找
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			// 缓存未命中，直接利用反射查找
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			// 存入缓存
			this.modelAttributeCache.put(handlerType, methods);
		}
		// List<InvocableHandlerMethod> 中的可执行Method是，需要提前于真实Request方法执行的@ModeAttribute标注的方法
		// 是用来处理数据封装的方法 -- 因此称为属性方法attrMethods
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// 全局方法优先
		this.modelAttributeAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});
		// 局部的Controller中的@ModelAttribute进入attrMethods的末尾
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		// 向 InvocableHandlerMethod 中注入参数解析器，形参名发现器以及DataBinderFactory
		// 这里的 InvocableHandlerMethod 应该是用 @ModelAttribute 标注的方法
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		// 获取HandlerMethod的外围BeanClass
		Class<?> handlerType = handlerMethod.getBeanType();
		// 从已有缓存initBinderCache中，获取key为handlerType对应@InitBinder标注的方法
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
			// 缓存未命中，需要手动检查然后加入缓存中，即检查Controller中带有@InitBinder注解的方法
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		// 注意：List<InvocableHandlerMethod>中的可执行method，并不是带有@GetMapping这种的Controller方法
		// 而是带有@InitBinder这种的可执行方法，用来处理数据绑定的方法
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// Global methods first -- 全局方法首先执行
		// 因此遍历initBinderAdviceCache即@ControllerAdvice类中的@InitBinder的方法，
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			// 当前HanlderType是否满足controllerAdiviceBean的要求
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
		// 然后再将局部的@InitBinder方法注入到initBinderMethods，这样在集合中的位置，就低于@ControllerAdvice的@InitBinder
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		return createDataBinderFactory(initBinderMethods); // DataBinderFactory
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {
		// 将 初始化绑定参数方法 与 WebDataBinder的初始化器 传递给 DataBinderFactory
		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer, ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {
		// 作用: 构建ModelAndView

		// 1. modelFactory更新model,将seesino里面的内容写入
		modelFactory.updateModel(webRequest, mavContainer);
		// 2. 如果mavContainer中的request已经被处理了,就返回null
		if (mavContainer.isRequestHandled()) {
			return null;
		}
		// 3. request还没有被处理,获取model
		ModelMap model = mavContainer.getModel();
		// 4. 根据viewName\model\httpStatus构造mav
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		if (!mavContainer.isViewReference()) { // 是否为String类型
			mav.setView((View) mavContainer.getView()); // 强转为View类型的
		}
		// 4. 对重定向RedirectAttributes参数的支持（两个请求之间传递参数，使用的是ATTRIBUTE）
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;

		// 执行完HandlerMethod后得到一个ModelAndView，它可能是null（比如已被处理过），
		// 那么最终交给DispatcherServlet就没有后续处理（渲染）了，否则会做视图渲染：render()。
	}

}
