package chart.controller;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.*;

@Controller
public class ChartController {
	
	Logger logger = LoggerFactory.getLogger(ChartController.class);
	
	@Value("${app.url}")
	private String endpointSelector;

	@GetMapping("/chart")
	public String hello(@RequestParam(value = "name", defaultValue = "ULNEW100KA") String name, Model model) {
		model.addAttribute("name",name);
		String selector = new String();
		String selectorKey = new String();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			logger.info("Received request : " + name);
			// invoke service selector
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_XML);
			
			//generate request body
			String requestBody = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:pac=\"http://indosatooredoo.com/ngssp/schemas/packageselector\">\r\n"
					+ "   <soapenv:Header/>\r\n"
					+ "   <soapenv:Body>\r\n"
					+ "      <pac:NgsspPackageSelectorRequest>\r\n"
					+ "         <pac:keyName>String</pac:keyName>\r\n"
					+ "         <pac:keyValue>"+ name +"</pac:keyValue>\r\n"
					+ "      </pac:NgsspPackageSelectorRequest>\r\n"
					+ "   </soapenv:Body>\r\n"
					+ "</soapenv:Envelope>";
			HttpEntity<String> request = new HttpEntity<String>(requestBody, headers);
			RestTemplate resttemplate = new RestTemplate();
			logger.info("invoke service selector : " + request.getBody() + " to : " + endpointSelector);
			ResponseEntity<String> postSelector = resttemplate.postForEntity(endpointSelector, request, String.class);
			logger.info("received response with http response code : " + postSelector.getStatusCodeValue());
			
