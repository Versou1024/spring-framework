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

package org.springframework.http.converter.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;

/**
 * Implementation of {@link org.springframework.http.converter.HttpMessageConverter}
 * that can read and write {@link Source} objects.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.0
 * @param <T> the converted object type
 */
public class SourceHttpMessageConverter<T extends Source> extends AbstractHttpMessageConverter<T> {
	// 处理一些和xml相关的资源，比如DOMSource、SAXSource、SAXSource等等

	private static final EntityResolver NO_OP_ENTITY_RESOLVER =
			(publicId, systemId) -> new InputSource(new StringReader(""));

	private static final XMLResolver NO_OP_XML_RESOLVER =
			(publicID, systemID, base, ns) -> StreamUtils.emptyInput();

	private static final Set<Class<?>> SUPPORTED_CLASSES = new HashSet<>(8);

	static {
		// 默认支持的class是DOMSource\SAXSource\StAXSource\StreamSource\Source
		SUPPORTED_CLASSES.add(DOMSource.class);
		SUPPORTED_CLASSES.add(SAXSource.class);
		SUPPORTED_CLASSES.add(StAXSource.class);
		SUPPORTED_CLASSES.add(StreamSource.class);
		SUPPORTED_CLASSES.add(Source.class);
	}


	private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

	private boolean supportDtd = false;

	private boolean processExternalEntities = false;


	/**
	 * Sets the {@link #setSupportedMediaTypes(java.util.List) supportedMediaTypes}
	 * to {@code text/xml} and {@code application/xml}, and {@code application/*-xml}.
	 */
	public SourceHttpMessageConverter() {
		// 注意: 这里将超类的supportedMediaTypes设置为支持以下三种类型
		// 为text/xml	application/xml		application/*+xml
		// 这表明:
		// 读的时候,形参必须为Source\StreamSource等class,且请求头中的content-type为text/xml application/xml application/*+xml 的类型
		// 返回的时候,同上的概念
		super(MediaType.APPLICATION_XML, MediaType.TEXT_XML, new MediaType("application", "*+xml"));
	}


	/**
	 * Indicate whether DTD parsing should be supported.
	 * <p>Default is {@code false} meaning that DTD is disabled.
	 */
	public void setSupportDtd(boolean supportDtd) {
		// 指示是否应支持 DTD 解析。
		// 默认为false,表示禁用DTD
		// 因为它所支持的都是 text/xml applicaiton/xml 等xml,所以和DTD有关
		this.supportDtd = supportDtd;
	}

	/**
	 * Return whether DTD parsing is supported.
	 */
	public boolean isSupportDtd() {
		return this.supportDtd;
	}

	/**
	 * Indicate whether external XML entities are processed when converting to a Source.
	 * <p>Default is {@code false}, meaning that external entities are not resolved.
	 * <p><strong>Note:</strong> setting this option to {@code true} also
	 * automatically sets {@link #setSupportDtd} to {@code true}.
	 */
	public void setProcessExternalEntities(boolean processExternalEntities) {
		this.processExternalEntities = processExternalEntities;
		if (processExternalEntities) {
			this.supportDtd = true;
		}
	}

	/**
	 * Return whether XML external entities are allowed.
	 */
	public boolean isProcessExternalEntities() {
		return this.processExternalEntities;
	}


