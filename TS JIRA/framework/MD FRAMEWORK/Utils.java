package com.optum.coe.automation.rally;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

import org.apache.http.HttpEntity;

public class Utils {
	
	// Logger Initialization for Utils Class
	
	private static final Logger logger = LogManager.getLogger();
	
/* Create a method to establish a Jira connection.This method have two String arguments as "url" and "apiKey"
 * ClosableHttpClient class is used to perform this operation instead HttpClient, so that a separate method is not required to close the connection each time.
 * This method returns CloseableHttpClient's object instance once connection is established */
	
	public static HttpEntity getJiraResponse(String url, String apiKey) {
			
		CloseableHttpClient connection = HttpClients.createDefault();
		HttpGet request = new HttpGet(url);
		request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Accept", "application/json");
        HttpResponse response = null;
		try {
			response = connection.execute(request);
		} catch (ClientProtocolException e) {
			logger.error("Error occurred in Jira connection" , e);
		} catch (IOException e) {
			logger.error("Error occurred in Jira connection" , e);
		}
        HttpEntity entity = response.getEntity();
		if (entity != null) {
			logger.info("Successfully returned HttpEntity response");
			return entity;
		} else {
			
			logger.error("Error occurred. HttpEntity is null and no respone is recevied.");
			return null;
			
		}

	}

	
	 /* Check if the Jira folder structure is available in Rally
	 *      a. If folder structure is not available in rally, then create the same Jira folder structure in rally for the testcase 
	 *      b. If folder structure is available in rally, no action is required */
	
	public static JsonObject createTestFolder(String[] folderHierarchy, String projectRef, String rallyBaseUrl, String rallyApiKey) {
        JsonObject lastFolder = null;
        String lastFolderRef = null;

        RallyRestApi restApi = null;
        try {
            restApi = new RallyRestApi(new URI(rallyBaseUrl), rallyApiKey);
            restApi.setApplicationName("CreateTestCaseApp");

            for (int i = 0; i < folderHierarchy.length; i++) {
                String folderName = folderHierarchy[i];
                if (folderName == null || folderName.trim().isEmpty()) {
                    logger.info("Invalid folder name encountered: '" + folderName + "'");
                    continue;
                }

                // If it's the top-level folder, ensure it is created as a parent folder
                if (i == 0) {
                    // Check if the folder exists as a parent folder
                    QueryRequest parentFolderExistenceRequest = new QueryRequest("testfolder");
                    parentFolderExistenceRequest.setQueryFilter(new QueryFilter("Name", "=", folderName.trim()).and(new QueryFilter("Parent", "=", "null")));
                    parentFolderExistenceRequest.setFetch(new Fetch("_ref", "Name", "Parent"));

                    QueryResponse parentQueryResponse = restApi.query(parentFolderExistenceRequest);

                    if (parentQueryResponse.wasSuccessful() && parentQueryResponse.getTotalResultCount() > 0) {
                        // Folder exists as a parent folder
                        lastFolder = parentQueryResponse.getResults().get(0).getAsJsonObject();
                        lastFolderRef = lastFolder.get("_ref").getAsString();
                        logger.info("Parent folder already exists: " + lastFolderRef);
                    } else {
                        // Folder does not exist as a parent folder, create it
                        JsonObject newFolder = new JsonObject();
                        newFolder.addProperty("Name", folderName.trim());
                        newFolder.addProperty("Project", projectRef);

                        CreateRequest createFolderRequest = new CreateRequest("testfolder", newFolder);
                        CreateResponse createFolderResponse = restApi.create(createFolderRequest);

                        if (createFolderResponse.wasSuccessful()) {
                            lastFolderRef = createFolderResponse.getObject().get("_ref").getAsString();
                            newFolder.addProperty("_ref", lastFolderRef);
                            lastFolder = newFolder;
                            logger.info("Successfully created parent folder: " + lastFolderRef);
                        } else {
                            logger.error("Error occurred creating parent folder.");
                            for (String error : createFolderResponse.getErrors()) {
                                System.out.println(error);
                            }
                            break;
                        }
                    }
                } else {
                    // For subfolders, check and create under the last folder
                    QueryRequest subFolderExistenceRequest = new QueryRequest("testfolder");
                    subFolderExistenceRequest.setQueryFilter(new QueryFilter("Name", "=", folderName.trim()).and(new QueryFilter("Parent", "=", lastFolderRef)));
                    subFolderExistenceRequest.setFetch(new Fetch("_ref", "Name", "Parent"));

                    QueryResponse subQueryResponse = restApi.query(subFolderExistenceRequest);

                    if (subQueryResponse.wasSuccessful() && subQueryResponse.getTotalResultCount() > 0) {
                        // Folder exists as a subfolder
                        lastFolder = subQueryResponse.getResults().get(0).getAsJsonObject();
                        lastFolderRef = lastFolder.get("_ref").getAsString();
                        logger.info("Subfolder already exists: " + lastFolderRef);
                    } else {
                        // Folder does not exist, create it as a subfolder
                        JsonObject newFolder = new JsonObject();
                        newFolder.addProperty("Name", folderName.trim());
                        newFolder.addProperty("Project", projectRef);
                        newFolder.addProperty("Parent", lastFolderRef);

                        CreateRequest createFolderRequest = new CreateRequest("testfolder", newFolder);
                        CreateResponse createFolderResponse = restApi.create(createFolderRequest);

                        if (createFolderResponse.wasSuccessful()) {
                            lastFolderRef = createFolderResponse.getObject().get("_ref").getAsString();
                            newFolder.addProperty("_ref", lastFolderRef);
                            lastFolder = newFolder;
                            logger.info("Successfully created subfolder: " + lastFolderRef);
                        } else {
                            logger.error("Error occurred creating subfolder");
                            for (String error : createFolderResponse.getErrors()) {
                                System.out.println(error);
                            }
                            break;
                        }
                    }
                }
            }

            return lastFolder;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (restApi != null) {
                try {
                    restApi.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

// Implementation to update the TestCase Migrated in Jira to "true". User story US7382197

	public void updateTestCaseMigratedStatusinJira(boolean status) {
			
				
			
		}


}

	
	
	

