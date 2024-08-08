package com.optum.coe.automation.rally;



import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;




public class JiraTestStep {
	// Logger Initialization for JiraTestStep Class
	private static final Logger logger = LogManager.getLogger();
	private int id;
	private String description;
	private String expectedResult;
	private int index;
	private String testData;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id=id;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description=description;
	}
	public String getExpectedresult() {
		return expectedResult;
	}
	public void setExpectedresult(String expectedResult) {
		this.expectedResult=expectedResult;
	}
	
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index=index;
	}
	public String getTestdata() {
		return testData;
	}
	public void setTestdata(String testData) {
		this.testData=testData;
	}
		
	
}
