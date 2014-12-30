package org.adho.dhconvalidator.conversion.input.docx.paragraphparser;

import org.adho.dhconvalidator.conversion.input.docx.DocxInputConverter.Namespace;
import org.adho.dhconvalidator.util.DocumentUtil;

import nu.xom.Document;
import nu.xom.Element;
import nu.xom.XPathContext;

public class SeekPermStartHandler implements StateHandler {

	@Override
	public State handleParagraph(Element matchElement, Document document,
			XPathContext xPathContext) {
		
		if (matchElement.getFirstChildElement("permStart", Namespace.MAIN.toUri()) != null) {
			return State.INPERM;
		}
		else if (DocumentUtil.hasMatch(matchElement, "w:pPr/w:pStyle[@w:val='DH-BibliographyHeading']", xPathContext)
				&& (DocumentUtil.hasMatch(matchElement, "w:r", xPathContext))) {
			return State.SEEKPERMSTART;
		}
		else {
			matchElement.getParent().removeChild(matchElement);
			return State.SEEKPERMSTART;
		}
	}

}