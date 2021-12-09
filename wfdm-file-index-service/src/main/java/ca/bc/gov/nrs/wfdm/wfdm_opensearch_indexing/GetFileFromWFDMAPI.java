package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.transform.TransformerConfigurationException;

import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class GetFileFromWFDMAPI {
	// TODO:move to propeties file
	private static final String BASE_URL = "<Enter base URL here>";
	private static final String WFDM_URL = "<Enter WFDM URL here>";;

	static Properties proFile;

	public void getAccessToken(String fileID) throws Exception {
		String accessToken = getAccessToken();
		String fileInformation = getFileInformation(accessToken, fileID);
	}

	private static String getAccessToken() {
		HttpResponse<JsonNode> httpResponse;
		JSONObject responseBody = null;
		String CLIENT_ID = "Clinet ID Goes Here";
		String PASSWORD = "Password Goes Here";

		try {
			httpResponse = Unirest.get(BASE_URL).basicAuth(CLIENT_ID, PASSWORD).asJson();
			responseBody = httpResponse.getBody().getObject();
		} catch (UnirestException e) {
			e.printStackTrace();
		}
		
		return (String) responseBody.get("access_token");
	}

	private static String getFileInformation(String accessToken, String fileId)
			throws UnirestException, TransformerConfigurationException, SAXException {

		HttpResponse<String> detailsResponse;
		String detailsJson = null;
		try {
			detailsResponse = Unirest.get(WFDM_URL + fileId).header("Authorization", "Bearer " + accessToken)
					.header("Content-Type", "application/json").asString();
			detailsJson = detailsResponse.getBody();
			JSONObject jsonObj = new JSONObject(detailsJson);
			String filePath = jsonObj.getString("filePath");
			String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
			// TOD0:Add more mimetype
			// get file bytes if its a text/pdf
			if (jsonObj.get("mimeType").equals("text/plain") || jsonObj.get("mimeType").equals("application/msword")
					|| jsonObj.get("mimeType").equals("application/msword")
					|| jsonObj.get("fileExtension").equals("docx")
					|| jsonObj.get("mimeType").equals("application/pdf")) {
				getFileBytes(accessToken, fileId, fileName);
			}

		} catch (UnirestException e) {
			e.printStackTrace();
		}
		return detailsJson;

	}

	private static byte[] getFileBytes(String accessToken, String fileId,String fileName) throws UnirestException, TransformerConfigurationException, SAXException {
		System.out.println("Get file bytes for file:" + fileId + accessToken+" "+fileName);
		byte[] fileBytes = null;
		HttpResponse<InputStream> bytesResponse;
		try {
			bytesResponse = Unirest.get(WFDM_URL + fileId + "/bytes").header("Accept", "*/*")
					.header("Authorization", "Bearer " + accessToken).asBinary();
			BufferedInputStream BIStream = new BufferedInputStream(bytesResponse.getBody());
			TikaParseDocument tikaParser = new TikaParseDocument();
			tikaParser.ParseDocument(BIStream,fileName);
			fileBytes = readFully(BIStream).toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fileBytes;

	}

	private static ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length = 0;
		while ((length = inputStream.read(buffer)) != -1) {
			baos.write(buffer, 0, length);
		}
		return baos;
	}

	//Properties file not working in AWS-
	//Need more research
	private static void readPropertiesFromFile() {
		try (InputStream propertyFile = new FileInputStream("src/main/resources/wfdm-api-config.properties")) {
			proFile = new Properties();
			proFile.load(propertyFile);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
