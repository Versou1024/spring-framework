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

package org.springframework.web.servlet.view.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

import org.springframework.util.Assert;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

/**
 * Abstract superclass for PDF views that operate on an existing
 * document with an AcroForm. Application-specific view classes
 * will extend this class to merge the PDF form with model data.
 *
 * <p>This view implementation uses Bruno Lowagie's
 * <a href="https://www.lowagie.com/iText">iText</a> API.
 * Known to work with the original iText 2.1.7 as well as its fork
 * <a href="https://github.com/LibrePDF/OpenPDF">OpenPDF</a>.
 * <b>We strongly recommend OpenPDF since it is actively maintained
 * and fixes an important vulnerability for untrusted PDF content.</b>
 *
 * <p>Thanks to Bryant Larsen for the suggestion and the original prototype!
 *
 * @author Juergen Hoeller
 * @since 2.5.4
 * @see AbstractPdfView
 */
public abstract class AbstractPdfStamperView extends AbstractUrlBasedView {
	// AbstractPdfStamperView
	// 这个和AbstractPdfView有点类似，不过它出来相对较晚。因为它可以基于URL去渲染PDF，它也是个抽象类，Spring MVC并没有PDF的具体的视图实现~~

	public AbstractPdfStamperView(){
		// 设置contentType application/pdf
		setContentType("application/pdf");
	}


	@Override
	protected boolean generatesDownloadContent() {
		// 返回此视图是否生成下载内容（通常是 PDF 或 Excel 文件等二进制内容）。
		// 对于下载类型的请求,需要在response中添加一些额外的请求头,则在AbstractView中完成了模板方法
		// 只需要打开开关即可

		return true;
	}

	@Override
	protected final void renderMergedOutputModel(
			Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 超类 AbstractView 的模板方法 render() 已经将需要的属性从model提取出来了,需要子类进行渲染
		// 即当前类

		// IE workaround: write into byte array first.
		// 1. 创建临时的输出流Stream
		ByteArrayOutputStream baos = createTemporaryOutputStream();

		// 2. pdfReader
		PdfReader reader = readPdfResource();
		PdfStamper stamper = new PdfStamper(reader, baos);
		mergePdfDocument(model, stamper, request, response);
		stamper.close();

		// Flush to HTTP response.
		writeToResponse(response, baos);
	}

	/**
	 * Read the raw PDF resource into an iText PdfReader.
	 * <p>The default implementation resolve the specified "url" property
	 * as ApplicationContext resource.
	 * @return the PdfReader instance
	 * @throws IOException if resource access failed
	 * @see #setUrl
	 */
	protected PdfReader readPdfResource() throws IOException {
		String url = getUrl();
		Assert.state(url != null, "'url' not set");
		return new PdfReader(obtainApplicationContext().getResource(url).getInputStream());
	}

	/**
	 * Subclasses must implement this method to merge the PDF form
	 * with the given model data.
	 * <p>This is where you are able to set values on the AcroForm.
	 * An example of what can be done at this level is:
	 * <pre class="code">
	 * // get the form from the document
	 * AcroFields form = stamper.getAcroFields();
	 *
	 * // set some values on the form
	 * form.setField("field1", "value1");
	 * form.setField("field2", "Vvlue2");
	 *
	 * // set the disposition and filename
	 * response.setHeader("Content-disposition", "attachment; FILENAME=someName.pdf");</pre>
	 * <p>Note that the passed-in HTTP response is just supposed to be used
	 * for setting cookies or other HTTP headers. The built PDF document itself
	 * will automatically get written to the response after this method returns.
	 * @param model the model Map
	 * @param stamper the PdfStamper instance that will contain the AcroFields.
	 * You may also customize this PdfStamper instance according to your needs,
	 * e.g. setting the "formFlattening" property.
	 * @param request in case we need locale etc. Shouldn't look at attributes.
	 * @param response in case we need to set cookies. Shouldn't write to it.
	 * @throws Exception any exception that occurred during document building
     */
	protected abstract void mergePdfDocument(Map<String, Object> model, PdfStamper stamper,
			HttpServletRequest request, HttpServletResponse response) throws Exception;

}
