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

package org.springframework.beans;

import java.beans.PropertyEditor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Internal helper class for converting property values to target types.
 *
 * <p>Works on a given {@link PropertyEditorRegistrySupport} instance.
 * Used as a delegate by {@link BeanWrapperImpl} and {@link SimpleTypeConverter}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @since 2.0
 * @see BeanWrapperImpl
 * @see SimpleTypeConverter
 */
class TypeConverterDelegate {

	private static final Log logger = LogFactory.getLog(TypeConverterDelegate.class);

	// 注意:持有PropertyEditorRegistrySupport即属性编辑器中心,而propertyEditorRegistry中有一个converService
	private final PropertyEditorRegistrySupport propertyEditorRegistry;

	@Nullable
	private final Object targetObject;


	/**
	 * Create a new TypeConverterDelegate for the given editor registry.
	 * @param propertyEditorRegistry the editor registry to use
	 */
	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry) {
		this(propertyEditorRegistry, null);
	}

	/**
	 * Create a new TypeConverterDelegate for the given editor registry and bean instance.
	 * @param propertyEditorRegistry the editor registry to use
	 * @param targetObject the target object to work on (as context that can be passed to editors)
	 */
	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry, @Nullable Object targetObject) {
		// 注册中心、目标类型
		this.propertyEditorRegistry = propertyEditorRegistry;
		this.targetObject = targetObject;
	}


	/**
	 * Convert the value to the required type for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
			Object newValue, @Nullable Class<T> requiredType) throws IllegalArgumentException {

		return convertIfNecessary(propertyName, oldValue, newValue, requiredType, TypeDescriptor.valueOf(requiredType));
	}

	/**
	 * Convert the value to the required type (if necessary from a String),
	 * for the specified property.
	 * @param propertyName name of the property
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @param typeDescriptor the descriptor for the target property or field
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws IllegalArgumentException {

		// 1、通过属性名字propertyName和目标类型requiredType 查找符合的属性编辑器PropertyEditor
		PropertyEditor editor = this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName);

		// 2. 存储失败的原因和异常
		ConversionFailedException conversionAttemptEx = null;

		// No custom editor but custom ConversionService specified?
		// 2、未找到合适的属性编辑器，但一般会指定自定义转换服务ConversionService，还是可以做尝试转换
		// 从 this.propertyEditorRegistry.getConversionService() 获取 ConversionService
		ConversionService conversionService = this.propertyEditorRegistry.getConversionService();
		if (editor == null && conversionService != null && newValue != null && typeDescriptor != null) {
			// 3、获取用户传入的propertyValue的value的类型属性 -- 例如 new PropertyValue("name","1");
			// 那么 sourceTypeDesc 就是特指String类型的
			TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
			// 4、是否可以从 sourceTypeDesc类型 转为指定的 typeDescriptor类型
			if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
				try {
					// 5、如果转换器支持这种转换，就开始转换，将 newValue 转换为目标类型 typeDescriptor
					return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
				}
				catch (ConversionFailedException ex) {
					// fallback to default conversion logic below
					conversionAttemptEx = ex;
				}
			}
		}

		Object convertedValue = newValue;

		// 6. editor 实际上不为null , 或者 requiredType不为空,且convertedValue并不是requiredType类型的
		// 值是否为不需要转换的类型？ -> 比如目标类型是String，用户提供的propertyValue的value也是String类型的
		if (editor != null || (requiredType != null && !ClassUtils.isAssignableValue(requiredType, convertedValue))) {
			// 6.1 对于目标类型是集合或者数组,需要处理 convertedValue
			if (typeDescriptor != null && requiredType != null && Collection.class.isAssignableFrom(requiredType) && convertedValue instanceof String) {
				TypeDescriptor elementTypeDesc = typeDescriptor.getElementTypeDescriptor(); // 获取元素的类型描述器
				// 6.1 elementTypeDesc 不为空,说明typeDescriptor目标类就是集合或数组类型的
				if (elementTypeDesc != null) {
					// 6.1.1 获取实际的元素类型
					Class<?> elementType = elementTypeDesc.getType(); // 获取元素的实际类型
					// 6.2.2 元素类型是Class或者,元素类型是Enum类型的
					// 都需要对convertedValue做一下切割
					if (Class.class == elementType || Enum.class.isAssignableFrom(elementType)) {
						// 将逗号分隔的列表（例如，CSV 文件中的一行）转换为字符串数组。
						// 改变 convertedValue 值
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
				}
			}
			// 6.2 实际查找不到编辑器 [说明前面this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName)查找定制的属性编辑器失败]
			// 且 conversionService 也无法处理,那就尝试使用默认的属性编辑器
			// 调用 findDefaultEditor(requiredType)
			if (editor == null) {
				// 6.3 尝试查找默认的编辑器
				editor = findDefaultEditor(requiredType);
			}
			// 6.4 尝试转换：用户给定值为convertedValue、目标类型requiredType、使用编辑器为editor
			// convertedValue 经过属性编辑器转换后的值
			convertedValue = doConvertValue(oldValue, convertedValue, requiredType, editor);
		}

		boolean standardConversion = false; // 是否完成标准转换

		// 7. 目标类型不是null
		if (requiredType != null) {
			// 7.1 如果合适，尝试应用一些标准类型转换规则 -- 帮助将结果强制转换
			if (convertedValue != null) {
				// 7.1.1 Object
				if (Object.class == requiredType) {
					return (T) convertedValue;
				}
				// 7.1.2 数组类型
				else if (requiredType.isArray()) {
					// 需要数组->应用适当的元素转换。
					if (convertedValue instanceof String && Enum.class.isAssignableFrom(requiredType.getComponentType())) {
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
					return (T) convertToTypedArray(convertedValue, propertyName, requiredType.getComponentType());
				}
				// 7.1.3 集合类型
				else if (convertedValue instanceof Collection) {
					// 将元素转换为目标类型（如果确定）.
					convertedValue = convertToTypedCollection(
							(Collection<?>) convertedValue, propertyName, requiredType, typeDescriptor);
					standardConversion = true;
				}
				// 7.1.4 Map结构
				else if (convertedValue instanceof Map) {
					// Convert keys and values to respective target type, if determined.
					convertedValue = convertToTypedMap(
							(Map<?, ?>) convertedValue, propertyName, requiredType, typeDescriptor);
					standardConversion = true;
				}
				// 7.1.5 为数组且只有一个元素
				if (convertedValue.getClass().isArray() && Array.getLength(convertedValue) == 1) {
					convertedValue = Array.get(convertedValue, 0);
					standardConversion = true;
				}
				// 7.1.6 对任何基本类型进行转换
				if (String.class == requiredType && ClassUtils.isPrimitiveOrWrapper(convertedValue.getClass())) {
					// We can stringify any primitive value...
					return (T) convertedValue.toString();
				}
				// 7.1.7 转换后的结果为String类型 且 转换结果不是目标类型的实现
				else if (convertedValue instanceof String && !requiredType.isInstance(convertedValue)) {
					if (conversionAttemptEx == null && !requiredType.isInterface() && !requiredType.isEnum()) {
						try {
							Constructor<T> strCtor = requiredType.getConstructor(String.class);
							return BeanUtils.instantiateClass(strCtor, convertedValue);
						}
						catch (NoSuchMethodException ex) {
							// proceed with field lookup
							if (logger.isTraceEnabled()) {
								logger.trace("No String constructor found on type [" + requiredType.getName() + "]", ex);
							}
						}
						catch (Exception ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Construction via String failed for type [" + requiredType.getName() + "]", ex);
							}
						}
					}
					String trimmedValue = ((String) convertedValue).trim();
					if (requiredType.isEnum() && trimmedValue.isEmpty()) {
						// It's an empty enum identifier: reset the enum value to null.
						return null;
					}
					convertedValue = attemptToConvertStringToEnum(requiredType, trimmedValue, convertedValue);
					standardConversion = true;
				}
				// 转换结果是Number类型的，就调用NumberUtils做数字转换
				else if (convertedValue instanceof Number && Number.class.isAssignableFrom(requiredType)) {
					convertedValue = NumberUtils.convertNumberToTargetClass(
							(Number) convertedValue, (Class<Number>) requiredType);
					standardConversion = true;
				}
			}
			else {
				// convertedValue == null
				if (requiredType == Optional.class) {
					convertedValue = Optional.empty();
				}
			}

			// 转换结果convertedValue不是requiredType的实例，就进入if
			if (!ClassUtils.isAssignableValue(requiredType, convertedValue)) {
				if (conversionAttemptEx != null) {
					// Original exception from former ConversionService call above...
					throw conversionAttemptEx;
				}
				else if (conversionService != null && typeDescriptor != null) {
					// ConversionService not tried before, probably custom editor found
					// but editor couldn't produce the required type...
					TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
					if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
						return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
					}
				}

				// Definitely doesn't match: throw IllegalArgumentException/IllegalStateException
				StringBuilder msg = new StringBuilder();
				msg.append("Cannot convert value of type '").append(ClassUtils.getDescriptiveType(newValue));
				msg.append("' to required type '").append(ClassUtils.getQualifiedName(requiredType)).append("'");
				if (propertyName != null) {
					msg.append(" for property '").append(propertyName).append("'");
				}
				if (editor != null) {
					msg.append(": PropertyEditor [").append(editor.getClass().getName()).append(
							"] returned inappropriate value of type '").append(
							ClassUtils.getDescriptiveType(convertedValue)).append("'");
					throw new IllegalArgumentException(msg.toString());
				}
				else {
					msg.append(": no matching editors or conversion strategy found");
					throw new IllegalStateException(msg.toString());
				}
			}
		}

		// 是否有转换异常
		if (conversionAttemptEx != null) {
			if (editor == null && !standardConversion && requiredType != null && Object.class != requiredType) {
				throw conversionAttemptEx;
			}
			logger.debug("Original ConversionService attempt failed - ignored since " +
					"PropertyEditor based conversion eventually succeeded", conversionAttemptEx);
		}

		// 返回转换值
		return (T) convertedValue;
	}

	private Object attemptToConvertStringToEnum(Class<?> requiredType, String trimmedValue, Object currentConvertedValue) {
		Object convertedValue = currentConvertedValue;

		if (Enum.class == requiredType && this.targetObject != null) {
			// target type is declared as raw enum, treat the trimmed value as <enum.fqn>.FIELD_NAME
			int index = trimmedValue.lastIndexOf('.');
			if (index > - 1) {
				String enumType = trimmedValue.substring(0, index);
				String fieldName = trimmedValue.substring(index + 1);
				ClassLoader cl = this.targetObject.getClass().getClassLoader();
				try {
					Class<?> enumValueType = ClassUtils.forName(enumType, cl);
					Field enumField = enumValueType.getField(fieldName);
					convertedValue = enumField.get(null);
				}
				catch (ClassNotFoundException ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Enum class [" + enumType + "] cannot be loaded", ex);
					}
				}
				catch (Throwable ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Field [" + fieldName + "] isn't an enum value for type [" + enumType + "]", ex);
					}
				}
			}
		}

		if (convertedValue == currentConvertedValue) {
			// Try field lookup as fallback: for JDK 1.5 enum or custom enum
			// with values defined as static fields. Resulting value still needs
			// to be checked, hence we don't return it right away.
			try {
				Field enumField = requiredType.getField(trimmedValue);
				ReflectionUtils.makeAccessible(enumField);
				convertedValue = enumField.get(null);
			}
			catch (Throwable ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Field [" + convertedValue + "] isn't an enum value", ex);
				}
			}
		}

		return convertedValue;
	}
	/**
	 * Find a default editor for the given type.
	 * @param requiredType the type to find an editor for
	 * @return the corresponding editor, or {@code null} if none
	 */
	@Nullable
	private PropertyEditor findDefaultEditor(@Nullable Class<?> requiredType) {
		PropertyEditor editor = null;
		if (requiredType != null) {
			// 没有找到定制的 editor -> 转换到检查默认编辑器
			editor = this.propertyEditorRegistry.getDefaultEditor(requiredType);
			if (editor == null && String.class != requiredType) {
				// 还是没有找到 BeanWrapper 的默认编辑器 -> check standard JavaBean editor.检查标准的JavaBean编辑器
				// 例如 目标class是DateTime,就会去用 DateTime + Editor 作为一个ClassName,然后用ClassLoader去加载
				// 有的话就加载成功
				editor = BeanUtils.findEditorByConvention(requiredType);
			}
		}
		return editor;
	}

	/**
	 * Convert the value to the required type (if necessary from a String),
	 * using the given property editor.
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newValue the proposed new value
	 * @param requiredType the type we must convert to
	 * (or {@code null} if not known, for example in case of a collection element)
	 * @param editor the PropertyEditor to use
	 * @return the new value, possibly the result of type conversion
	 * @throws IllegalArgumentException if type conversion failed
	 */
	@Nullable
	private Object doConvertValue(@Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<?> requiredType, @Nullable PropertyEditor editor) {
		// 使用给定的属性编辑器将值转换为所需的类型（如果需要从字符串）。
		//		参数：
		//		oldValue – 前一个值，如果可用（可能为null ）
		//		newValue – 提议的新值
		//		requiredType – 我们必须转换为的类型（如果未知，则为null ，例如在集合元素的情况下）
		//		editor – 要使用的 PropertyEditor

		Object convertedValue = newValue;

		// 1. 编辑器不为空，且给定的值不是String
		if (editor != null && !(convertedValue instanceof String)) {
			// 1.1 不是字符串的情况->使用PropertyEditor的设置值。对于标准或者内置的PropertyEditor，这将返回完全相同的对象；
			// 但是，我们只想允许特殊属性编辑器覆盖setValue。用于将非字符串值转换为所需类型。
			try {
				// 1.2 调用属性编辑器设置值
				editor.setValue(convertedValue);
				// 1.2 对于标准属性编辑器获取结果而言，editor.getValue()的结果newConvertedValue和前面setValue(convertedValue)的convertedValue是一样的
				// 只有一些用户定义的特殊属性编辑器,可能不会遵守 setValue() 和 getValue() 不是同一个值
				Object newConvertedValue = editor.getValue();
				if (newConvertedValue != convertedValue) {
					// 1.3 特殊的属性编辑器,更新最新的转换值为convertedValue = newConvertedValue
					convertedValue = newConvertedValue;
					// 1.4 重置属性编辑器：它已经进行了正确的转换。不要在setAsText调用中再次使用它。
					editor = null;
				}
			}
			catch (Exception ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
				}
				// Swallow and proceed.
			}
		}

		Object returnValue = convertedValue;

		// 2. requiredType目标类型不是数组,但是转换值convertedValue是String数组 -- 需要处理一下convertedValue
		// 主要是使用editor之前修正convertedValue
		if (requiredType != null && !requiredType.isArray() && convertedValue instanceof String[]) {
			if (logger.isTraceEnabled()) {
				logger.trace("Converting String array to comma-delimited String [" + convertedValue + "]");
			}
			// 2.1 对convertedValue做一个处理,合并为一个是用,合并的字符串后返回吧
			convertedValue = StringUtils.arrayToCommaDelimitedString((String[]) convertedValue);
		}

		// 3. convertedValue 是 String 类型,若editor不为空,就可以尝试转换啦
		if (convertedValue instanceof String) {
			// 用户给定值属于字符串类型
			if (editor != null) {
				// 如果是字符串值，请使用PropertyEdit的setAsText。
				if (logger.isTraceEnabled()) {
					logger.trace("Converting String to [" + requiredType + "] using property editor [" + editor + "]");
				}
				String newTextValue = (String) convertedValue;
				// 3.1 属性编辑器适合String转其他类型，因此标准属性编辑器是可以的
				// oldValue 前一个值,newTextValue 下一次推荐返回的值
				return doConvertTextValue(oldValue, newTextValue, editor);
			}
			else if (String.class == requiredType) {
				// 3.2 原来目标值也是String类型,不需要转换啦
				returnValue = convertedValue;
			}
		}

		return returnValue;
	}

	/**
	 * Convert the given text value using the given property editor.
	 * @param oldValue the previous value, if available (may be {@code null})
	 * @param newTextValue the proposed text value
	 * @param editor the PropertyEditor to use
	 * @return the converted value
	 */
	private Object doConvertTextValue(@Nullable Object oldValue, String newTextValue, PropertyEditor editor) {
		// 使用给定的属性编辑器转换给定的文本值。

		try {
			// 1. 首先设置oldValue
			editor.setValue(oldValue);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
			}
			// Swallow and proceed.
		}
		// 2. 将newTextValue通过属性编辑器转换为给属性编辑器的的类型 -- 底层会 将newTextValue转换特定类型后,调用setValue存进去
		editor.setAsText(newTextValue);
		// 3. 获取编辑器转换后的值
		return editor.getValue();
	}

	private Object convertToTypedArray(Object input, @Nullable String propertyName, Class<?> componentType) {
		if (input instanceof Collection) {
			// Convert Collection elements to array elements.
			Collection<?> coll = (Collection<?>) input;
			Object result = Array.newInstance(componentType, coll.size());
			int i = 0;
			for (Iterator<?> it = coll.iterator(); it.hasNext(); i++) {
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, it.next(), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else if (input.getClass().isArray()) {
			// Convert array elements, if necessary.
			if (componentType.equals(input.getClass().getComponentType()) &&
					!this.propertyEditorRegistry.hasCustomEditorForElement(componentType, propertyName)) {
				return input;
			}
			int arrayLength = Array.getLength(input);
			Object result = Array.newInstance(componentType, arrayLength);
			for (int i = 0; i < arrayLength; i++) {
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, Array.get(input, i), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else {
			// A plain value: convert it to an array with a single component.
			Object result = Array.newInstance(componentType, 1);
			Object value = convertIfNecessary(
					buildIndexedPropertyName(propertyName, 0), null, input, componentType);
			Array.set(result, 0, value);
			return result;
		}
	}

	@SuppressWarnings("unchecked")
	private Collection<?> convertToTypedCollection(Collection<?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		if (!Collection.class.isAssignableFrom(requiredType)) {
			return original;
		}

		boolean approximable = CollectionFactory.isApproximableCollectionType(requiredType);
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Collection type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Collection as-is");
			}
			return original;
		}

		boolean originalAllowed = requiredType.isInstance(original);
		TypeDescriptor elementType = (typeDescriptor != null ? typeDescriptor.getElementTypeDescriptor() : null);
		if (elementType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			return original;
		}

		Iterator<?> it;
		try {
			it = original.iterator();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Collection of type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		Collection<Object> convertedCopy;
		try {
			if (approximable) {
				convertedCopy = CollectionFactory.createApproximateCollection(original, original.size());
			}
			else {
				convertedCopy = (Collection<Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Collection type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		for (int i = 0; it.hasNext(); i++) {
			Object element = it.next();
			String indexedPropertyName = buildIndexedPropertyName(propertyName, i);
			Object convertedElement = convertIfNecessary(indexedPropertyName, null, element,
					(elementType != null ? elementType.getType() : null) , elementType);
			try {
				convertedCopy.add(convertedElement);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Collection type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Collection as-is: " + ex);
				}
				return original;
			}
			originalAllowed = originalAllowed && (element == convertedElement);
		}
		return (originalAllowed ? original : convertedCopy);
	}

	@SuppressWarnings("unchecked")
	private Map<?, ?> convertToTypedMap(Map<?, ?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		if (!Map.class.isAssignableFrom(requiredType)) {
			return original;
		}

		boolean approximable = CollectionFactory.isApproximableMapType(requiredType);
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Map type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Map as-is");
			}
			return original;
		}

		boolean originalAllowed = requiredType.isInstance(original);
		TypeDescriptor keyType = (typeDescriptor != null ? typeDescriptor.getMapKeyTypeDescriptor() : null);
		TypeDescriptor valueType = (typeDescriptor != null ? typeDescriptor.getMapValueTypeDescriptor() : null);
		if (keyType == null && valueType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			return original;
		}

		Iterator<?> it;
		try {
			it = original.entrySet().iterator();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Map of type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		Map<Object, Object> convertedCopy;
		try {
			if (approximable) {
				convertedCopy = CollectionFactory.createApproximateMap(original, original.size());
			}
			else {
				convertedCopy = (Map<Object, Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Map type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		while (it.hasNext()) {
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
			Object key = entry.getKey();
			Object value = entry.getValue();
			String keyedPropertyName = buildKeyedPropertyName(propertyName, key);
			Object convertedKey = convertIfNecessary(keyedPropertyName, null, key,
					(keyType != null ? keyType.getType() : null), keyType);
			Object convertedValue = convertIfNecessary(keyedPropertyName, null, value,
					(valueType!= null ? valueType.getType() : null), valueType);
			try {
				convertedCopy.put(convertedKey, convertedValue);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Map type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Map as-is: " + ex);
				}
				return original;
			}
			originalAllowed = originalAllowed && (key == convertedKey) && (value == convertedValue);
		}
		return (originalAllowed ? original : convertedCopy);
	}

	@Nullable
	private String buildIndexedPropertyName(@Nullable String propertyName, int index) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + index + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	@Nullable
	private String buildKeyedPropertyName(@Nullable String propertyName, Object key) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + key + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	private boolean canCreateCopy(Class<?> requiredType) {
		return (!requiredType.isInterface() && !Modifier.isAbstract(requiredType.getModifiers()) &&
				Modifier.isPublic(requiredType.getModifiers()) && ClassUtils.hasConstructor(requiredType));
	}

}
