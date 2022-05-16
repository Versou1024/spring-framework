/*
 * Copyright 2002-2009 the original author or authors.
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

package org.springframework.core.type.classreading;

import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;

/**
 * Simple facade for accessing class metadata,
 * as read by an ASM {@link org.springframework.asm.ClassReader}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
public interface MetadataReader {

	/*
	 * 你是否有疑问：为何Spring要提供一个标准实现和一个ASM的实现呢？这里就能给你答案。
	 * 此接口是一个访问ClassMetadata等的简单门面，实现是委托给org.springframework.asm.ClassReader、ClassVisitor来处理的，
	 * 它不用把Class加载进JVM就可以拿到元数据，因为它读取的是资源：Resource，这是它最大的优势所在。
	 * 原文链接：https://blog.csdn.net/f641385712/article/details/88765470
	 * 
	 * 上述所有实现，都是委托对应元素直接基于 反射 实现的，因此前提是对应的 Class 必须加载到 JVM 中，而实际的应用场景，并不一定保证对应的 Class 已加载，比如 Spring 的第三方类扫描
	 * 因此，MetadataReader 接口抽象元数据的读取，其实现基于 ASM 直接扫描对应文件字节码实现，Spring 提供了唯一实现 SimpleMetadataReader
	 * 此接口是一个访问ClassMetadata等的简单门面，实现是委托给org.springframework.asm.ClassReader、ClassVisitor来处理的，它不用把Class加载进JVM就可以拿到元数据，因为它读取的是资源：Resource，这是它最大的优势所在。
	 */

	/**
	 * Return the resource reference for the class file.
	 */
	Resource getResource(); // 返回此Class文件的来源（资源）

	/**
	 * Read basic class metadata for the underlying class.
	 */
	ClassMetadata getClassMetadata(); // 返回此Class的元数据信息

	/**
	 * Read full annotation metadata for the underlying class,
	 * including metadata for annotated methods.
	 */
	AnnotationMetadata getAnnotationMetadata(); // 返回此类的注解元信息（包括方法的）

}
