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

package org.springframework.beans;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

import org.xml.sax.InputSource;

import org.springframework.beans.propertyeditors.ByteArrayPropertyEditor;
import org.springframework.beans.propertyeditors.CharArrayPropertyEditor;
import org.springframework.beans.propertyeditors.CharacterEditor;
import org.springframework.beans.propertyeditors.CharsetEditor;
import org.springframework.beans.propertyeditors.ClassArrayEditor;
import org.springframework.beans.propertyeditors.ClassEditor;
import org.springframework.beans.propertyeditors.CurrencyEditor;
import org.springframework.beans.propertyeditors.CustomBooleanEditor;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;
import org.springframework.beans.propertyeditors.CustomMapEditor;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.beans.propertyeditors.FileEditor;
import org.springframework.beans.propertyeditors.InputSourceEditor;
import org.springframework.beans.propertyeditors.InputStreamEditor;
import org.springframework.beans.propertyeditors.LocaleEditor;
import org.springframework.beans.propertyeditors.PathEditor;
import org.springframework.beans.propertyeditors.PatternEditor;
import org.springframework.beans.propertyeditors.PropertiesEditor;
import org.springframework.beans.propertyeditors.ReaderEditor;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.beans.propertyeditors.TimeZoneEditor;
import org.springframework.beans.propertyeditors.URIEditor;
import org.springframework.beans.propertyeditors.URLEditor;
import org.springframework.beans.propertyeditors.UUIDEditor;
import org.springframework.beans.propertyeditors.ZoneIdEditor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Base implementation of the {@link PropertyEditorRegistry} interface.
 * Provides management of default editors and custom editors.
 * Mainly serves as base class for {@link BeanWrapperImpl}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 1.2.6
 * @see java.beans.PropertyEditorManager
 * @see java.beans.PropertyEditorSupport#setAsText
 * @see java.beans.PropertyEditorSupport#setValue
 */
public class PropertyEditorRegistrySupport implements PropertyEditorRegistry {
	/**
	 * 属性编辑器的注册中心 --
	 * 通过组合关系 ConversionService Converter有任何耦合关系
	 */

	@Nullable
	private ConversionService conversionService; // ❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️❗️

	private boolean defaultEditorsActive = false;

	private boolean configValueEditorsActive = false;

	@Nullable
	private Map<Class<?>, PropertyEditor> defaultEditors; // 默认编辑器

	@Nullable
	private Map<Class<?>, PropertyEditor> overriddenDefaultEditors;
	// 重写的默认编辑器 -- ConversionService 将覆盖这样的默认编辑器

	@Nullable
	private Map<Class<?>, PropertyEditor> customEditors;
	// 定制编辑器 -- 自定义编辑器通常会覆盖 ConversionService

	@Nullable
	private Map<String, CustomEditorHolder> customEditorsForPath;
	// 该map的key是用户可以自定义的
	// 而customEditors就是属性编辑器的转换目标的class作为key

	@Nullable
	private Map<Class<?>, PropertyEditor> customEditorCache; // 定制编辑器 - 缓存