			// reading xml by DOM
			DocumentBuilder builder = factory.newDocumentBuilder();
			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(postSelector.getBody()));
			//Document doc = builder.parse("ULNEW100KA" + ".xml"); //getting file xml in same folder
			Document doc = builder.parse(is);
			
			// variable
			String vRuleId = new String();
			String vValues = new String();
			String vDetails = new String();
			String vParent = new String();
			String branchID = new String();
			int hasRule = 0;
			int hasDifferentDetail = 0;
			selector = doc.getElementsByTagName("CoherenceKey").item(0).getTextContent().trim();
			selectorKey = doc.getElementsByTagName("NgsspPackageKey").item(0).getTextContent().trim();
			
			//new logic
			List<String> rules = new ArrayList<>();
			HashMap<String, String> listDetail = new HashMap<String, String>();
			
			// set selector element
			List<ChartData> lcd = new ArrayList<ChartData>();
			ChartData setCD = SetChartData("", selector + "-" + selectorKey, selector);
			lcd.add(setCD);
			
			//initial process
			JSONObject jChart = new JSONObject();
			Map<String,String> mapTitle = new HashMap<String, String>();
			mapTitle.put("text", "Chart - " + selector);
			jChart.put("type", "tree");
			jChart.put("title", mapTitle);
			//selector = doc.getElementsByTagName("NgsspPackageKey").item(0).getTextContent().trim();
			
			logger.info("Reading the selector");
			// start reading variant rule
			NodeList nList = doc.getElementsByTagName("NgsspVariantRule");
			logger.info("Length of variant rule : " + nList.getLength());
			for (int i = 0; i < nList.getLength(); i++) {
				Integer indexRoot = i;
				Node l = nList.item(i);
				if (l.getNodeType()==Node.ELEMENT_NODE) {
					Element e = (Element) l;
					vValues = "";
					vDetails = "";
					vParent = "";
					branchID = "";
					hasRule = 0;
					hasDifferentDetail = 0;
					NodeList cList = e.getChildNodes();
					int counter = 0;
					int parentCounter = 0;
					for (int j = 0; j < cList.getLength(); j++) {
						Node n = cList.item(j);
						// getting RuleId
						if (n.getNodeName().equalsIgnoreCase("RuleId")) {
							vRuleId = n.getTextContent();
//							if (vRuleId.length() > 16) {
//								vRuleId = vRuleId.substring(0, 17) + "\n" + vRuleId.substring(15, vRuleId.length());
//							}
						}
						if (n.getNodeName()=="NgsspRule" && n.getNodeType()==Node.ELEMENT_NODE) {
							counter++;
							if (indexRoot == 0) {
								parentCounter = parentCounter++;
							}
							Element eRule = (Element) n;
								
								String subType = eRule.getElementsByTagName("Type").item(0).getTextContent();
								String Operation = eRule.getElementsByTagName("Operation").item(0).getTextContent();
								String subValue = eRule.getElementsByTagName("Values").item(0).getTextContent();
								Node n1 = eRule.getElementsByTagName("Values").item(0);
								NodeList cnl = n1.getChildNodes();
								
								vValues = "";
								vDetails = "";
								String vNextDetail = new String();
								for (int k = 0; k < cnl.getLength(); k++) {
									if (cnl.item(k).getTextContent().trim() != "") {
										if (vValues.length() == 0) {
											vValues = cnl.item(k).getTextContent().trim();
										} else {
											//vValues = vValues; // + "," + cnl.item(k).getTextContent().trim();
											vDetails = (vDetails.isEmpty() ? vValues + " \n ": vDetails) + " " + cnl.item(k).getTextContent().trim();
											vNextDetail = (vNextDetail.isEmpty() ? vValues : vNextDetail) + " " + cnl.item(k).getTextContent().trim();
											if (vNextDetail.length() > 20 & cnl.getLength() < 31) {
												vNextDetail = "";
												vDetails = vDetails + " \n ";
											} else if (vNextDetail.length() > 210) {
												vNextDetail = "";
												vDetails = vDetails + " \n ";
											}
										}
									}
								}
								
								String vBranch = subType + " " + vValues;
								vParent = branchID.isBlank() ? selector : branchID;
								String vName = subType + " " + Operation + " " + (vDetails.isEmpty() ? vValues : vDetails);
								if (subType.equals("SUBSCRIBER_TYPE")) {
									vName = vValues;
								}
								
								if (listDetail.containsKey(vBranch)) {
									if (!listDetail.get(vBranch).equals(vDetails)) {
										hasDifferentDetail = 1;
										vBranch = subType + " Others";
										vName = vBranch;
										//System.out.println("has different 0 : " + vBranch + "-" + listDetail.get(vBranch));
										//System.out.println("has different 1 : " + hasDifferentDetail + "-" + vDetails);
									}										
								} else if (vDetails.length() > 0){
									listDetail.put(vBranch, vDetails);
									//System.out.println("listDetail : " + listDetail.get(vBranch) + "-" + vDetails);
								}
								
								// selection node
								branchID = vParent + vBranch;
								if (indexRoot == 0) {
									rules.add(branchID);
									ChartData setChild = SetChartData(vParent, vName, branchID);
									lcd.add(setChild);
								} else {
									hasRule = 0;
									for (String rule : rules) {
										if (rule.equals(branchID)) {
											hasRule = 1;
										}
										//System.out.println("rule : " + indexRoot + "-" + rule + " - " + vBranch);
									}
									if (hasRule == 0) {
										rules.add(branchID);
										ChartData setChild = SetChartData(vParent, vName, branchID);
										lcd.add(setChild);
									}
								}
								
						}
						if (cList.getLength()-1 == j) {
							ChartData setChild = SetChartData(branchID, vRuleId, vRuleId +indexRoot);
							lcd.add(setChild);
						}
					}
				}
				
				
			}
			JSONArray jaData = new JSONArray(lcd);
			jChart.put("series", jaData);
			model.addAttribute("ChartData", lcd);
			logger.info("Chart data : " + jaData);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "hello";
	}
	
	private static final ChartData SetChartData(String vParent, String vName, String vId) {
		ChartData cd = new ChartData();
			cd.setId(vId);
			cd.setName(vName);
			cd.setParent(vParent);
			cd.setValue("10");
		return cd;
	}
}
