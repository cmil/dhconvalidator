package org.adho.dhconvalidator.conftool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;

import org.adho.dhconvalidator.properties.PropertyKey;
import org.adho.dhconvalidator.util.DocumentUtil;
import org.restlet.Context;
import org.restlet.data.Method;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

public class ConfToolClient {
	private static final boolean LOGIN_SUCCESS = true;
	public static final class AuthenticationException extends Exception {

		public AuthenticationException() {
			super();
		}

		public AuthenticationException(String message) {
			super(message);
		}
	}
	
	private char[] restSharedPass;
	private String confToolUrl;

	public ConfToolClient(String confToolUrl, char[] restSharedPass) {
		this.confToolUrl = confToolUrl;
		this.restSharedPass = restSharedPass;
	}
	
	public ConfToolClient() {
		this(
			PropertyKey.conftool_url.getValue(),
			PropertyKey.conftool_shared_pass.getValue().toCharArray());
	}
	
	private String getPassHash(String nonce) {
		return Hashing.sha256().hashString(
			nonce+new String(restSharedPass), Charsets.UTF_8).toString();
	}
	
	public String getDetails(String user) throws IOException {
		String nonce = getNonce();
		
		StringBuilder urlBuilder = new StringBuilder(confToolUrl);
		urlBuilder.append("?page=remoteLogin"); //$NON-NLS-1$
		urlBuilder.append("&nonce="); //$NON-NLS-1$
		urlBuilder.append(nonce);
		urlBuilder.append("&passhash="); //$NON-NLS-1$
		urlBuilder.append(getPassHash(nonce));
		urlBuilder.append("&user="); //$NON-NLS-1$
		urlBuilder.append(user);
		urlBuilder.append("&command=request"); //$NON-NLS-1$
		
		ClientResource client = 
				new ClientResource(Context.getCurrent(), Method.GET, urlBuilder.toString());
		
		Representation result = client.get();

		try (InputStream resultStream = result.getStream()) {
			Builder builder = new Builder();
			Document resultDoc = builder.build(resultStream);
			return resultDoc.toXML();
		}
		catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	public User getDetailedUser(User loginUser) throws IOException {
		User author = new DocumentToUserMapper().getUser(
				getExportData(ExportType.users, loginUser));
		if (author != null) {
			loginUser.setFirstName(author.getFirstName());
			loginUser.setLastName(author.getLastName());
		}
		return loginUser;
	}
	
	public List<Paper> getPapers(User user) throws IOException {
		return new DocumentToPaperMapper().getPaperList(
				getExportData(ExportType.papers, user));
	}

	public Paper getPaper(User user, Integer paperId) throws IOException {
		List<Paper> papers = getPapers(user);
		if (papers != null) {
			for (Paper paper : papers) {
				if (paper.getPaperId().equals(paperId)) {
					return paper;
				}
			}
		}
		return null;
	}

	
	private String getNonce() {
		Date date = new Date(new Date().getTime()*60);
		return String.valueOf(date.getTime());
	}
	
	private Document getExportData(ExportType type, User user) throws IOException {
		String nonce = getNonce();
		
		StringBuilder urlBuilder = new StringBuilder(confToolUrl);
		urlBuilder.append("?page=adminExport"); //$NON-NLS-1$
		urlBuilder.append("&nonce="); //$NON-NLS-1$
		urlBuilder.append(nonce);
		urlBuilder.append("&passhash="); //$NON-NLS-1$
		urlBuilder.append(getPassHash(nonce));
		urlBuilder.append("&export_select="); //$NON-NLS-1$
		urlBuilder.append(type.name());
		urlBuilder.append("&form_include_deleted=0"); //$NON-NLS-1$
		urlBuilder.append("&form_export_format=xml"); //$NON-NLS-1$
		urlBuilder.append("&form_export_header=default"); //$NON-NLS-1$
		urlBuilder.append("&cmd_create_export=true"); //$NON-NLS-1$
		
		if (user != null) {
			urlBuilder.append("&form_userID="); //$NON-NLS-1$
			urlBuilder.append(user.getUserId());
		}
		
		ClientResource client = 
				new ClientResource(Context.getCurrent(), Method.GET, urlBuilder.toString());
		
		Representation result = client.get();
		
		try (InputStream resultStream = result.getStream()) {
			Builder builder = new Builder();
			Document resultDoc = builder.build(resultStream);
			return resultDoc;
		}
		catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	public User authenticate(String user, char[] pass) 
			throws IOException, AuthenticationException {
		String nonce = getNonce();
		
		StringBuilder urlBuilder = new StringBuilder(confToolUrl);
		urlBuilder.append("?page=remoteLogin"); //$NON-NLS-1$
		urlBuilder.append("&nonce="); //$NON-NLS-1$
		urlBuilder.append(nonce);
		urlBuilder.append("&passhash="); //$NON-NLS-1$
		urlBuilder.append(getPassHash(nonce));
		urlBuilder.append("&user="); //$NON-NLS-1$
		urlBuilder.append(user);
		urlBuilder.append("&command=login"); //$NON-NLS-1$
		urlBuilder.append("&password="); //$NON-NLS-1$
		urlBuilder.append(pass);
		
		ClientResource client = 
				new ClientResource(Context.getCurrent(), Method.GET, urlBuilder.toString());
		
		Representation result = client.get();

		try (InputStream resultStream = result.getStream()) {
			Builder builder = new Builder();
			Document resultDoc = builder.build(resultStream);
			if (getLoginResult(resultDoc) == LOGIN_SUCCESS) {
				return getUser(resultDoc);
			}
			else {
				throw new AuthenticationException(getMessage(resultDoc));
			}
		}
		catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	private String getUserId(Document resultDoc) {
		Element resultElement = DocumentUtil.getFirstMatch(resultDoc, "/login/id"); //$NON-NLS-1$
		return resultElement.getValue();
	}	
	
	private String getUserName(Document resultDoc) {
		Element resultElement = DocumentUtil.getFirstMatch(resultDoc, "/login/username"); //$NON-NLS-1$
		return resultElement.getValue();
	}
	
	private User getUser(Document resultDoc) {
		return new User(Integer.valueOf(getUserId(resultDoc)), getUserName(resultDoc));
	}
	
	private boolean getLoginResult(Document resultDoc) {
		Element resultElement = DocumentUtil.getFirstMatch(resultDoc, "/login/result"); //$NON-NLS-1$
		return Boolean.valueOf(resultElement.getValue());
	}
	
	private String getMessage(Document resultDoc) {
		Element resultElement = DocumentUtil.getFirstMatch(resultDoc, "/login/message"); //$NON-NLS-1$
		return resultElement.getValue();
	}
}
