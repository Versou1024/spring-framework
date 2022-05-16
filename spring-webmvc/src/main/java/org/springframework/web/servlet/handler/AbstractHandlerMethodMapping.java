/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.web.servlet.handler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
 * needed to match the handler method to an incoming request.
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

	/*
	 * AbstractHandlerMethodMapping 实现 AbstractHandlerMapping
	 *
	 * 内部类：MappingRegistry 作为 HandlerMethod 与 url 映射的注册表，
	 * 这说明所有的Url和HandlerMethod都已经提前注册好啦
	 * 聚合 MappingRegistry ，表明有url->HandlerMethod的注册中心
	 * 由于当前类实现InitializingBean，因此也需要重点关注初始化方法
	 *
	 * 在前面我们已经知道了AbstractHandlerMethodMapping的父类AbstractHandlerMapping，其定义了抽象方法getHandlerInternal(HttpServletRequest request)，那么这里主要看看它对此抽象方法的实现：
	 * AbstractHandlerMethodMapping系列是将method作为handler来使用的，比如@RequestMapping所注释的方法就是这种handler（当然它并不强制你一定得使用@RequestMapping这样的注解）。
	 */


	/**
	 * Bean name prefix for target beans behind scoped proxies. Used to exclude those
	 * targets from handler method detection, in favor of the corresponding proxies.
	 * <p>We're not checking the autowire-candidate status here, which is how the
	 * proxy target filtering problem is being handled at the autowiring level,
	 * since autowire-candidate may have been turned to {@code false} for other
	 * reasons, while still expecting the bean to be eligible for handler methods.
	 * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
	 * but duplicated here to avoid a hard dependency on the spring-aop module.
	 */
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH = new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration(); // 默认的跨域配置

	static {
		ALLOW_CORS_CONFIG.addAllowedOrigin("*");
		ALLOW_CORS_CONFIG.addAllowedMethod("*");
		ALLOW_CORS_CONFIG.addAllowedHeader("*");
		ALLOW_CORS_CONFIG.setAllowCredentials(true);
	}


	// 默认不会去祖先容器里面找Handlers
	private boolean detectHandlerMethodsInAncestorContexts = false;

	// HandlerMethodMappingNamingStrategy是@since 4.1 提供的新接口
	// 为处HandlerMethod的映射分配名称的策略接口,只有一个方法getName()
	// 唯一实现为：RequestMappingInfoHandlerMethodMappingNamingStrategy
	// 策略为：
	// 		@RequestMapping指定了name属性，那就以指定的为准
	// 		否则策略为：取出Controller所有的`大写字母` + # + method.getName()
	// 如：AppoloController#match方法  最终的name为：AC#match
	// 当然这个你也可以自己实现这个接口，然后set进来即可（只是一般没啥必要这么去干~~）
	@Nullable
	private HandlerMethodMappingNamingStrategy<T> namingStrategy;

	// url -> HandlerMethod 的注册中心 内部类：负责注册~
	private final MappingRegistry mappingRegistry = new MappingRegistry();


	/**
	 * Whether to detect handler methods in beans in ancestor ApplicationContexts.
	 * <p>Default is "false": Only beans in the current ApplicationContext are
	 * considered, i.e. only in the context that this HandlerMapping itself
	 * is defined in (typically the current DispatcherServlet's context).
	 * <p>Switch this flag on to detect handler beans in ancestor contexts
	 * (typically the Spring root WebApplicationContext) as well.
	 * @see #getCandidateBeanNames()
	 */
	public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
		this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
	}

	/**
	 * Configure the naming strategy to use for assigning a default name to every
	 * mapped handler method.
	 * <p>The default naming strategy is based on the capital letters of the
	 * class name followed by "#" and then the method name, e.g. "TC#getFoo"
	 * for a class named TestController with method getFoo.
	 */
	public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	/**
	 * Return the configured naming strategy or {@code null}.
	 */
	@Nullable
	public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
		return this.namingStrategy;
	}

	/**
	 * Return a (read-only) map with all mappings and HandlerMethod's.
	 */
	public Map<T, HandlerMethod> getHandlerMethods() {
		// 使用的是读写锁, 获取HandlerMethods
		this.mappingRegistry.acquireReadLock();
		try {
			return Collections.unmodifiableMap(this.mappingRegistry.getMappings());
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Return the handler methods for the given mapping name.
	 * @param mappingName the mapping name
	 * @return a list of matching HandlerMethod's or {@code null}; the returned
	 * list will never be modified and is safe to iterate.
	 * @see #setHandlerMethodMappingNamingStrategy
	 */
	@Nullable
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		// 此处是根据mappingName来获取一个Handler  此处需要注意哦~~~
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	/**
	 * Return the internal mapping registry. Provided for testing purposes.
	 */
	MappingRegistry getMappingRegistry() {
		return this.mappingRegistry;
	}

	/**
	 * Register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping for the handler method
	 * @param handler the handler
	 * @param method the method
	 */
	public void registerMapping(T mapping, Object handler, Method method) {
		if (logger.isTraceEnabled()) {
			logger.trace("Register \"" + mapping + "\" to " + method.toGenericString());
		}
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Un-register the given mapping.
	 * <p>This method may be invoked at runtime after initialization has completed.
	 * @param mapping the mapping to unregister
	 */
	public void unregisterMapping(T mapping) {
		if (logger.isTraceEnabled()) {
			logger.trace("Unregister mapping \"" + mapping + "\"");
		}
		this.mappingRegistry.unregister(mapping);
	}


	// Handler method detection

	/**
	 * Detects handler methods at initialization.
	 * @see #initHandlerMethods
	 */
	@Override
	public void afterPropertiesSet() {
		/*
		 * ❗️核心方法：加载url->HandlerMethod的映射
		 * 这个很重要，是初始化HandlerMethods的入口~~~~~
		 */
		initHandlerMethods();
	}

	/**
	 * Scan beans in the ApplicationContext, detect and register handler methods.
	 * @see #getCandidateBeanNames()
	 * @see #processCandidateBean
	 * @see #handlerMethodsInitialized
	 */
	protected void initHandlerMethods() {
		/*
		 * 初始化内部的HandlerMethods
		 * 获取所有的候选Bean，如果候选Bean不是以scopedTarget.开头，都需要 processCandidateBean 进入处理
		 */
		// 1. getCandidateBeanNames：Object.class相当于拿到当前容器（一般都是当前容器） 内所有的Bean定义信息
		// 如果阁下容器隔离到到的话，这里一般只会拿到@Controller标注的web组件  以及其它相关web组件的  不会非常的多的~~~~
		for (String beanName : getCandidateBeanNames()) {
			// 2. BeanName不是以这个打头得  这里才会process这个BeanName~~~~
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				// 3. 会在每个Bean里面找处理方法，HandlerMethod，然后注册进去
				processCandidateBean(beanName); // 重点关注：接下来就就会判断是否为Handler、是否为可执行的HandlerMethod
			}
		}
		// 4. 略：它就是输出一句日志：debug日志或者trace日志   `7 mappings in 'requestMappingHandlerMapping'`
		handlerMethodsInitialized(getHandlerMethods());
	}

	/**
	 * Determine the names of candidate beans in the application context.
	 * @since 5.1
	 * @see #setDetectHandlerMethodsInAncestorContexts
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors
	 */
	protected String[] getCandidateBeanNames() {
		// 所谓的候选Bean就是，只要在IOC容器中是一个Object对象即可
		return (this.detectHandlerMethodsInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(obtainApplicationContext(), Object.class) :
				obtainApplicationContext().getBeanNamesForType(Object.class));
	}

	/**
	 * Determine the type of the specified candidate bean and call
	 * {@link #detectHandlerMethods} if identified as a handler type.
	 * <p>This implementation avoids bean creation through checking
	 * {@link org.springframework.beans.factory.BeanFactory#getType}
	 * and calling {@link #detectHandlerMethods} with the bean name.
	 * @param beanName the name of the candidate bean
	 * @since 5.1
	 * @see #isHandler
	 * @see #detectHandlerMethods
	 */
	protected void processCandidateBean(String beanName) {
		// 确定指定的候选bean的类型，如果标识为Handler类型，则调用DetectHandlerMethods
		// isHandler(beanType):判断这个type是否为Handler类型   它是个抽象方法，由子类去决定到底啥才叫Handler~~~~
		// `RequestMappingHandlerMapping`的判断依据为：该类上标注了@Controller注解或者@Controller注解  就算作是一个Handler
		// 所以此处：@Controller起到了一个特殊的作用，不能等价于@Component的哟~~~~

		Class<?> beanType = null;
		try {
			// 1. 获取bean的class
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}
		// 2. isHandler用于判断是否为Handler类，例如类上有注解@Controller或者@RestController 【抽象方法】
		if (beanType != null && isHandler(beanType)) {
			// 3. 如果是Handler类,就需要检查类中有哪些是HandlerMethod
			// 检查handler中的HandlerMethod,属于探测系列
			detectHandlerMethods(beanName);
		}
	}

	/**
	 * Look for handler methods in the specified handler bean.
	 * @param handler either a bean name or an actual handler instance
	 * @see #getMappingForMethod
	 */
	protected void detectHandlerMethods(Object handler) {
		Class<?> handlerType = (handler instanceof String ? obtainApplicationContext().getType((String) handler) : handler.getClass());

		if (handlerType != null) {
			// 1. 获取handlerType的用户类，即有可能handlerType是代理对象
			Class<?> userType = ClassUtils.getUserClass(handlerType);
			// 2. MethodIntrospector.selectMethods就是用来遍历userType下的所有method，并调用函数式接口MethodFilter进行过滤，并返回符合的集合
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							return getMappingForMethod(method, userType); // 【抽象方法】具体实现交给子类，由子类定义什么才是真的HandlerMethod
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}
			// 3. 已经找到所有的Handler和Method后，就需要注册到 HandlerMethodRegistry 注册表中
			methods.forEach((method, mapping) -> {
				// 3.1 反射获取创建可执行的method
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				// 3.2 注册进去 handle/invocableMethod/mapping
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

	private String formatMappings(Class<?> userType, Map<Method, T> methods) {
		String formattedType = Arrays.stream(ClassUtils.getPackageName(userType).split("\\."))
				.map(p -> p.substring(0, 1))
				.collect(Collectors.joining(".", "", "." + userType.getSimpleName()));
		Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
				.map(Class::getSimpleName)
				.collect(Collectors.joining(",", "(", ")"));
		return methods.entrySet().stream()
				.map(e -> {
					Method method = e.getKey();
					return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
				})
				.collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
	}

	/**
	 * Register a handler method and its unique mapping. Invoked at startup for
	 * each detected handler method.
	 * @param handler the bean name of the handler or the handler instance
	 * @param method the method to register
	 * @param mapping the mapping conditions associated with the handler method
	 * @throws IllegalStateException if another method was already registered
	 * under the same mapping
	 */
	protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		this.mappingRegistry.register(mapping, handler, method);
	}

	/**
	 * Create the HandlerMethod instance.
	 * @param handler either a bean name or an actual handler instance
	 * @param method the target method
	 * @return the created HandlerMethod
	 */
	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		if (handler instanceof String) {
			return new HandlerMethod((String) handler,
					obtainApplicationContext().getAutowireCapableBeanFactory(), method);
		}
		return new HandlerMethod(handler, method);
	}

	/**
	 * Extract and return the CORS configuration for the mapping.
	 */
	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
		return null;
	}

	/**
	 * Invoked after all handler methods have been detected.
	 * @param handlerMethods a read-only map with handler methods and mappings.
	 */
	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
		// Total includes detected mappings + explicit registrations via registerMapping
		int total = handlerMethods.size();
		if ((logger.isTraceEnabled() && total == 0) || (logger.isDebugEnabled() && total > 0) ) {
			logger.debug(total + " mappings in " + formatMappingName());
		}
	}


	// Handler method lookup

	/**
	 * 查找给定request的handler method。
	 */
	@Override
	@Nullable
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		/**
		 * ❗️核心方法：
		 * 查找request对应的HandlerMethod
		 */

		// 1. 通过父类聚合的UrlPathHelper获取当前请求request的查找路径
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		request.setAttribute(LOOKUP_PATH, lookupPath); // 设置到属性中
		this.mappingRegistry.acquireReadLock();
		try {
			// 2. 通过已经缓存的MappingRegistry进行查找
			HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	/**
	 * Look up the best-matching handler method for the current request.
	 * If multiple matches are found, the best match is selected.
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @return the best-matching handler method, or {@code null} if no match
	 * @see #handleMatch(Object, String, HttpServletRequest)
	 * @see #handleNoMatch(Set, String, HttpServletRequest)
	 */
	@Nullable
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		// Match是一个private class，内部就两个属性：T mapping和HandlerMethod handlerMethod
		List<Match> matches = new ArrayList<>();
		// 1. 通过url精准查询符合的RequestMappingInfo
		// 根据lookupPath去注册中心里查找mappingInfos，因为一个具体的url可能匹配上多个MappingInfo的
		// 至于为何是多值？有这么一种情况  URL都是/api/v1/hello  但是有的是get post delete等方法  当然还有可能是headers/consumes等等不一样，都算多个的  所以有可能是会匹配到多个MappingInfo的
		// 所有这个里可以匹配出多个出来。比如/hello 匹配出GET、POST、PUT都成，所以size可以为3
		List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
		if (directPathMatches != null) {
			// 2. 精准查询成功
			// 通过 RequestMappingInfo 去 mappingRegistry 查询对应的HandlerMethod，然后将 HandlerMethod + RequestMappingInfo 组合为 Match 并设置到 matches 中
			// 依赖于子类实现的抽象方法：getMatchingMapping()  看看到底匹不匹配，而不仅仅是URL匹配就行
			// 比如还有method、headers、consumes等等这些不同都代表着不同的MappingInfo的
			// 最终匹配上的，会new Match()放进matches里面去
			addMatchingMappings(directPathMatches, matches, request); // 添加Match到matches
		}
		// 3. 精准匹配URL失败的时候，别无选择，只能浏览所有校验信息mapping
		// 这里为何要浏览所有的mappings呢？而不是报错404呢？
		// 因此一个模糊匹配
		if (matches.isEmpty()) {
			addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
		}

		// 4. 一般都会进入当前的matches中
		// 最终只能找到了一个匹配的,所以需要就进来这里了~~~
		// 请注意：到这里匹配上的可能还不止一个,所以才需要继续处理~~
		if (!matches.isEmpty()) {
			Match bestMatch = matches.get(0);
			// 5. 如果请求有多个匹配结果Match,就需要检查谁更满足点
			// 即使url\headers\请求方式\customer都匹配
			// 还需要进一步比较url: 有的可能是模式匹配,没有精准匹配的优先级高
			if (matches.size() > 1) {
				// 5.1 Match比较器
				Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
				matches.sort(comparator);
				// 5.2 排序后最佳的匹配matches
				bestMatch = matches.get(0);
				if (logger.isTraceEnabled()) {
					logger.trace(matches.size() + " matching mappings: " + matches);
				}
				if (CorsUtils.isPreFlightRequest(request)) {
					return PREFLIGHT_AMBIGUOUS_MATCH;
				}
				// 5.3 次最佳的匹配matches
				Match secondBestMatch = matches.get(1);
				// 5.4 如果,实际上最佳的和最次佳还是一样的
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					String uri = request.getRequestURI();
					throw new IllegalStateException(
							"Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
				}
			}
			// 向request中注入追加的执行的HandlerMethod
			request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.handlerMethod);
			// 向request中设置查找的路径属性
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.handlerMethod;
		} else {
			// 一个都没匹配上，handleNoMatch这个方法虽然不是抽象方法，protected方法子类复写
			// RequestMappingInfoHandlerMapping有复写此方法~~~~
			return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		// mappings 需要遍历的MappingInfo
		// matches 需要存入的Match的集合
		// request 请求
		for (T mapping : mappings) {
			// 只有RequestMappingInfoHandlerMapping 实现了一句话：return info.getMatchingCondition(request);
			// 因此RequestMappingInfo#getMatchingCondition() 方法里大有文章可为~~~
			// 它会对所有的methods、params、headers... 都进行匹配  但凡匹配不上的就返回null
			// mapping 就是 RequestMappingInfo,里面有url/method/header等匹配器
			T match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
			}
		}
	}

	/**
	 * Invoked when a matching mapping is found.
	 * @param mapping the matching mapping
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 */
	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		// 设置查找的路径属性
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
	}

	/**
	 * Invoked when no matching mapping is not found.
	 * @param mappings all registered mappings
	 * @param lookupPath mapping lookup path within the current servlet mapping
	 * @param request the current request
	 * @throws ServletException in case of errors
	 */
	@Nullable
	protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
			throws Exception {

		return null;
	}

	@Override
	protected boolean hasCorsConfigurationSource(Object handler) {
		return super.hasCorsConfigurationSource(handler) ||
				(handler instanceof HandlerMethod && this.mappingRegistry.getCorsConfiguration((HandlerMethod) handler) != null);
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
			}
			else {
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}


	// Abstract template methods

	/**
	 * Whether the given type is a handler with handler methods.
	 * @param beanType the type of the bean being checked
	 * @return "true" if this a handler type, "false" otherwise.
	 */
	protected abstract boolean isHandler(Class<?> beanType);
	// 当前整个Class是否为Handler类

	/**
	 * Provide the mapping for a handler method. A method for which no
	 * mapping can be provided is not a handler method.
	 * @param method the method to provide a mapping for
	 * @param handlerType the handler type, possibly a sub-type of the method's
	 * declaring class
	 * @return the mapping, or {@code null} if the method is not mapped
	 */
	@Nullable
	protected abstract T getMappingForMethod(Method method, Class<?> handlerType);
	// 判断Handler即handlerType中的method是否为HandlerMethod,一般就是Method上有@GetMapping这种注解,就是HandlerMethod
	// 是的话,就需要返回这个HandlerMethod的相关信息封装类RequestMappingInfo

	/**
	 * Extract and return the URL paths contained in the supplied mapping.
	 */
	protected abstract Set<String> getMappingPathPatterns(T mapping);
	//

	/**
	 * Check if a mapping matches the current request and return a (potentially
	 * new) mapping with conditions relevant to the current request.
	 * @param mapping the mapping to get a match for
	 * @param request the current HTTP servlet request
	 * @return the match, or {@code null} if the mapping doesn't match
	 */
	@Nullable
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);
	// 需要子类根据request是否完全符合mapping的定义,如果是的话返回匹配的mappinginfo
	// 如果不匹配就返回一个null吧

	/**
	 * Return a comparator for sorting matching mappings.
	 * The returned comparator should sort 'better' matches higher.
	 * @param request the current request
	 * @return the comparator (never {@code null})
	 */
	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);
	// 需要子类提供Mapping比较器
	// 主要是在request匹配多个match[url/header/等需求都满足时],就需要进一步查找追加的match

	/**
	 * 非常重要：非常重要：
	 * MappingRegistry一个HandlerMethod和对应url的注册表，它维护到处理程序方法的所有映射，公开方法以执行查找并提供并发访问。
	 */
	class MappingRegistry {
		/*
		 * 提供url到T的映射 - urlLookup
		 * 提供T到HandlerMethod的以色列和 - mappingLookup
		 * mappingName映射到其拥有的HandlerMethod - nameLookup
		 */

		// mapping对应的其MappingRegistration对象~~~
		private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

		// 保存着mapping和HandlerMethod的对应关系~
		private final Map<T, HandlerMethod> mappingLookup = new LinkedHashMap<>();

		// 保存着URL与匹配条件（mapping）的对应关系  当然这里的URL是pattern式的，可以使用通配符
		// 这里的Map不是普通的Map，而是MultiValueMap，它是个多值Map。其实它的value是一个list类型的值
		// 至于为何是多值？有这么一种情况  URL都是/api/v1/hello  但是有的是get post delete等方法   所以有可能是会匹配到多个MappingInfo的
		private final MultiValueMap<String, T> urlLookup = new LinkedMultiValueMap<>();

		// 这个Map是Spring MVC4.1新增的（毕竟这个策略接口HandlerMethodMappingNamingStrategy在Spring4.1后才有,这里的name是它生成出来的）
		// 保存着name和HandlerMethod的对应关系（一个name可以有多个HandlerMethod）
		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

		// 这两个就不用解释了
		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		// 读写锁~~~ 读写分离  提高启动效率
		private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

		/**
		 * Return all mappings and handler methods. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		public Map<T, HandlerMethod> getMappings() {
			return this.mappingLookup;
		}

		/**
		 * Return matches for the given URL path. Not thread-safe.
		 * @see #acquireReadLock()
		 */
		@Nullable
		public List<T> getMappingsByUrl(String urlPath) {
			return this.urlLookup.get(urlPath);
		}

		/**
		 * Return handler methods by mapping name. Thread-safe for concurrent use.
		 */
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}

		/**
		 * Return CORS configuration. Thread-safe for concurrent use.
		 */
		@Nullable
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}

		/**
		 * Acquire the read lock when using getMappings and getMappingsByUrl.
		 */
		public void acquireReadLock() {
			this.readWriteLock.readLock().lock();
		}

		/**
		 * Release the read lock after using getMappings and getMappingsByUrl.
		 */
		public void releaseReadLock() {
			this.readWriteLock.readLock().unlock();
		}

		public void register(T mapping, Object handler, Method method) {
			// 那就是注册handlerMethod，是交给AbstractHandlerMethodMapping的一个内部类MappingRegistry去完成的，用来专门维持所有的映射关系，并提供方法去查找方法去提供当前url映射的方法。
			// ❗️ 核心注册方法
			// mapping 映射结构,存储一些重要的信息
			// handler 目标Controller类
			// method 目标执行方法
			if (KotlinDetector.isKotlinType(method.getDeclaringClass())) { // 忽略
				Class<?>[] parameterTypes = method.getParameterTypes();
				if ((parameterTypes.length > 0) && "kotlin.coroutines.Continuation".equals(parameterTypes[parameterTypes.length - 1].getName())) {
					throw new IllegalStateException("Unsupported suspending handler method detected: " + method);
				}
			}
			this.readWriteLock.writeLock().lock();
			try {
				// HandlerMethod 是一个组合类，内部聚合丰富信息
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
				// 检查 RequestMappingInfo -> HandlerMethod 的映射是否重复，重复就报错 -- 不能够有相同的mapping,不同的handler
				validateMethodMapping(handlerMethod, mapping);
				// 存入 RequestMappingInfo -> HandlerMethod 的映射
				this.mappingLookup.put(mapping, handlerMethod);

				List<String> directUrls = getDirectUrls(mapping);
				// 存入 url -> RequestMappingInfo 的映射 -- 一个HandlerMethod，@RequestMapping的path属性可以映射多个地址，
				// 注意: directUrls 是从mapping校验信息中获取额精准url,如果是模糊url会返回null
				// 因此 urlLookup 提供了根据url做精准匹配的能力
				for (String url : directUrls) {
					this.urlLookup.add(url, mapping);
				}
				// name -> HandlerMethod 的映射
				// 保存name和handlerMethod的关系  同样也是一对多
				String name = null;
				if (getNamingStrategy() != null) {
					name = getNamingStrategy().getName(handlerMethod, mapping);
					addMappingName(name, handlerMethod);
				}

				// 跨域配置 -- 初始化
				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					// 完成 HandlerMethod -> CorsConfiguration
					this.corsLookup.put(handlerMethod, corsConfig);
				}

				// 注册mapping和MappingRegistration的关系
				this.registry.put(mapping, new MappingRegistration<>(mapping, handlerMethod, directUrls, name));
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
			// RequestMappingInfo 只能对应同一个HandlerMethod，否则就会报出异常
			// 因为 RequestMappingInfo 是 HandlerMethod 上校验合集,如果校验信息即mapping相同,但不是同一个HandlerMethod,那就是出现两仪性直接报错吧

			HandlerMethod existingHandlerMethod = this.mappingLookup.get(mapping);
			if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
				throw new IllegalStateException(
						"Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
						handlerMethod + "\nto " + mapping + ": There is already '" +
						existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
			}
		}

		private List<String> getDirectUrls(T mapping) {
			// 从校验信息mapping中提取出映射的url,如果url不是模式匹配,而是精准匹配
			// 那么就加入到urls
			// 这意味着 urlLookup 中使用的都是精准url
			// 一旦从urlLookup找不到,就只能被破做模糊匹配啦

			List<String> urls = new ArrayList<>(1);
			for (String path : getMappingPathPatterns(mapping)) {
				if (!getPathMatcher().isPattern(path)) {
					urls.add(path);
				}
			}
			return urls;
		}

		private void addMappingName(String name, HandlerMethod handlerMethod) {
			// 将HandlerMethod和RequestMappingInfo的name -> 映射到 HandlerMethod 上
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.emptyList();
			}

			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			newList.addAll(oldList);
			newList.add(handlerMethod);
			this.nameLookup.put(name, newList);
		}

		public void unregister(T mapping) {
			this.readWriteLock.writeLock().lock();
			try {
				MappingRegistration<T> definition = this.registry.remove(mapping);
				if (definition == null) {
					return;
				}

				this.mappingLookup.remove(definition.getMapping());

				for (String url : definition.getDirectUrls()) {
					List<T> list = this.urlLookup.get(url);
					if (list != null) {
						list.remove(definition.getMapping());
						if (list.isEmpty()) {
							this.urlLookup.remove(url);
						}
					}
				}

				removeMappingName(definition);

				this.corsLookup.remove(definition.getHandlerMethod());
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void removeMappingName(MappingRegistration<T> definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}


	private static class MappingRegistration<T> {

		private final T mapping;

		private final HandlerMethod handlerMethod;

		private final List<String> directUrls;

		@Nullable
		private final String mappingName;

		public MappingRegistration(T mapping, HandlerMethod handlerMethod,
				@Nullable List<String> directUrls, @Nullable String mappingName) {

			Assert.notNull(mapping, "Mapping must not be null");
			Assert.notNull(handlerMethod, "HandlerMethod must not be null");
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directUrls = (directUrls != null ? directUrls : Collections.emptyList());
			this.mappingName = mappingName;
		}

		public T getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public List<String> getDirectUrls() {
			return this.directUrls;
		}

		@Nullable
		public String getMappingName() {
			return this.mappingName;
		}
	}


	/**
	 * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
	 * comparing the best match with a comparator in the context of the current request.
	 */
	private class Match {
		// 匹配结果的封装
		// 聚合：RequestMappingInfo 与 HandlerMethod

		private final T mapping;

		private final HandlerMethod handlerMethod;

		public Match(T mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}


	private class MatchComparator implements Comparator<Match> {
		// 持有一个定制的 Match 比较器

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}


	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		static private boolean isSuspend(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return function != null && function.isSuspend();
		}
	}

}
