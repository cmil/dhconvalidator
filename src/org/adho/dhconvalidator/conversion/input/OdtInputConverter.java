package org.adho.dhconvalidator.conversion.input;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.XPathContext;

import org.adho.dhconvalidator.conftool.ConfToolCacheProvider;
import org.adho.dhconvalidator.conftool.Paper;
import org.adho.dhconvalidator.conftool.User;
import org.adho.dhconvalidator.conversion.Type;
import org.adho.dhconvalidator.conversion.ZipFs;

public class OdtInputConverter implements InputConverter {
	private enum Namespace {
		STYLE("style", "urn:oasis:names:tc:opendocument:xmlns:style:1.0"),
		TEXT("text", "urn:oasis:names:tc:opendocument:xmlns:text:1.0"),
		DC("dc", "http://purl.org/dc/elements/1.1/"),
		OFFICE("office", "urn:oasis:names:tc:opendocument:xmlns:office:1.0"),
		META("meta", "urn:oasis:names:tc:opendocument:xmlns:meta:1.0"),
		;
		private String name;
		private String uri;

		private Namespace(String name, String uri) {
			this.name = name;
			this.uri = uri;
		}

		public String toUri() {
			return uri;
		}
		
		public String getName() {
			return name;
		}
	}
	private static final String TEMPLATE = "template/DH_template_v1.ott";
	private static final String CONFTOOLPAPERID_ATTRIBUTENAME = "ConfToolPaperID";
	
	private XPathContext xPathContext;
	
	public OdtInputConverter() {
		xPathContext = new XPathContext();
		for (Namespace ns : Namespace.values()) {
			xPathContext.addNamespace(
				ns.getName(),
				ns.toUri());
		}
	}

	@Override
	public byte[] convert(byte[] sourceData, User user) throws IOException {
		ZipFs zipFs = new ZipFs(sourceData);
		Document contentDoc = zipFs.getDocument("content.xml");
		
		stripAutomaticParagraphStyles(contentDoc);
		stripTemplateSections(contentDoc);
		
		Document metaDoc = zipFs.getDocument("meta.xml");
		Integer paperId = getPaperIdFromMeta(metaDoc);
		Paper paper = ConfToolCacheProvider.INSTANCE.getConfToolCache().getPaper(user, paperId );

		injectTitleIntoMeta(metaDoc, paper.getTitle());
		injectAuthorsIntoMeta(metaDoc, paper.getAuthorsAndAffiliations());

		zipFs.putDocument("content.xml", contentDoc);
		return zipFs.toZipData();
	}

	private void stripTemplateSections(Document contentDoc) {
		Nodes searchResult = 
				contentDoc.query(
					"//text:section[@text:name='Authors from ConfTool']", 
					xPathContext);
		if (searchResult.size() > 0) {
			removeNodes(searchResult);
		}
		
		searchResult = 
			contentDoc.query(
				"//text:section[@text:name='Guidelines']", 
				xPathContext);
		
		if (searchResult.size() > 0) {
			removeNodes(searchResult);
		}
				
		searchResult = 
			contentDoc.query(
				"//text:section[@text:name='Title from ConfTool']", 
				xPathContext);
		
		if (searchResult.size() > 0) {
			removeNodes(searchResult);
		}
	}

	private void removeNodes(Nodes nodes) {
		for (int i=0; i<nodes.size(); i++) {
			Node n = nodes.get(i);
			n.getParent().removeChild(n);
		}
	}

	private Integer getPaperIdFromMeta(Document metaDoc) throws IOException {

		Nodes searchResult = 
			metaDoc.query(
				"/office:document-meta/office:meta/meta:user-defined[@meta:name='"
						+CONFTOOLPAPERID_ATTRIBUTENAME+"']", 
				xPathContext);
	
		if (searchResult.size() == 1) {
			Element confToolPaperIdElement = (Element) searchResult.get(0);
			return Integer.valueOf(confToolPaperIdElement.getValue());
		}
		else {
			throw new IOException(
				"document has invalid meta section: ConfToolPaperID not found!");
		}
	}

