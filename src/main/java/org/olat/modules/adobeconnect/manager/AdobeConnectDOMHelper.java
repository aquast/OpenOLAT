/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.modules.adobeconnect.manager;

import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpEntity;
import org.olat.core.logging.OLog;
import org.olat.core.logging.Tracing;
import org.olat.modules.adobeconnect.model.AdobeConnectError;
import org.olat.modules.adobeconnect.model.AdobeConnectErrorCodes;
import org.olat.modules.adobeconnect.model.AdobeConnectErrors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 
 * Initial date: 23 avr. 2019<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class AdobeConnectDOMHelper {
	
	private static final OLog log = Tracing.createLoggerFor(AdobeConnectDOMHelper.class);
	
	protected  static boolean isStatusOk(HttpEntity entity) {
		try {
			Document doc = getDocumentFromEntity(entity);
			return AdobeConnectDOMHelper.isStatusOk(doc);
		} catch (Exception e) {
			log.error("", e);
			return false;
		}
	}
	
    protected static Document getDocumentFromEntity(HttpEntity entity) throws Exception {
    	try(InputStream in=entity.getContent()) {
	        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	        return dBuilder.parse(in);
    	} catch(Exception e) {
    		throw e;
    	}
    }
	
	protected static final boolean isStatusOk(Document doc) {
		NodeList permissionList = doc.getElementsByTagName("status");
		if(permissionList != null && permissionList.getLength() == 1) {
			Element status = (Element)permissionList.item(0);
			return "ok".equalsIgnoreCase(status.getAttribute("code"));
		}
		return true;
	}
	
	protected static final void error(Document doc, AdobeConnectErrors errors) {
		NodeList permissionList = doc.getElementsByTagName("status");
		if(permissionList != null && permissionList.getLength() == 1) {
			Element status = (Element)permissionList.item(0);
			String code = status.getAttribute("code");
			String subcode = status.getAttribute("subcode");
			
			AdobeConnectError error = new AdobeConnectError();
			error.setCode(AdobeConnectErrorCodes.unkown);
			
			if("invalid".equals(code)) {
				String field = null;
				NodeList invalidList = doc.getElementsByTagName("invalid");
				if(invalidList != null && invalidList.getLength() == 1) {
					Element invalid = (Element)invalidList.item(0);
					subcode = invalid.getAttribute("subcode");
					field = invalid.getAttribute("field");
				}

				if("duplicate".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.duplicateField);
				} else if("format".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.formatError);
				} else if("illegal-operation".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.illegalOperation);
				} else if("missing".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.missingParameter);
				} else if("no-such-item".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.noSuchItem);
				} else if("range".equals(subcode)) {
					error.setCode(AdobeConnectErrorCodes.rangeError);
				}
				
				if(field != null) {
					error.setArguments(new String[] { field });
				}
				
			} else if("no-access".equals(code)) {
				error.setCode(AdobeConnectErrorCodes.noAccessDenied);
			}
			errors.append(error);
		}
	}
	
	protected static void print(Document document) {
		if(log.isDebug()) {
		    try(StringWriter writer = new StringWriter()) {
				Transformer transformer = TransformerFactory.newInstance().newTransformer();
				Source source = new DOMSource(document);
				transformer.transform(source, new StreamResult(writer));
				writer.flush();
				log.info(writer.toString());
			} catch (Exception e) {
				log.error("", e);
			}
		}
	}
}