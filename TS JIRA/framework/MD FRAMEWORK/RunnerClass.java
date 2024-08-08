package com.optum.coe.automation.rally;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class RunnerClass {

	// Logger Initialization for Runner Class
	private static final Logger logger = LogManager.getLogger();

	// Main method
	public static void main(String[] args) throws MalformedURLException, IOException, URISyntaxException {
		
	    /* Main method calls below functionalities from com.optum.coe.automation.rally package
	     * 1. Get Jira non migrated testcase keys 
	     * 2. Get Jira Testcase details for the given testcase key. It is an iterative process
	     * 3. Create the testcase in Rally using the Jira testcase details
	     * 4. Validate if the testcase is created successfully ; Future implementation is required. US7440061*/
				
		Gson gson=new Gson();
		JiraTestCase jiraTestCase = new JiraTestCase();
		JiraOperation jiraOperation = new JiraOperation();
		JiraTestStep steps=new JiraTestStep();
		ArrayList<String> testcaseKeys = jiraOperation.getJiraNonMigratedTestcaseKeys();
		for (int i = 0 ; i < testcaseKeys.size() ; i++) {
			jiraTestCase.setKey(testcaseKeys.get(i));
			logger.info("Processing " + jiraTestCase.getKey());
			JsonObject jiraTestcaseJson = jiraOperation.getJiraTestCaseDetails(jiraTestCase.getKey());
			RallyOperation rallyOperation = new RallyOperation();
			boolean rallyTestcaseCreationStatus= rallyOperation.createRallyTestcase(jiraTestcaseJson);
			
			String rallyTestcaseOID = rallyOperation.getRallyTestCaseOID();
			
			
			
					JsonArray stepsArray=jiraTestcaseJson.getAsJsonObject("testScript").getAsJsonArray("steps");			
					List<JiraTestStep> testSteps=new ArrayList<>();				
					 for(JsonElement element : stepsArray) {
						JiraTestStep step =gson.fromJson(element, JiraTestStep.class);
						testSteps.add(step);
						
						rallyOperation.createTestStep(step);
						}
					/* boolean isDescending = true;
					 for(int j=1; j<testSteps.size();j++) {
						 if(testSteps.get(j-1).getIndex() <testSteps.get(j).getIndex()) {
							 isDescending = false;
							 break;
						 }
					 }
					
						 Collections.reverse(testSteps);
					
					 if(rallyTestcaseOID!=null) {
						 rallyOperation.createTestStep(steps);
					
					 }*/
					
					 			
			
			/* Needs to be added calling methods for TestStep, Attachments, etc in future iterations. 
			 * US7266086 - For Test Step
			 * US7132986 - For Attachment ( Not Embedded )*/
						
			if (rallyTestcaseCreationStatus == true ) {
				System.out.println("Rally Testcase Creation Status is true" );
				// call the method to update the TestCase Migrated in Jira to "true". this method should go to Utils.Java Class
				//Utils.updateTestCaseMigratedStatusinJira(true); // Yet to be implemented - US7382197
			
				
			} else { 
			    
				logger.error("The Jira testcase is not created in rally. Jira Testcase key is " + jiraTestCase.getKey() + " is not created in rally");
				return;
			}
			
		}
		
		
		
		

	

	}

}
