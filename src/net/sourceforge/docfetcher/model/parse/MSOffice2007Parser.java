/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.util.annotations.NotNull;

import org.apache.poi.POITextExtractor;
import org.apache.poi.POIXMLProperties;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackageProperties;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.CTProperties;

import com.google.common.io.Closeables;

/**
 * @author Tran Nam Quang
 */
abstract class MSOffice2007Parser extends FileParser {
	
	static {
		/*
		 * Disable zipbomb detection. With zipbomb detection on, POI will throw
		 * an IOException if it encounters a zip file with unusually high
		 * compression ratio.
		 */
		ZipSecureFile.setMinInflateRatio(0);
		
		ZipSecureFile.setMaxEntrySize(0xFFFFFFFFL);
		ZipSecureFile.setMaxTextSize(0xFFFFFFFFL);
	}
	
	public static final class MSWord2007Parser extends MSOffice2007Parser {
		public MSWord2007Parser() {
			super(Msg.filetype_docx.get(), "docx", "docm", "dotx", "dotm");
		}
	}
	
	public static final class MSExcel2007Parser extends MSOffice2007Parser {
		public MSExcel2007Parser() {
			super(Msg.filetype_xlsx.get(), "xlsx", "xlsm", "xltx", "xltm");
		}
	}
	
	public static final class MSPowerPoint2007Parser extends MSOffice2007Parser {
		public MSPowerPoint2007Parser() {
			super(Msg.filetype_pptx.get(), "pptx", "pptm", "ppsx", "ppsm", "potx", "potm");
		}
	}
	
	private final Collection<String> types = MediaType.Col.application("zip");
	
	private final String typeLabel;
	private final Collection<String> extensions;
	
	private MSOffice2007Parser(	@NotNull String typeLabel,
								@NotNull String... extensions) {
		this.typeLabel = typeLabel;
		this.extensions = Arrays.asList(extensions);
	}

	@Override
	protected ParseResult parse(File file, ParseContext context)
			throws ParseException {
		OPCPackage pkg = null;
		try {
			pkg = OPCPackage.open(file.getPath(), PackageAccess.READ);
			String contents = extractText(pkg);
			
			// Open properties
			PackageProperties props = pkg.getPackageProperties();
			
			// Get author(s)
			String author = null;
			String defaultAuthor = props.getCreatorProperty().getValue();
			String lastAuthor = props.getLastModifiedByProperty().getValue();
			if (defaultAuthor == null) {
				if (lastAuthor != null)
					author = lastAuthor;
			}
			else if (lastAuthor == null) {
				author = defaultAuthor;
			}
			else {
				if (defaultAuthor.equals(lastAuthor))
					author = defaultAuthor;
				else
					author = defaultAuthor + ", " + lastAuthor; //$NON-NLS-1$
			}
			
			// Get other metadata
			String description = props.getDescriptionProperty().getValue();
			String keywords = props.getKeywordsProperty().getValue();
			String subject = props.getSubjectProperty().getValue();
			String title = props.getTitleProperty().getValue();
			
			return new ParseResult(contents)
				.setTitle(title)
				.addAuthor(author)
				.addMiscMetadata(description)
				.addMiscMetadata(keywords)
				.addMiscMetadata(subject)
				.setPageCount(getPageCount(pkg));
		}
		catch (NoClassDefFoundError e) {
			if (e.getMessage().contains("Could not initialize class")) {
				/*
				 * This crash seems to have surfaced after the crash in the next
				 * exception handler was surpressed, starting with DocFetcher
				 * 1.1.16. This crash is probably also related to the POI
				 * upgrade to 3.11, and upgrading the Java runtime also fixes
				 * it.
				 *
				 * First bug reports:
				 * https://sourceforge.net/p/docfetcher/bugs/1059/
				 * https://sourceforge.net/p/docfetcher/bugs/1068/
				 */
				String msg = "Outdated Java version. Please upgrade to Java 1.6.0 Update 18 or newer.";
				throw new ParseException(msg, e);
			}
			else {
				throw e;
			}
		}
		catch (ExceptionInInitializerError e) {
			/*
			 * Apache POI's StringUtil class has a static initializer that
			 * loads the Big5 charset. On some Java installations (especially
			 * minimal/modular JREs), the Big5 charset is not available in
			 * the java.base module (it's in jdk.charsets), causing an
			 * UnsupportedCharsetException wrapped in ExceptionInInitializerError.
			 *
			 * Bug reports:
			 * https://sourceforge.net/p/docfetcher/bugs/2308/
			 * https://sourceforge.net/p/docfetcher/bugs/2438/
			 */
			String msg = "Your Java installation is missing required character set support. "
					+ "Please use a complete Java runtime (JRE) that includes the jdk.charsets module, "
					+ "or upgrade to a newer version of DocFetcher.";
			throw new ParseException(msg, e);
		}
		catch (NoSuchMethodError e) {
			if (e.getMessage().contains("XMLEventFactory.newFactory")) {
				/*
				 * This crash started occuring after upgrading POI from
				 * 3.11-beta2 to 3.11 in DocFetcher 1.1.13. The crash happens
				 * with Java runtimes older than Java 1.6.0_18.
				 * 
				 * First bug report:
				 * https://sourceforge.net/p/docfetcher/bugs/902/
				 * 
				 * Further discussion:
				 * http://stackoverflow.com/questions/26866398/nosuchmethoderror
				 * -in-main-thread-while-reading-xlsx-using-apache-poi
				 */
				String msg = "Outdated Java version. Please upgrade to Java 1.6.0 Update 18 or newer.";
				throw new ParseException(msg, e);
			}
			else {
				throw e;
			}
		}
		catch (java.util.zip.ZipError e) {
			/*
			 * java.util.zip.ZipError is an Error (not Exception) that occurs
			 * at the native level when the JVM's ZIP library encounters corrupted
			 * or invalid ZIP entries. Office 2007+ files (.docx, .xlsx, .pptx, etc.)
			 * are ZIP files, so corrupted Office files can trigger this error.
			 *
			 * The error typically has a message like "jzentry == 0" with details
			 * about the file being read. Since this is an Error rather than an
			 * Exception, it bypasses normal exception handling and crashes the
			 * application if not caught explicitly.
			 *
			 * Bug report:
			 * https://sourceforge.net/p/docfetcher/bugs/2188/
			 */
			String msg = "The file appears to be corrupted or invalid. "
					+ "Try opening it in Microsoft Office to verify if it's a valid document.";
			throw new ParseException(msg, e);
		}
		catch (Exception e) {
			throw new ParseException(e);
		}
		finally {
			try {
				Closeables.closeQuietly(pkg);
			} catch (OpenXML4JRuntimeException e2) {
				// Bug in POI 3.12 beta 1 and earlier, see:
				// http://stackoverflow.com/questions/28593223/apache-poi-opcpackage-unable-to-save-jasper-report-generated-xlsx
				// Remove this workaround once POI has been upgraded.
			}
		}
	}
	