	private void stripAutomaticParagraphStyles(Document contentDoc) {
		Map<String,String> paragraphStyleMapping = new HashMap<>();
		
		Nodes styleResult = contentDoc.query(
			"/office:document-content/office:automatic-styles/style:style[@style:family='paragraph']",
			xPathContext);
		
		for (int i=0; i<styleResult.size(); i++) {
			Element styleNode = (Element)styleResult.get(i);
			System.out.println(styleNode);
			String adhocName = styleNode.getAttributeValue("name", Namespace.STYLE.toUri());
			String definedName = 
				styleNode.getAttributeValue("parent-style-name", Namespace.STYLE.toUri());
			paragraphStyleMapping.put(adhocName, definedName);
		}
		
		Nodes textResult = contentDoc.query(
			"/office:document-content/office:body/office:text/text:*", xPathContext);
		
		for (int i=0; i<textResult.size(); i++) {
			Element textNode = (Element)textResult.get(i);
			String styleName = textNode.getAttributeValue("style-name", Namespace.TEXT.toUri());
			if (styleName != null) {
				String definedName = paragraphStyleMapping.get(styleName);
				if (definedName != null) {
					textNode.getAttribute("style-name", Namespace.TEXT.toUri()).setValue(definedName);
				}
			}
		}
	}

	public byte[] getPersonalizedTemplate(Paper paper) throws IOException {
		ZipFs zipFs = 
			new ZipFs(
				Thread.currentThread().getContextClassLoader().getResourceAsStream(TEMPLATE));
		Document contentDoc = zipFs.getDocument("content.xml");
		
		injectTitleIntoContent(contentDoc, paper.getTitle());
		injectAuthorsIntoContent(contentDoc, paper.getAuthorsAndAffiliations());
		
		zipFs.putDocument("content.xml", contentDoc);
		
		Document metaDoc = zipFs.getDocument("meta.xml");
		injectTitleIntoMeta(metaDoc, paper.getTitle());
		injectAuthorsIntoMeta(metaDoc, paper.getAuthorsAndAffiliations());
		injectPaperIdIntoMeta(metaDoc, paper.getPaperId());
		
		zipFs.putDocument("meta.xml", metaDoc);
		
		return zipFs.toZipData();
	}

	private void injectPaperIdIntoMeta(Document metaDoc, Integer paperId) {
		Nodes searchResult = 
			metaDoc.query(
				"/office:document-meta/office:meta/meta:user-defined[@meta:name='"
						+CONFTOOLPAPERID_ATTRIBUTENAME+"']", 
				xPathContext);
		
		if (searchResult.size() != 0) {
			for (int i=0; i<searchResult.size(); i++) {
				Node n = searchResult.get(i);
				n.getParent().removeChild(n);
			}
		}
		
		Element confToolPaperIdElement = 
				new Element("meta:user-defined", Namespace.META.toUri());
		confToolPaperIdElement.addAttribute(
			new Attribute("meta:name", Namespace.META.toUri(), CONFTOOLPAPERID_ATTRIBUTENAME));
		confToolPaperIdElement.appendChild(String.valueOf(paperId));
		
		Element metaElement = metaDoc.getRootElement()
				.getFirstChildElement("meta", Namespace.OFFICE.toUri());
		metaElement.appendChild(confToolPaperIdElement);
		
	}

