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

package org.springframework.web.method.annotation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.context.request.WebRequest;

/**
 * Manages controller-specific session attributes declared via
 * {@link SessionAttributes @SessionAttributes}. Actual storage is
 * delegated to a {@link SessionAttributeStore} instance.
 *
 * <p>When a controller annotated with {@code @SessionAttributes} adds
 * attributes to its model, those attributes are checked against names and
 * types specified via {@code @SessionAttributes}. Matching model attributes
 * are saved in the HTTP session and remain there until the controller calls
 * {@link SessionStatus#setComplete()}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class SessionAttributesHandler {
	/*
	 * 会话属性处理器
	 * 管理通过@SessionAttributes声明的特定于控制器的会话属性
	 * 实际存储被委派给SessionAttributeStore实例。
	 *
	 * 作用：
	 * 1、如果想让参数在多个请求间共享，那么可以用到要说到的@SessionAttribute注解SessionAttribute只能作用在类上
	 * 2、在每个RequestMapping的方法执行后，这个SessionAttributesHandler都会将它自己管理的“属性”从Model中写入到真正的HttpSession；
	 * 3、同样，在每个RequestMapping的方法执行前，SessionAttributesHandler会将HttpSession中的被@SessionAttributes注解的属性写入到新的Model中。
	 *
	 * SessionAttributesHandler管理通过@SessionAttributes声明的特定于handler的会话属性。实际存储委托给SessionAttributeStore实例。
	 * 当使用@SessionAttributes注释的handler向其model添加attribute时，
	 * 这些属性会根据通过@SessionAttributes指定的名称和类型进行检查。匹配的模型属性保存在 HTTP 会话中并保持在那里，直到handler调用SessionStatus.setComplete() 。
	 *
	 * 这个类是对SessionAttribute这些属性的核心处理能力：包括了所谓的增删改查。因为要进一步理解到它的原理，所以要说到它的处理入口，那就要来到ModelFactory了~
	 */

	// 构造中完成初始化 - @SessionAttributes的names属性名 + 通过调用HttpSession.setAttribute()设置的
	private final Set<String> attributeNames = new HashSet<>();

	// 构造中完成初始化 - @SessionAttributes的types属性值类型
	private final Set<Class<?>> attributeTypes = new HashSet<>();

	// 注意这个重要性：它是注解方式放入session和API方式放入session的关键（它只会记录注解方式放进去的session属性~~）
	private final Set<String> knownAttributeNames = Collections.newSetFromMap(new ConcurrentHashMap<>(4));

	// sessionAttr存储器：它最终存储到的是WebRequest的session域里面去（对httpSession是进行了包装的）
	// 因为有WebRequest的处理，所以达到我们上面看到的效果。complete只会清除注解放进去的，并不清除API放进去的~~~
	// 它的唯一实现类DefaultSessionAttributeStore实现也简单。（特点：能够制定特殊的前缀，这个有时候还是有用的）
	// 前缀attributeNamePrefix在构造器里传入进来  默认是“”
	private final SessionAttributeStore sessionAttributeStore; // 构造中完成初始化 - 属性存取 -- 用于存、检索、移除指定的session属性


	/**
	 * Create a new session attributes handler. Session attribute names and types
	 * are extracted from the {@code @SessionAttributes} annotation, if present,
	 * on the given type.
	 * @param handlerType the controller type
	 * @param sessionAttributeStore used for session access
	 */
	public SessionAttributesHandler(Class<?> handlerType, SessionAttributeStore sessionAttributeStore) {
		// 唯一的构造器
		// handlerType：控制器类型
		// SessionAttributeStore 是由调用者上层传进来的
		// 调用处 -- RequestMappingHandlerAdapter#handleInternal() -> invokeHandlerMethod() -> getModelFactory() -> getSessionAttributesHandler()
		// new SessionAttributesHandler(type, this.sessionAttributeStore)
		// 没有判断type是否有@ModelAttributes注解,就直接new一个SessionAttributesHandler
		// new 出来的 SessionAttributesHandler 会融入到 ModelFactory 的构建中
		// 并在 ModelFactory 中的intiModel() 中被使用到

		Assert.notNull(sessionAttributeStore, "SessionAttributeStore may not be null");

		// 1. 已有的会话属性存取实例
		this.sessionAttributeStore = sessionAttributeStore;
		// 2. 查看当前Controller控制器上是否有@SessionAttributes注解 -- 注意：@SessionAttributes只能注解在类上哦,不同于@SessionAttribute
		// 如果没有@SessionAttributes注解,那么attributeNames\attributeTypes就是空的哦
		SessionAttributes ann = AnnotatedElementUtils.findMergedAnnotation(handlerType, SessionAttributes.class);
		if (ann != null) {
			Collections.addAll(this.attributeNames, ann.names()); // 添加属性名 -- 即 SessionAttributes#names
			Collections.addAll(this.attributeTypes, ann.types()); // 添加属性值类型 -- 即 SessionAttributes#types
		}
		// 3. 添加到已知的属性命名中 -- knownAttributeNames 仅仅用于存储@ModelAttributes注解的names
		this.knownAttributeNames.addAll(this.attributeNames);
	}


	/**
	 * Whether the controller represented by this instance has declared any
	 * session attributes through an {@link SessionAttributes} annotation.
	 */
	public boolean hasSessionAttributes() {
		// 检查是否拥有会话属性

		return (!this.attributeNames.isEmpty() || !this.attributeTypes.isEmpty());
	}

	/**
	 * Whether the attribute name or type match the names and types specified
	 * via {@code @SessionAttributes} on the underlying controller.
	 * <p>Attributes successfully resolved through this method are "remembered"
	 * and subsequently used in {@link #retrieveAttributes(WebRequest)} and
	 * {@link #cleanupAttributes(WebRequest)}.
	 * @param attributeName the attribute name to check
	 * @param attributeType the type for the attribute
	 */
	public boolean isHandlerSessionAttribute(String attributeName, Class<?> attributeType) {
		// 检查是否为SessionAttribute

		Assert.notNull(attributeName, "Attribute name must not be null");
		if (this.attributeNames.contains(attributeName) || this.attributeTypes.contains(attributeType)) {
			this.knownAttributeNames.add(attributeName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Store a subset of the given attributes in the session. Attributes not
	 * declared as session attributes via {@code @SessionAttributes} are ignored.
	 * @param request the current request
	 * @param attributes candidate attributes for session storage
	 */
	public void storeAttributes(WebRequest request, Map<String, ?> attributes) {
		// 在会话session中存储给定属性attributes的子集。
		// 未通过@SessionAttributes声明为会话属性的属性将被忽略。

		attributes.forEach((name, value) -> {
			if (value != null && isHandlerSessionAttribute(name, value.getClass())) {
				this.sessionAttributeStore.storeAttribute(request, name, value);
			}
		});
	}

	/**
	 * Retrieve "known" attributes from the session, i.e. attributes listed
	 * by name in {@code @SessionAttributes} or attributes previously stored
	 * in the model that matched by type.
	 * @param request the current request
	 * @return a map with handler session attributes, possibly empty
	 */
	public Map<String, Object> retrieveAttributes(WebRequest request) {
		// 从已知的属性名那种检索
		// 检索所有的属性们  用的是knownAttributeNames哦~~~~
		// 也就是说手动API放进Session的 此处不会被检索出来的
		// 因此只有这个reuqest对应处理的Handler上有@ModelAttributes才会生效

		Map<String, Object> attributes = new HashMap<>();
		for (String name : this.knownAttributeNames) {
			Object value = this.sessionAttributeStore.retrieveAttribute(request, name); // 利用 sessionAttributeStore 从当前request的属性中检索指定的会话属性
			if (value != null) {
				attributes.put(name, value);
			}
		}
		return attributes;
	}

	/**
	 * Remove "known" attributes from the session, i.e. attributes listed
	 * by name in {@code @SessionAttributes} or attributes previously stored
	 * in the model that matched by type.
	 * @param request the current request
	 */
	public void cleanupAttributes(WebRequest request) {
		// 从会话中删除“已知”属性，即在@SessionAttributes中按名称列出的属性或先前存储在模型中的按类型匹配的属性。
		// 不会删除调用HttpSession.setAttribute()方法存入的会话属性

		for (String attributeName : this.knownAttributeNames) {
			this.sessionAttributeStore.cleanupAttribute(request, attributeName);
		}
	}

	/**
	 * A pass-through call to the underlying {@link SessionAttributeStore}.
	 * @param request the current request
	 * @param attributeName the name of the attribute of interest
	 * @return the attribute value, or {@code null} if none
	 */
	@Nullable
	Object retrieveAttribute(WebRequest request, String attributeName) {
		return this.sessionAttributeStore.retrieveAttribute(request, attributeName);
	}

}