	/**
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 */
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * Return the associated ConversionService, if any.
	 */
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}


	//---------------------------------------------------------------------
	// Management of default editors
	//---------------------------------------------------------------------

	/**
	 * Activate the default editors for this registry instance,
	 * allowing for lazily registering default editors when needed.
	 */
	protected void registerDefaultEditors() {
		// 激活此注册表实例的默认编辑器，允许在需要时延迟注册默认编辑器。
		this.defaultEditorsActive = true;
	}

	/**
	 * Activate config value editors which are only intended for configuration purposes,
	 * such as {@link org.springframework.beans.propertyeditors.StringArrayPropertyEditor}.
	 * <p>Those editors are not registered by default simply because they are in
	 * general inappropriate for data binding purposes. Of course, you may register
	 * them individually in any case, through {@link #registerCustomEditor}.
	 */
	public void useConfigValueEditors() {
		this.configValueEditorsActive = true;
	}

	/**
	 * Override the default editor for the specified type with the given property editor.
	 * <p>Note that this is different from registering a custom editor in that the editor
	 * semantically still is a default editor. A ConversionService will override such a
	 * default editor, whereas custom editors usually override the ConversionService.
	 * @param requiredType the type of the property
	 * @param propertyEditor the editor to register
	 * @see #registerCustomEditor(Class, PropertyEditor)
	 */
	public void overrideDefaultEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
		// 使用给定的属性编辑器覆盖指定类型的默认编辑器。
		//	请注意，这与注册自定义编辑器不同，因为该编辑器在语义上仍然是默认编辑器。
		//	ConversionService 将覆盖这样的默认编辑器，而自定义编辑器通常会覆盖 ConversionService
		if (this.overriddenDefaultEditors == null) {
			this.overriddenDefaultEditors = new HashMap<>();
		}
		this.overriddenDefaultEditors.put(requiredType, propertyEditor);
	}

	/**
	 * Retrieve the default editor for the given property type, if any.
	 * <p>Lazily registers the default editors, if they are active.
	 * @param requiredType type of the property
	 * @return the default editor, or {@code null} if none found
	 * @see #registerDefaultEditors
	 */
	@Nullable
	public PropertyEditor getDefaultEditor(Class<?> requiredType) {
		// 1. defaultEditorsActive 为true,表示默认的数据编辑器被激活
		// 依次从 overriddenDefaultEditors、defaultEditors查找合适requireType的编辑器
		if (!this.defaultEditorsActive) {
			return null;
		}
		// 2. 一般 overriddenDefaultEditors 第一次都是null
		if (this.overriddenDefaultEditors != null) {
			PropertyEditor editor = this.overriddenDefaultEditors.get(requiredType);
			if (editor != null) {
				return editor;
			}
		}
		// 3. 一般 defaultEditors 第一次都是null
		if (this.defaultEditors == null) {
			// defaultEditors为空，就需要进行延迟初始阿虎 -- 重点
			createDefaultEditors();
		}
		// 4. 重新差杭州啊
		return this.defaultEditors.get(requiredType); // 然后根据类型查找
	}

	/**
	 * Actually register the default editors for this registry instance.
	 */
	private void createDefaultEditors() {
		/**
		 * 核心的延迟初始化 -- 默认注册的编辑器
		 *
		 * 需要认识一点：PropertyEditorSupport的实现类其内部没有保存与具体转换目标的信息，仅仅是以Object存储
		 * 因此需要这里的map key作为转换的目标，这也是注册中心的一个作用
		 */
		this.defaultEditors = new HashMap<>(64);

		// 简单的编辑器，没有参数化功能。JDK不包含任何这些目标类型的默认编辑器。
		this.defaultEditors.put(Charset.class, new CharsetEditor());
		this.defaultEditors.put(Class.class, new ClassEditor());
		this.defaultEditors.put(Class[].class, new ClassArrayEditor());
		this.defaultEditors.put(Currency.class, new CurrencyEditor());
		this.defaultEditors.put(File.class, new FileEditor());
		this.defaultEditors.put(InputStream.class, new InputStreamEditor());
		this.defaultEditors.put(InputSource.class, new InputSourceEditor());
		this.defaultEditors.put(Locale.class, new LocaleEditor());
		this.defaultEditors.put(Path.class, new PathEditor());
		this.defaultEditors.put(Pattern.class, new PatternEditor());
		this.defaultEditors.put(Properties.class, new PropertiesEditor());
		this.defaultEditors.put(Reader.class, new ReaderEditor());
		this.defaultEditors.put(Resource[].class, new ResourceArrayPropertyEditor());
		this.defaultEditors.put(TimeZone.class, new TimeZoneEditor());
		this.defaultEditors.put(URI.class, new URIEditor());
		this.defaultEditors.put(URL.class, new URLEditor());
		this.defaultEditors.put(UUID.class, new UUIDEditor());
		this.defaultEditors.put(ZoneId.class, new ZoneIdEditor());

		// 集合编辑器的默认实例。
		// Can be overridden by registering custom instances of those as custom editors.
		this.defaultEditors.put(Collection.class, new CustomCollectionEditor(Collection.class));
		this.defaultEditors.put(Set.class, new CustomCollectionEditor(Set.class));
		this.defaultEditors.put(SortedSet.class, new CustomCollectionEditor(SortedSet.class));
		this.defaultEditors.put(List.class, new CustomCollectionEditor(List.class));
		this.defaultEditors.put(SortedMap.class, new CustomMapEditor(SortedMap.class));

		// Default editors for primitive arrays.
		// 原始数组的默认编辑器。
		this.defaultEditors.put(byte[].class, new ByteArrayPropertyEditor());
		this.defaultEditors.put(char[].class, new CharArrayPropertyEditor());

		// The JDK does not contain a default editor for char!
		// JDK 不包含 char 的默认编辑器！
		this.defaultEditors.put(char.class, new CharacterEditor(false));
		this.defaultEditors.put(Character.class, new CharacterEditor(true));

		// Spring's CustomBooleanEditor accepts more flag values than the JDK's default editor.
		// Spring 的 CustomBooleanEditor 接受的标志值比 JDK 的默认编辑器多。
		this.defaultEditors.put(boolean.class, new CustomBooleanEditor(false));
		this.defaultEditors.put(Boolean.class, new CustomBooleanEditor(true));

		// The JDK does not contain default editors for number wrapper types!
		// Override JDK primitive number editors with our own CustomNumberEditor.
		this.defaultEditors.put(byte.class, new CustomNumberEditor(Byte.class, false));
		this.defaultEditors.put(Byte.class, new CustomNumberEditor(Byte.class, true));
		this.defaultEditors.put(short.class, new CustomNumberEditor(Short.class, false));
		this.defaultEditors.put(Short.class, new CustomNumberEditor(Short.class, true));
		this.defaultEditors.put(int.class, new CustomNumberEditor(Integer.class, false));
		this.defaultEditors.put(Integer.class, new CustomNumberEditor(Integer.class, true));
		this.defaultEditors.put(long.class, new CustomNumberEditor(Long.class, false));
		this.defaultEditors.put(Long.class, new CustomNumberEditor(Long.class, true));
		this.defaultEditors.put(float.class, new CustomNumberEditor(Float.class, false));
		this.defaultEditors.put(Float.class, new CustomNumberEditor(Float.class, true));
		this.defaultEditors.put(double.class, new CustomNumberEditor(Double.class, false));
		this.defaultEditors.put(Double.class, new CustomNumberEditor(Double.class, true));
		this.defaultEditors.put(BigDecimal.class, new CustomNumberEditor(BigDecimal.class, true));
		this.defaultEditors.put(BigInteger.class, new CustomNumberEditor(BigInteger.class, true));

		// Only register config value editors if explicitly requested.
		if (this.configValueEditorsActive) {
			StringArrayPropertyEditor sae = new StringArrayPropertyEditor();
			this.defaultEditors.put(String[].class, sae);
			this.defaultEditors.put(short[].class, sae);
			this.defaultEditors.put(int[].class, sae);
			this.defaultEditors.put(long[].class, sae);
		}
	}

	/**
	 * Copy the default editors registered in this instance to the given target registry.
	 * @param target the target registry to copy to
	 */
	protected void copyDefaultEditorsTo(PropertyEditorRegistrySupport target) {
		target.defaultEditorsActive = this.defaultEditorsActive;
		target.configValueEditorsActive = this.configValueEditorsActive;
		target.defaultEditors = this.defaultEditors;
		target.overriddenDefaultEditors = this.overriddenDefaultEditors;
	}


	//---------------------------------------------------------------------
	// Management of custom editors
	//---------------------------------------------------------------------

	@Override
	public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
		registerCustomEditor(requiredType, null, propertyEditor);
	}

	@Override
	public void registerCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath, PropertyEditor propertyEditor) {
		// 核心：注册自定义编辑器的核心
		if (requiredType == null && propertyPath == null) {
			throw new IllegalArgumentException("Either requiredType or propertyPath is required");
		}
		// 1. 属性路径不为空,就以propertyPath作为key,存储到customEditorsForPath
		if (propertyPath != null) {
			if (this.customEditorsForPath == null) {
				// 初始化 自定义属性编辑器集合
				this.customEditorsForPath = new LinkedHashMap<>(16);
			}
			// 向容器中存入指定的key和value -- 注意使用CustomEditorHolder包装requiredType
			this.customEditorsForPath.put(propertyPath, new CustomEditorHolder(propertyEditor, requiredType));
		}
		// 2. 属性路径为空,就以requiredType作为key
		else {
			if (this.customEditors == null) {
				// 初始化 自定义属性加载器集合
				this.customEditors = new LinkedHashMap<>(16);
			}
			// 存入自定义属性编辑器容器中
			this.customEditors.put(requiredType, propertyEditor);
			this.customEditorCache = null;
		}
	}

	@Override
	@Nullable
	public PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath) {
		/*
		 * 核心：根据要求编辑的属性的type、或者属性的路径查找对应的属性编辑器
		 * 1、先根据属性路径propertyPath去查找属性编辑器
		 * 2、查找不到，就根据属性类型requiredType去查找属性编辑器
		 */
		Class<?> requiredTypeToUse = requiredType;
		// 1. propertyPath 不为空，一般成立
		if (propertyPath != null) {
			// 1.1 customEditorsForPath 不为空,
			if (this.customEditorsForPath != null) {
				// 首先检查 property-specific editor
				PropertyEditor editor = getCustomEditor(propertyPath, requiredType);
				if (editor == null) {
					List<String> strippedPaths = new ArrayList<>();
					// addStrippedPropertyPaths() 添加具有剥离键和/或索引的所有变体的属性路径。使用嵌套路径递归调用自身
					addStrippedPropertyPaths(strippedPaths, "", propertyPath);
					for (Iterator<String> it = strippedPaths.iterator(); it.hasNext() && editor == null;) {
						String strippedPath = it.next();
						editor = getCustomEditor(strippedPath, requiredType);
					}
				}
				if (editor != null) {
					return editor;
				}
			}
			// requiredType 一般不会为null
			if (requiredType == null) {
				// 为null时，就需要根据propertyPath去找对应属性
				requiredTypeToUse = getPropertyType(propertyPath);
			}
		}
		// No property-specific editor -> check type-specific editor.
		// 2. 没有特定于属性路径的编辑器->那么就选中特定于类型的编辑器。
		return getCustomEditor(requiredTypeToUse);
	}

	/**
	 * Determine whether this registry contains a custom editor
	 * for the specified array/collection element.
	 * @param elementType the target type of the element
	 * (can be {@code null} if not known)
	 * @param propertyPath the property path (typically of the array/collection;
	 * can be {@code null} if not known)
	 * @return whether a matching custom editor has been found
	 */
	public boolean hasCustomEditorForElement(@Nullable Class<?> elementType, @Nullable String propertyPath) {
		if (propertyPath != null && this.customEditorsForPath != null) {
			for (Map.Entry<String, CustomEditorHolder> entry : this.customEditorsForPath.entrySet()) {
				if (PropertyAccessorUtils.matchesProperty(entry.getKey(), propertyPath) &&
						entry.getValue().getPropertyEditor(elementType) != null) {
					return true;
				}
			}
		}
		// No property-specific editor -> check type-specific editor.
		return (elementType != null && this.customEditors != null && this.customEditors.containsKey(elementType));
	}

	/**
	 * Determine the property type for the given property path.
	 * <p>Called by {@link #findCustomEditor} if no required type has been specified,
	 * to be able to find a type-specific editor even if just given a property path.
	 * <p>The default implementation always returns {@code null}.
	 * BeanWrapperImpl overrides this with the standard {@code getPropertyType}
	 * method as defined by the BeanWrapper interface.
	 * @param propertyPath the property path to determine the type for
	 * @return the type of the property, or {@code null} if not determinable
	 * @see BeanWrapper#getPropertyType(String)
	 */
	@Nullable
	protected Class<?> getPropertyType(String propertyPath) {
		return null;
	}

	/**
	 * Get custom editor that has been registered for the given property.
	 * @param propertyName the property path to look for
	 * @param requiredType the type to look for
	 * @return the custom editor, or {@code null} if none specific for this property
	 */
	@Nullable
	private PropertyEditor getCustomEditor(String propertyName, @Nullable Class<?> requiredType) {
		CustomEditorHolder holder = (this.customEditorsForPath != null ? this.customEditorsForPath.get(propertyName) : null);
		return (holder != null ? holder.getPropertyEditor(requiredType) : null);
	}

	/**
	 * Get custom editor for the given type. If no direct match found,
	 * try custom editor for superclass (which will in any case be able
	 * to render a value as String via {@code getAsText}).
	 * @param requiredType the type to look for
	 * @return the custom editor, or {@code null} if none found for this type
	 * @see java.beans.PropertyEditor#getAsText()
	 */
	@Nullable
	private PropertyEditor getCustomEditor(@Nullable Class<?> requiredType) {
		// 查找特定类型的 requiredType 的属性编辑器

		if (requiredType == null || this.customEditors == null) {
			return null;
		}
		// customEditors 不为空
		// 直接根据类型查找对应的属性编辑器
		PropertyEditor editor = this.customEditors.get(requiredType);
		if (editor == null) {
			// Check cached editor for type, registered for superclass or interface.
			if (this.customEditorCache != null) {
				editor = this.customEditorCache.get(requiredType);
			}
			if (editor == null) {
				// Find editor for superclass or interface.
				for (Iterator<Class<?>> it = this.customEditors.keySet().iterator(); it.hasNext() && editor == null;) {
					Class<?> key = it.next();
					if (key.isAssignableFrom(requiredType)) {
						editor = this.customEditors.get(key);
						// Cache editor for search type, to avoid the overhead
						// of repeated assignable-from checks.
						if (this.customEditorCache == null) {
							this.customEditorCache = new HashMap<>();
						}
						this.customEditorCache.put(requiredType, editor);
					}
				}
			}
		}
		return editor;
	}

	/**
	 * Guess the property type of the specified property from the registered
	 * custom editors (provided that they were registered for a specific type).
	 * @param propertyName the name of the property
	 * @return the property type, or {@code null} if not determinable
	 */
	@Nullable
	protected Class<?> guessPropertyTypeFromEditors(String propertyName) {
		if (this.customEditorsForPath != null) {
			CustomEditorHolder editorHolder = this.customEditorsForPath.get(propertyName);
			if (editorHolder == null) {
				List<String> strippedPaths = new ArrayList<>();
				addStrippedPropertyPaths(strippedPaths, "", propertyName);
				for (Iterator<String> it = strippedPaths.iterator(); it.hasNext() && editorHolder == null;) {
					String strippedName = it.next();
					editorHolder = this.customEditorsForPath.get(strippedName);
				}
			}
			if (editorHolder != null) {
				return editorHolder.getRegisteredType();
			}
		}
		return null;
	}

	/**
	 * Copy the custom editors registered in this instance to the given target registry.
	 * @param target the target registry to copy to
	 * @param nestedProperty the nested property path of the target registry, if any.
	 * If this is non-null, only editors registered for a path below this nested property
	 * will be copied. If this is null, all editors will be copied.
	 */
	protected void copyCustomEditorsTo(PropertyEditorRegistry target, @Nullable String nestedProperty) {
		String actualPropertyName =
				(nestedProperty != null ? PropertyAccessorUtils.getPropertyName(nestedProperty) : null);
		if (this.customEditors != null) {
			this.customEditors.forEach(target::registerCustomEditor);
		}
		if (this.customEditorsForPath != null) {
			this.customEditorsForPath.forEach((editorPath, editorHolder) -> {
				if (nestedProperty != null) {
					int pos = PropertyAccessorUtils.getFirstNestedPropertySeparatorIndex(editorPath);
					if (pos != -1) {
						String editorNestedProperty = editorPath.substring(0, pos);
						String editorNestedPath = editorPath.substring(pos + 1);
						if (editorNestedProperty.equals(nestedProperty) || editorNestedProperty.equals(actualPropertyName)) {
							target.registerCustomEditor(
									editorHolder.getRegisteredType(), editorNestedPath, editorHolder.getPropertyEditor());
						}
					}
				}
				else {
					target.registerCustomEditor(
							editorHolder.getRegisteredType(), editorPath, editorHolder.getPropertyEditor());
				}
			});
		}
	}


	/**
	 * Add property paths with all variations of stripped keys and/or indexes.
	 * Invokes itself recursively with nested paths.
	 * @param strippedPaths the result list to add to
	 * @param nestedPath the current nested path
	 * @param propertyPath the property path to check for keys/indexes to strip
	 */
	private void addStrippedPropertyPaths(List<String> strippedPaths, String nestedPath, String propertyPath) {
		int startIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_PREFIX_CHAR);
		if (startIndex != -1) {
			int endIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_SUFFIX_CHAR);
			if (endIndex != -1) {
				String prefix = propertyPath.substring(0, startIndex);
				String key = propertyPath.substring(startIndex, endIndex + 1);
				String suffix = propertyPath.substring(endIndex + 1);
				// Strip the first key.
				strippedPaths.add(nestedPath + prefix + suffix);
				// Search for further keys to strip, with the first key stripped.
				addStrippedPropertyPaths(strippedPaths, nestedPath + prefix, suffix);
				// Search for further keys to strip, with the first key not stripped.
				addStrippedPropertyPaths(strippedPaths, nestedPath + prefix + key, suffix);
			}
		}
	}


	/**
	 * Holder for a registered custom editor with property name.
	 * Keeps the PropertyEditor itself plus the type it was registered for.
	 */
	private static final class CustomEditorHolder {
		/**
		 * 具有属性名称的已注册自定义编辑器的持有者。
		 * 保留PropertyEditor本身及其注册的类型
		 */

		private final PropertyEditor propertyEditor;

		@Nullable
		private final Class<?> registeredType;

		private CustomEditorHolder(PropertyEditor propertyEditor, @Nullable Class<?> registeredType) {
			this.propertyEditor = propertyEditor;
			this.registeredType = registeredType;
		}

		private PropertyEditor getPropertyEditor() {
			return this.propertyEditor;
		}

		@Nullable
		private Class<?> getRegisteredType() {
			return this.registeredType;
		}

		@Nullable
		private PropertyEditor getPropertyEditor(@Nullable Class<?> requiredType) {
			// 1、registeredType 为空，既没有指定具体的转换目标类型
			// 2、指定了具体的转换类型，且与requiredType是同一个继承体系
			// 3、requiredType为null，registeredType不为空，但registeredType为集合或数组
			if (this.registeredType == null ||
					(requiredType != null &&
					(ClassUtils.isAssignable(this.registeredType, requiredType) ||
					ClassUtils.isAssignable(requiredType, this.registeredType))) ||
					(requiredType == null &&
					(!Collection.class.isAssignableFrom(this.registeredType) && !this.registeredType.isArray()))) {
				return this.propertyEditor;
			}
			else {
				return null;
			}
		}
	}

}