	@Override
	public boolean supports(Class<?> clazz) {
		// 重写超类的supports()
		// 表明SourceHttpMessageConverter支持返回值或形参是SUPPORTED_CLASSES的class即可


		return SUPPORTED_CLASSES.contains(clazz);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected T readInternal(Class<? extends T> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		// 重写超类: readInternal()


		// 1. 获取InputStream,根据形参值类型做一个做不同的调用
		// 具体不做了解展示
		InputStream body = inputMessage.getBody();
		if (DOMSource.class == clazz) {
			return (T) readDOMSource(body, inputMessage);
		}
		else if (SAXSource.class == clazz) {
			return (T) readSAXSource(body, inputMessage);
		}
		else if (StAXSource.class == clazz) {
			return (T) readStAXSource(body, inputMessage);
		}
		else if (StreamSource.class == clazz || Source.class == clazz) {
			return (T) readStreamSource(body);
		}
		else {
			throw new HttpMessageNotReadableException("Could not read class [" + clazz +
					"]. Only DOMSource, SAXSource, StAXSource, and StreamSource are supported.", inputMessage);
		}
	}

	private DOMSource readDOMSource(InputStream body, HttpInputMessage inputMessage) throws IOException {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(true);
			documentBuilderFactory.setFeature(
					"http://apache.org/xml/features/disallow-doctype-decl", !isSupportDtd());
			documentBuilderFactory.setFeature(
					"http://xml.org/sax/features/external-general-entities", isProcessExternalEntities());
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			if (!isProcessExternalEntities()) {
				documentBuilder.setEntityResolver(NO_OP_ENTITY_RESOLVER);
			}
			Document document = documentBuilder.parse(body);
			return new DOMSource(document);
		}
		catch (NullPointerException ex) {
			if (!isSupportDtd()) {
				throw new HttpMessageNotReadableException("NPE while unmarshalling: This can happen " +
						"due to the presence of DTD declarations which are disabled.", ex, inputMessage);
			}
			throw ex;
		}
		catch (ParserConfigurationException ex) {
			throw new HttpMessageNotReadableException(
					"Could not set feature: " + ex.getMessage(), ex, inputMessage);
		}
		catch (SAXException ex) {
			throw new HttpMessageNotReadableException(
					"Could not parse document: " + ex.getMessage(), ex, inputMessage);
		}
	}

	@SuppressWarnings("deprecation")  // on JDK 9
	private SAXSource readSAXSource(InputStream body, HttpInputMessage inputMessage) throws IOException {
		try {
			XMLReader xmlReader = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
			xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !isSupportDtd());
			xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", isProcessExternalEntities());
			if (!isProcessExternalEntities()) {
				xmlReader.setEntityResolver(NO_OP_ENTITY_RESOLVER);
			}
			byte[] bytes = StreamUtils.copyToByteArray(body);
			return new SAXSource(xmlReader, new InputSource(new ByteArrayInputStream(bytes)));
		}
		catch (SAXException ex) {
			throw new HttpMessageNotReadableException(
					"Could not parse document: " + ex.getMessage(), ex, inputMessage);
		}
	}

	private Source readStAXSource(InputStream body, HttpInputMessage inputMessage) {
		try {
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, isSupportDtd());
			inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, isProcessExternalEntities());
			if (!isProcessExternalEntities()) {
				inputFactory.setXMLResolver(NO_OP_XML_RESOLVER);
			}
			XMLStreamReader streamReader = inputFactory.createXMLStreamReader(body);
			return new StAXSource(streamReader);
		}
		catch (XMLStreamException ex) {
			throw new HttpMessageNotReadableException(
					"Could not parse document: " + ex.getMessage(), ex, inputMessage);
		}
	}

	private StreamSource readStreamSource(InputStream body) throws IOException {
		byte[] bytes = StreamUtils.copyToByteArray(body);
		return new StreamSource(new ByteArrayInputStream(bytes));
	}

	@Override
	@Nullable
	protected Long getContentLength(T t, @Nullable MediaType contentType) {
		// 重写超类的getContentLength()方法
		//

		if (t instanceof DOMSource) {
			try {
				CountingOutputStream os = new CountingOutputStream();
				transform(t, new StreamResult(os));
				return os.count;
			}
			catch (TransformerException ex) {
				// ignore
			}
		}
		return null;
	}

	@Override
	protected void writeInternal(T t, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		try {
			Result result = new StreamResult(outputMessage.getBody());
			transform(t, result);
		}
		catch (TransformerException ex) {
			throw new HttpMessageNotWritableException("Could not transform [" + t + "] to output message", ex);
		}
	}

	private void transform(Source source, Result result) throws TransformerException {
		this.transformerFactory.newTransformer().transform(source, result);
	}


	private static class CountingOutputStream extends OutputStream {

		long count = 0;

		@Override
		public void write(int b) throws IOException {
			this.count++;
		}

		@Override
		public void write(byte[] b) throws IOException {
			this.count += b.length;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			this.count += len;
		}
	}

}
