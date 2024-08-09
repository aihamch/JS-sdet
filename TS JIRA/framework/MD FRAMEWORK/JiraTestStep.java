package com.optum.coe.automation.rally;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.List;

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
	
	
	 private String input;
	    
	    private int stepIndex;
	    
	
	 private List<JiraAttachment> attachments;

	    public String getInput() {
	        return input;
	    }

	    public void setInput(String input) {
	        this.input = input;
	    }

	    public String getExpectedResult() {
	        return expectedResult;
	    }

	    public void setExpectedResult(String expectedResult) {
	        this.expectedResult = expectedResult;
	    }

	    public int getStepIndex() {
	        return stepIndex;
	    }

	    public void setStepIndex(int stepIndex) {
	        this.stepIndex = stepIndex;
	    }

	    public List<JiraAttachment> getAttachments() {
	        return attachments;
	    }

	    public void setAttachments(List<JiraAttachment> attachments) {
	        this.attachments = attachments;}
	
	    // Method to extract embedded image URLs from the description
	    public List<String> getEmbeddedImageUrls() {
	        List<String> imageUrls = new ArrayList<>();

	        if (description != null) {
	            // Regular expression to match URLs (simplified version)
	            String urlRegex = "(https?://[^\\s]+\\.(?:jpg|jpeg|png|gif))";
	            Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
	            Matcher matcher = pattern.matcher(description);

	            while (matcher.find()) {
	                imageUrls.add(matcher.group());
	            }
	        }

	        return imageUrls;
	    }
	
	
	
	
	
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
