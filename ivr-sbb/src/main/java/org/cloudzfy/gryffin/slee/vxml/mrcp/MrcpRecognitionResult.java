/*
 * gryffin-as, IVR Media Resource Controller in JAIN SLEE
 * Copyright (C) 2013, Cloudzfy
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cloudzfy.gryffin.slee.vxml.mrcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.jvoicexml.RecognitionResult;
import org.jvoicexml.xml.srgs.ModeType;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl;

public class MrcpRecognitionResult implements RecognitionResult {

	private static Logger logger = Logger.getLogger(MrcpRecognitionResult.class);
	
	private static String separater = " ";
	private boolean isAccepted = false;;
	private String text;
	private float confidence;
	private ModeType modeType = null;
	private String[] words;
	private float[] wordsConfidence;
	private String markname;
	private ScriptableObject interpretation;
	
	public MrcpRecognitionResult(String doc) {
		ClassLoader loader = DocumentBuilderFactoryImpl.class.getClassLoader();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", loader);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(new InputSource(new ByteArrayInputStream(doc.getBytes())));
			this.isAccepted = true;
			if(this.modeType == null) {
				this.modeType = ModeType.VOICE;
			}
			if(doc.isEmpty()) {
				this.isAccepted = false;
			}
			else {
				words = new String[0];
				wordsConfidence = new float[0];
				Node resultN = document.getFirstChild();
				NodeList interpretations = resultN.getChildNodes();
				float confidenceF = (float) 0.0;
				int markI = -1;
				for(int i=0; i<interpretations.getLength(); i++) {
					Node child = interpretations.item(i);
					if(child.getNodeType() == Node.ELEMENT_NODE) {
						Element element = (Element) child;
						String confidence = element.getAttribute("confidence");
						if(confidence != null && confidence != "") {
							float tmp = new Float(confidence)/100;
							if(tmp > confidenceF) {
								confidenceF = tmp;
								markI = i;
							}
						}
					}
					
				}
				if(markI != -1) {
					readChild(interpretations.item(markI), (float)1.0);
				}
				if(words.length > 0) {
					StringBuilder result = new StringBuilder();
					result.append(words[0]);
					for(int i=1; i<words.length; i++) {
						result.append(separater);
						result.append(words[i]);
					}
					this.text = result.toString();
					this.confidence = 0;
					for(int i=0; i<wordsConfidence.length; i++) {
						this.confidence += wordsConfidence[i];
					}
					this.confidence = this.confidence / wordsConfidence.length;
				} else {
					this.text = "";
				}
			}
		} catch (ParserConfigurationException e) {
			logger.error("Unable to config Parser. ", e);
		} catch (SAXException e) {
			logger.error("Unable to parse given Document. ", e);
		} catch (IOException e) {
			logger.error("Unable to parse given Document. ", e);
		}
		
	}
	
	private void readChild(Node parent, float conf) {
		NodeList children = parent.getChildNodes();
		for(int i=0; i<children.getLength(); i++) {
			Node child = children.item(i);
			if(child.getNodeType() == Node.TEXT_NODE) {
				Text textNode = (Text)child;
				String[] currentWords = textNode.getData().trim().split(" ");
				if(currentWords[0].isEmpty()) {
					continue;
				}
				String[] wordsTmp = new String[words.length + currentWords.length];
				float[] wordsConfidenceTmp = new float[words.length + currentWords.length];
				for(int j=0; j<words.length; j++) {
					wordsTmp[j] = words[j];
					wordsConfidenceTmp[j] = wordsConfidence[j];
				}
				
				for(String word : currentWords) {
					wordsConfidenceTmp[words.length] = conf;
					wordsTmp[words.length] = word;
				}
				words = wordsTmp;
				wordsConfidence = wordsConfidenceTmp;
			}
			else if(child.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element)child;
				if(element.getTagName().equals("input")) {
					String mode = element.getAttribute("mode");
					if(mode != null && mode != "") {
						if(mode.equals("speech")) {
							this.modeType = ModeType.VOICE;
						}
						else if(mode.equals("dtmf")) {
							this.modeType = ModeType.DTMF;
						}
					}
					String confidence = element.getAttribute("confidence");
					float currentConf;
					if(confidence != null && confidence != "") {
						currentConf = new Float(confidence)/100;
					} else {
						currentConf = conf;
					}
					readChild(element, currentConf);
				}
				else if(element.getTagName().equals("noinput")) {
					this.isAccepted = false;
				}
				else if(element.getTagName().equals("nomatch")) {
					this.isAccepted = false;
				}
			}
		}
	}
	
	@Override
	public float getConfidence() {
		return this.confidence;
	}

	@Override
	public String getMark() {
		return this.markname;
	}

	@Override
	public ModeType getMode() {
		return this.modeType;
	}

	@Override
	public Object getSemanticInterpretation() {
		return this.interpretation;
	}

	@Override
	public String getUtterance() {
		return this.text;
	}

	@Override
	public String[] getWords() {
		return words;
	}

	@Override
	public float[] getWordsConfidence() {
		return this.wordsConfidence;
	}

	@Override
	public boolean isAccepted() {
		return this.isAccepted;
	}

	@Override
	public boolean isRejected() {
		return !this.isAccepted;
	}

	@Override
	public void setMark(String markname) {
		this.markname = markname;
	}

}
