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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation to bind a method parameter to a session attribute.
 *
 * <p>The main motivation is to provide convenient access to existing, permanent
 * session attributes (e.g. user authentication object) with an optional/required
 * check and a cast to the target method parameter type.
 *
 * <p>For use cases that require adding or removing session attributes consider
 * injecting {@code org.springframework.web.context.request.WebRequest} or
 * {@code javax.servlet.http.HttpSession} into the controller method.
 *
 * <p>For temporary storage of model attributes in the session as part of the
 * workflow for a controller, consider using {@link SessionAttributes} instead.
 *
 * @author Rossen Stoyanchev
 * @since 4.3
 * @see RequestMapping
 * @see SessionAttributes
 * @see RequestAttribute
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SessionAttribute {
	// 该注解顾名思义，作用是将Model中的属性同步到session会话当中，方便在下一次请求中使用(比如重定向场景~)。
	// 虽然说Session的概念在当下前后端完全分离的场景中已经变得越来越弱化了，但是若为web开发者来说，我仍旧强烈不建议各位扔掉这个知识点，so我自然就建议大家能够熟练使用@SessionAttributes来简化平时的开发，本文带你入坑~

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the session attribute to bind to.
	 * <p>The default name is inferred from the method parameter name.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Whether the session attribute is required.
	 * <p>Defaults to {@code true}, leading to an exception being thrown
	 * if the attribute is missing in the session or there is no session.
	 * Switch this to {@code false} if you prefer a {@code null} or Java 8
	 * {@code java.util.Optional} if the attribute doesn't exist.
	 */
	boolean required() default true;

}
