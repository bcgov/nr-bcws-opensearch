package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.adobe.xmp.impl.Utils;

public class PropertyLoader {

	static final Logger logger = LogManager.getLogger(PropertyLoader.class);

	
	//This method is no longer required, the properties are moved from
	//properties file to Lambda->config->Environment variables
	public static String getProperty(String key) {
		ClassLoader classLoader = Utils.class.getClassLoader();

		try {
			InputStream is = classLoader.getResourceAsStream("wfdm-api-config.properties");
			Properties prop = new Properties();
			prop.load(is);
			return prop.getProperty(key);
		} catch (Exception e) {
			return null;
		}
	}
}