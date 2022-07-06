/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.beans.factory.Aware;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Interface to be implemented by any @{@link Configuration} class that wishes
 * to be injected with the {@link AnnotationMetadata} of the @{@code Configuration}
 * class that imported it. Useful in conjunction with annotations that
 * use @{@link Import} as a meta-annotation.
 *
 * @author Chris Beams
 * @since 3.1
 */
public interface ImportAware extends Aware {
	// 设置importing在@Configuration的class上的注解信息
	// 这个接口可以被任何标注@Configuration类实现，以期望注入导入这个配置类的@Configuration类上的AnnotationMetadata
	// 比如

	// @Import(C2.class)
	// class @interface EnableXxx{
	//  	...
	// }

	//  @Configuration
	//  @EnableXxx
	//  class C1{
	//  
	//  }
	//  
	//  class C2 extends ImportSelector{ // c2 实现了 ImportAware，c2是被c1导入的，因此获取的就是c1的AnnotationMetadata
	//  
	//  	@Override
	//  	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
	//  		return new String[]{"com.xylink.C3"};
	//  	}
	//  }
	// 
	// class C3 extends ImportAware{
	// 		public void setImportMetadata(AnnotationMetadata importMetadata){
	//			...	 // AnnotationMetadata 就是C1的注解信息哦
	//		}
	//  }


	/**
	 * Set the annotation metadata of the importing @{@code Configuration} class.
	 */
	void setImportMetadata(AnnotationMetadata importMetadata);

}
