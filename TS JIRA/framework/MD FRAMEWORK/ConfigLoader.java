package com.optum.coe.automation.rally;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


// This Class will help to load and retrieve the config values from .properties file

public class ConfigLoader {

	// Logger Initialization for ConfigLoader Class
	private static final Logger logger = LogManager.getLogger();
	
	// Config file location 	
	private static String CONFIG_FILE_LOCATION = Paths.get("").toAbsolutePath().toString() + "/resources/rally_migration_config.properties";	
	
	private static Properties properties = new Properties(); // Initialization of a object for Properties class. This variable will be used to load the config file 
	
	//Create a static block to load the properties file. 
	//Since we are using Static block, the code will be executed one time while class is loaded first time in JVM.
	
	static {
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(CONFIG_FILE_LOCATION);  
		} catch (FileNotFoundException e) {
			logger.error("The config file is not found." + CONFIG_FILE_LOCATION + "is not available", e);
			
		}
		
		try {
			properties.load(fis); // Load the config file value
		} catch (IOException e) {
			logger.error("The config file is not loaded successfully." + CONFIG_FILE_LOCATION + " is the location", e);
		}
    }
	
	// A Method to get the Config value. This method accepts the key for the value available in Config file. For Example: RALLY_BASE_URL is the key and this method will return https://rally1.rallydev.com
	
	public static String getConfigValue(String key) {
		
		return properties.getProperty(key); // Return statement for the method
		
	}

}
