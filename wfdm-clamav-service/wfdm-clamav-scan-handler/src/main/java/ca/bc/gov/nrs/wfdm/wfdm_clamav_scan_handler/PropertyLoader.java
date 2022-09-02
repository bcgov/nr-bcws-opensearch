package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import java.io.InputStream;
import java.util.Properties;

import com.adobe.xmp.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyLoader {

	private static final Logger logger = LoggerFactory.getLogger(PropertyLoader.class);

	public static String getProperty(String key) {
		ClassLoader classLoader = Utils.class.getClassLoader();

		try {
			InputStream is = classLoader.getResourceAsStream("wfdm-file-index-config.properties");
			Properties prop = new Properties();
			prop.load(is);
			return prop.getProperty(key);
		} catch (Exception e) {
			return null;
		}
	}
}