	private void injectAuthorsIntoMeta(Document metaDoc,
			List<String> authorsAndAffiliations) {
		Nodes searchResult = 
				metaDoc.query(
					"/office:document-meta/office:meta/meta:initial-creator", 
					xPathContext);
		Element initialCreatorElement = null;
		if (searchResult.size() > 0) {
			initialCreatorElement = (Element) searchResult.get(0);
			
		}
		else {
			initialCreatorElement = 
				new Element("meta:initial-creator", Namespace.META.toUri());
			Element metaElement = metaDoc.getRootElement()
					.getFirstChildElement("meta", Namespace.OFFICE.toUri());
			metaElement.appendChild(initialCreatorElement);
		}
		
		initialCreatorElement.removeChildren();
		StringBuilder builder = new StringBuilder();
		String conc  = "";
		for (String author : authorsAndAffiliations) {
			builder.append(conc);
			builder.append(author);
			conc = "; ";
		}
		initialCreatorElement.appendChild(builder.toString());
		
		Nodes creatorSearchResult = 
				metaDoc.query(
					"/office:document-meta/office:meta/dc:creator", 
					xPathContext);
		if (creatorSearchResult.size() > 0) {
			creatorSearchResult.get(0).getParent().removeChild(creatorSearchResult.get(0));
		}
	}

	private void injectTitleIntoMeta(Document metaDoc, String title) {
		Nodes searchResult = 
				metaDoc.query(
					"/office:document-meta/office:meta/dc:title", 
					xPathContext);
		Element titleElement = null;
		if (searchResult.size() > 0) {
			titleElement = (Element) searchResult.get(0);
			
		}
		else {
			titleElement = new Element("dc:title", Namespace.DC.toUri());
			Element metaElement = metaDoc.getRootElement()
				.getFirstChildElement("meta", Namespace.OFFICE.toUri());
			metaElement.appendChild(titleElement);
		}
		titleElement.removeChildren();
		titleElement.appendChild(title);
	}

	private void injectAuthorsIntoContent(Document contentDoc,
			List<String> authorsAndAffiliations) throws IOException {
		Nodes searchResult = 
				contentDoc.query(
					"//text:section[@text:name='Authors from ConfTool']", 
					xPathContext);
		
		if (searchResult.size()!=1) {
			throw new IOException(
				"document does not contain exactly one section element "
				+ "for the ConfTool author/affiliation, found: "
				+ searchResult.size());
		}
		
		if (!(searchResult.get(0) instanceof Element)) {
			throw new IllegalStateException(
				"section for ConfTool author/affiliation doesn't seem to be a proper Element");
		}
		
		Element authorSectionElement = (Element) searchResult.get(0);
		
		authorSectionElement.removeChildren();
		for (String authorAffiliation : authorsAndAffiliations){
			Element authorParagraphElement = new Element("p", Namespace.TEXT.toUri());
			authorSectionElement.appendChild(authorParagraphElement);
			authorParagraphElement.appendChild(authorAffiliation);
			authorParagraphElement.addAttribute(
				new Attribute("text:style-name", Namespace.TEXT.toUri(), "P6"));
		}
	}

	private void injectTitleIntoContent(Document contentDoc, String title) throws IOException {
		Nodes searchResult = 
			contentDoc.query(
				"//text:section[@text:name='Title from ConfTool']", 
				xPathContext);
		
		if (searchResult.size()!=1) {
			throw new IOException(
				"document does not contain exactly one section element "
				+ "for the ConfTool title, found: "
				+ searchResult.size());
		}
		
		if (!(searchResult.get(0) instanceof Element)) {
			throw new IllegalStateException(
				"section for ConfTool title doesn't seem to be a proper Element");
		}
		
		Element titleSectionElement = (Element) searchResult.get(0);
		
		titleSectionElement.removeChildren();
		Element titleParagraphElement = new Element("p", Namespace.TEXT.toUri());
		titleSectionElement.appendChild(titleParagraphElement);
		titleParagraphElement.appendChild(title);
		titleParagraphElement.addAttribute(
				new Attribute("text:style-name", Namespace.TEXT.toUri(), "P1"));

	}
	
	@Override
	public String getFileExtension() {
		return Type.ODT.getExtension();
	}
}