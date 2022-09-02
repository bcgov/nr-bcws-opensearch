package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.InputStream;
import java.util.Properties;

import com.adobe.xmp.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(PropertyLoader.class);
	
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