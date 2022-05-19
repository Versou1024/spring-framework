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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {
	/*
	 * 在Controller方法调用之前协助初始化模型，并在调用之后进行更新。 -- 关键[初始化Model的数据，以及更新Model的数据]
	 * 初始化时，调用通过调用@ModelAttribute方法[即成员变量modelMethods]，使用临时存储在session中的属性填充model。
	 * 更新时model attribute 与 session 同步，如果缺少BindingResult属性，也会添加这些属性。
	 *
	 * 聚合：@ModeAttribute方法聚合、数据绑定工厂dataBinderFactory、会话属性处理器sessionAttributesHandler
	 */

	private final List<ModelMethod> modelMethods = new ArrayList<>(); // 标注有@ModelAttribute的方法

	private final WebDataBinderFactory dataBinderFactory; // 数据绑定工厂

	private final SessionAttributesHandler sessionAttributesHandler; // 会话属性处理器


	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {
		//  modelMethods 的唯一赋值地方就是构建ModelFactory时传参进来

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod)); // InvocableHandlerMethod -> ModelMethod
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * Populate the model in the following order:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * <li>Invoke {@code @ModelAttribute} methods
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {
		// 按以下顺序填充模型：
		// 		检索列为@SessionAttributes的“已知”会话属性。
		// 		调用@ModelAttribute方法
		// 		查找也列为@SessionAttributes的@ModelAttribute方法参数，并确保它们存在于模型中，并在必要时引发异常


		// 1. 利用SessionAttributesHandler从request检索属于当前request的会话属性 -- 就是检索当前request对应的handler上的@SessionAttributes中的name和value
		// 注意: 先拿到sessionAttr里所有的属性们（首次进来肯定木有，但同一个session第二次进来就有了）
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		// 2. 将从request检索出来的session属性，注入到mavContainer中 -- 完成session属性到model，在执行完后，session name不变，但属性值可能改变，因此需要又更新会model总共
		// 注意：sessionAttributes中只有当前model不存在的属性，它才会放进去 -- 不会造成覆盖
		container.mergeAttributes(sessionAttributes);
		// 3. 执行标注有@ModelAttribute的Method -- 向 mavContainer 中注入ModelAttributeMethod的属性
		// 第一次进来时@SessionAttributes的handler实际上获取的sessionAttributes只有name没有value,需要这里的invokeModelAttributeMethods()
		// 用@ModelAttribute更新Model后,@ModelAttribute对应的name就会有value值,并在退出前将这个value值存入会话,下一次进来sessionAttributes的value就有值啦
		invokeModelAttributeMethods(request, container);

		// 4. 遍历HandlerMethod即Controller要执行的请求方法的请求参数中，是否携带@ModelAttribute或@SessionAttribute注解的形参名列表
		// 最后，最后，最后还做了这么一步操作~~~
		// findSessionAttributeArguments的作用：把@ModelAttribute的入参也列入SessionAttributes（非常重要） 详细见下文
		// 这里一定要掌握：因为使用中的坑坑经常是因为没有理解到这块逻辑
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			// 因为形参上的这种是需要注入的，因此必须检查mavContainer中是否存在指定name的属性
			if (!container.containsAttribute(name)) {
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value); // 注入到model的属性中中
			}
		}
	}

	/**
	 * Invoke model attribute methods to populate the model.
	 * Attributes are added only if not already present in the model.
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {
		// 调用标注有注解@ModelAttribute的方法来填充Model
		// modelMethods 是被构造器传递进来的哦

		// 1. 遍历modelMethods : 其中封装都是方法上持有@ModelAttribute的方法
		while (!this.modelMethods.isEmpty()) {
			// 1.1 转换为可执行的 HandlerMethod
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			// 1.2 获取方法级别上的注解 @ModelAttribute
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			// 1.3 检查container是否包含方法级别的@ModelAttribute注解信息
			Assert.state(ann != null, "No ModelAttribute annotation");
			if (container.containsAttribute(ann.name())) {
				// 1.4 如果包含,但注解的binding为false,表示不需要数据绑定,因此需要将其设置为bindingDisable
				if (!ann.binding()) {
					container.setBindingDisabled(ann.name());
				}
				// 1.5 因为已经包含了,不需要再次执行,直接continue
				continue;
			}

			// 2. 执行 modelMethod,拿到返回值
			Object returnValue = modelMethod.invokeForRequest(request, container);
			if (!modelMethod.isVoid()){
				// 2.1 方法返回值非Void
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				// 2.2 是否禁用数据绑定
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				// 2.3 是否包含返回值returnValueName整个属性,不包含就需要添加进去
				// 在个判断是个小细节：只有容器内不存在此属性，才会放进去   因此并不会有覆盖的效果哦~~~
				// 所以若出现同名的  请自己控制好顺序吧
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	// 拿到下一个标注有此注解方法~~
	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		// 1. 每次都会遍历所有的构造进来的modelMethods
		for (ModelMethod modelMethod : this.modelMethods) {
			// 1.1 dependencies：表示该方法的所有入参中 标注有@ModelAttribute的入参们
			// checkDependencies的作用是：所有的dependencies依赖们必须都是container已经存在的属性，才会进到这里来
			if (modelMethod.checkDependencies(container)) {
				// 1.2 找到一个 就移除一个
				// 这里使用的是List的remove方法，不用担心并发修改异常？？？ 哈哈其实不用担心的  小伙伴能知道为什么吗？？
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		// 2. 若并不是所有的依赖属性Model里都有，那就拿第一个吧~~~~
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		// 1. 查找方法上关于  @ModelAttribute 上注入的 @SessionAttributes

		List<String> result = new ArrayList<>();
		// 2. 遍历形参列表
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			// 3. 检查形参上是否存在指定注解@ModelAttribute
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				// 4. 检查形参上注解@ModelAttribute的属性name+形参type是否和hanler上的@SessionAttributes是相同的
				// 如果是相同的,需要就返回这个name
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * Add {@link BindingResult} attributes where necessary.
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		// 将列为@SessionAttributes的模型数据，提升到sessionAttr里

		ModelMap defaultModel = container.getDefaultModel();
		if (container.getSessionStatus().isComplete()){
			// 1. 已标记为完成,即cleanup
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			// 2. 没有标记session完成,就存储到sessionAttr里
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		// 若该request还没有被处理  并且 Model就是默认defaultModel
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		// 将bindingResult属性添加到需要该属性的模型中。
		// isBindingCandidate：给定属性在Model模型中是否需要bindingResult。

		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		// @ModelAttribute指定了value值就以它为准，否则就是类名的首字母小写（当然不同类型不一样，下面有给范例）

		// 1. 注解有value()值就是用value值,没有value就是用形参名
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * Derive the model attribute name for the given return value. Results will be
	 * based on:
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * <li>the declared return type if it is more specific than {@code Object}
	 * <li>the actual return value type
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		// 关于方法这块的处理逻辑，和上差不多，主要是返回类型和实际类型的区分
		// 比如List<String>它对应的名是：stringList。即使你的返回类型是Object~~~

		// 派生给定返回值的模型属性名称。结果将基于：
		//		方法ModelAttribute注释值
		//		声明的返回类型（如果它比Object更具体）
		//		实际返回值类型

		// 1. 获取返回类型returnType的@ModelAttribute注解
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		// 2. 如果@ModelAttribute有value值,直接返回
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			// 3. 获取对应的方法
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			// 确定给定方法的返回类型的常规变量名称，考虑通用集合类型（如果有），如果方法声明不够具体，则返回给定返回值，例如Object返回类型或无类型集合。
			// 从 5.0 开始，此方法支持响应式类型：
			// Mono<com.myapp.Product>变为"productMono"
			// Flux<com.myapp.MyProduct>变为"myProductFlux"
			// Observable<com.myapp.MyProduct>变为"myProductObservable"
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			// dependencies依赖: 是指HandlerMethod中标有@ModelAttribute的形参
			// HandlerMethod被执行时,依赖上述形参必须存在与mavContainer中
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			// 检查依赖:

			// 就是方法中的@ModelAttribute标注的形参对应的属性,必须提前在macContainer中存在
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