	@Override
	protected final String renderText(File file, String filename)
			throws ParseException {
		OPCPackage pkg = null;
		try {
			pkg = OPCPackage.open(file.getPath(), PackageAccess.READ);
			return extractText(pkg);
		}
		catch (java.util.zip.ZipError e) {
			// See comment in parse() method above for details about this error
			String msg = "The file appears to be corrupted or invalid. "
					+ "Try opening it in Microsoft Office to verify if it's a valid document.";
			throw new ParseException(msg, e);
		}
		catch (Exception e) {
			throw new ParseException(e);
		}
		finally {
			Closeables.closeQuietly(pkg);
		}
	}
	
	/**
	 * Returns the page count (Word) or slide count (PowerPoint) recorded in
	 * the document's extended properties, or -1 if unavailable.
	 */
	private static int getPageCount(@NotNull OPCPackage pkg) {
		try {
			CTProperties props = new POIXMLProperties(pkg)
				.getExtendedProperties().getUnderlyingProperties();
			if (props.isSetPages() && props.getPages() > 0)
				return props.getPages();
			if (props.isSetSlides() && props.getSlides() > 0)
				return props.getSlides();
		}
		catch (Exception e) {
			// Extended properties are optional
		}
		catch (LinkageError e) {
			// Missing optional classes; treat as unavailable
		}
		return countWordPageBreaks(pkg);
	}
	
	/**
	 * Fallback for Word documents without page metadata: Counts the page
	 * break markers in the main document part. Word records a
	 * 'lastRenderedPageBreak' marker at each page boundary when saving; if
	 * there are none, explicit page breaks are counted instead. Returns -1
	 * if no markers are found or this isn't a Word document.
	 */
	private static int countWordPageBreaks(@NotNull OPCPackage pkg) {
		InputStream in = null;
		try {
			PackagePart part = pkg.getPart(
				PackagingURIHelper.createPartName("/word/document.xml"));
			if (part == null)
				return -1;
			in = part.getInputStream();
			int rendered = 0;
			int explicit = 0;
			byte[] buf = new byte[64 * 1024];
			String carry = "";
			int n;
			while ((n = in.read(buf)) > 0) {
				String chunk = carry + new String(buf, 0, n, "UTF-8");
				rendered += countOccurrences(chunk, "<w:lastRenderedPageBreak");
				explicit += countOccurrences(chunk, "w:type=\"page\"");
				// Keep a tail so that markers spanning two chunks are found
				carry = chunk.length() > 40 ? chunk.substring(chunk.length() - 40) : chunk;
			}
			int breaks = rendered > 0 ? rendered : explicit;
			return breaks > 0 ? breaks + 1 : -1;
		}
		catch (Exception e) {
			return -1;
		}
		finally {
			Closeables.closeQuietly(in);
		}
	}
	
	private static int countOccurrences(@NotNull String s, @NotNull String sub) {
		int count = 0;
		int i = 0;
		while ((i = s.indexOf(sub, i)) >= 0) {
			count++;
			i += sub.length();
		}
		return count;
	}
	
	// Caller is responsible for closing the given package
	@NotNull
	private static String extractText(@NotNull OPCPackage pkg) throws Exception {
		POITextExtractor extractor = ExtractorFactory.createExtractor(pkg);
		if (extractor instanceof XSSFExcelExtractor) {
			// Results of formula cells, and the formulas themselves if wanted
			XSSFWorkbook workbook = (XSSFWorkbook) ((XSSFExcelExtractor) extractor).getDocument();
			return ExcelText.extract(workbook, ProgramConf.Bool.IndexExcelFormulas.get());
		}
		String text = extractor.getText();
		return text;
	}

	protected final Collection<String> getExtensions() {
		return extensions;
	}

	protected final Collection<String> getTypes() {
		return types;
	}

	public final String getTypeLabel() {
		return typeLabel;
	}

}
