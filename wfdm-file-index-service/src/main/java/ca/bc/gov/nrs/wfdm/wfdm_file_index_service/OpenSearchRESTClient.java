package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

/**
 * OpenSeachRESTClient provides access to the OpenSearch Restful API. This is
 * used by the SQS Message processor to push text content and WFDM File resource
 * metadata into the OpenSearch/Elastic service for searching
 */
public class OpenSearchRESTClient {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenSearchRESTClient.class);
	
	// should likely be moved into a config file...
	private static String serviceName = "es";
	private static String region = "ca-central-1";
	private static String units = "BKMGTPEZY";
	private OpenSearchClient openSearchClient;
	private SdkHttpClient httpClient;

	/**
	 * Adds the provided content and metadata to the OpenSearch index
	 * 
	 * @param content
	 * @param fileName
	 * @param scanStatus 
	 * @return
	 * 
	 * @throws OpenSearchException
	 */
	public IndexResponse addIndex(String content, String fileName, JSONObject fileDetails, String scanStatus) throws OpenSearchException {
		String indexName = System.getenv("WFDM_DOCUMENT_OPENSEARCH_INDEXNAME").trim();
		String domainEndpoint = System.getenv("WFDM_DOCUMENT_OPENSEARCH_DOMAIN_ENDPOINT").trim();
		logger.info(domainEndpoint);
		openSearchClient = openSearchClient(domainEndpoint, serviceName, Region.CA_CENTRAL_1);
		
		if (openSearchClient == null) {
			logger.info("open search client is null");
		}
		logger.info("content" + content + "\n" + fileDetails+"\n status"+scanStatus);

		SearchDocumentResultsDto searchDocumentResultsDto = new SearchDocumentResultsDto();
		
		searchDocumentResultsDto.setKey(fileName);
		searchDocumentResultsDto.setAbsoluteFilePath(fileDetails.getString("filePath"));
		
		if(!fileDetails.isNull("lastUpdatedTimestamp")) {
			searchDocumentResultsDto.setLastModified(fileDetails.get("lastUpdatedTimestamp").toString());
		}

		if(!fileDetails.isNull("uploadedBy")) {
			searchDocumentResultsDto.setUploadedBy(fileDetails.get("uploadedBy").toString());
		}
		
		if(!fileDetails.isNull("lastUpdatedBy")) {
			searchDocumentResultsDto.setLastUpdatedBy(fileDetails.get("lastUpdatedBy").toString());
		}
		
		//Directories/Folders will not have a mime type and it needs to be set to "" to be processed 
		if (fileDetails.get("mimeType") == "null") {
			searchDocumentResultsDto.setMimeType("DIRECTORY");
		} else {
			searchDocumentResultsDto.setMimeType(fileDetails.get("mimeType").toString());
		}
		
		if (fileDetails.get("fileType") != "null"  ){
			searchDocumentResultsDto.setFileType(fileDetails.get("fileType").toString());
		}

		searchDocumentResultsDto.setFileName(fileName);
		
		if (!fileDetails.isNull("fileExtension")) {
			searchDocumentResultsDto.setFileExtension(fileDetails.get("fileExtension").toString());
		} else if (fileName.contains(".")) {
			String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
			searchDocumentResultsDto.setFileExtension(fileExtension);
		}

		if(!fileDetails.isNull("retention")) {
			searchDocumentResultsDto.setFileRetention(fileDetails.get("retention").toString());
		}

		if (content != null && !content.isEmpty()) {
			JSONObject jsonObj = new JSONObject(content);
			searchDocumentResultsDto.setFileContent(jsonObj.getString("Text"));
		}
		
		JSONObject parent = fileDetails.getJSONObject("parent");
		JSONArray parentLinkArray = parent.getJSONArray("links");
		JSONObject parentLinkObj = parentLinkArray.getJSONObject(0);
		searchDocumentResultsDto.setFileLink(parentLinkObj.get("href").toString());
		searchDocumentResultsDto.setFilePath(parent.getString("filePath"));
		
		if (!fileDetails.isNull("fileSize")) {
			Long fileSizeLong = fileDetails.getLong("fileSize");
			String fileSize =  humanReadableByteCountBin(fileSizeLong.longValue());
			searchDocumentResultsDto.setFileSize(fileSize);
		} else {
			searchDocumentResultsDto.setFileSize(String.valueOf(0));
		}

		searchDocumentResultsDto.setFileSizeBytes(parsetoBytes(searchDocumentResultsDto.getFileSize()));

		JSONArray metadataArray = filterDataFromFileDetailsMeta(fileDetails.getJSONArray("metadata").toString(), 
				"metadataName", "metadataValue");

		ArrayList<Map<String, Object>> metadataList = new ArrayList<>();
		JSONObject jsonOb = new JSONObject();
		for (int i = 0 ; i < metadataArray.length() ; i++) {
			Map<String, Object> metadataKeyVal = new HashMap<>();
			jsonOb = metadataArray.getJSONObject(i);
			metadataKeyVal.put("metadataName", jsonOb.get("metadataName"));
			metadataKeyVal.put("metadataValue", jsonOb.get("metadataValue"));
	
			if (jsonOb.has("metadataDateValue") && jsonOb.get("metadataDateValue") != null) {
			  // alter date string into an opensearch strict_date_optional_time format
			  // example: “2019-03-23T21:34:46”
	
			  String dateValue = jsonOb.get("metadataDateValue").toString();
			  dateValue = dateValue.replace(" ", "T");
			  metadataKeyVal.put("metadataDateValue", dateValue);
			}
	
			if (jsonOb.has("metadataBooleanValue") && jsonOb.get("metadataBooleanValue") != null) {
			  metadataKeyVal.put("metadataBooleanValue", jsonOb.get("metadataBooleanValue"));
			}
	
			if (jsonOb.has("metadataNumberValue") && jsonOb.get("metadataNumberValue") != null) {
			  metadataKeyVal.put("metadataNumberValue", jsonOb.get("metadataNumberValue"));
			}
	
			metadataList.add(metadataKeyVal);
		}
		
		searchDocumentResultsDto.setMetadata(metadataList);
	    
	  	JSONArray securityArray = fileDetails.getJSONArray("security");
		JSONArray jsonArray = new JSONArray();
		for (int i = 0; i < securityArray.length(); i++) {
			JSONObject objects = securityArray.getJSONObject(i);
			jsonArray.put(objects.get("securityKey"));
		}

		JSONArray jsonSecurityArray = filterDataFromFileDetails(jsonArray.toString(), "displayLabel", "securityKey");
		ArrayList<Map<String, Object>> securityList = new ArrayList<>();
		for (int i = 0; i < jsonSecurityArray.length(); i++) {
			Map<String, Object> securityKeyVal = new HashMap<>();
			jsonOb = jsonSecurityArray.getJSONObject(i);
			securityKeyVal.put("displayLabel", jsonOb.get("displayLabel"));
			securityKeyVal.put("securityKey", jsonOb.get("securityKey"));
			securityList.add(securityKeyVal);
		}
		
		searchDocumentResultsDto.setSecurity(securityList);
		
		JSONArray scopeArray = new JSONArray();
		for (int i = 0; i < securityArray.length(); i++) {
			JSONObject objects = securityArray.getJSONObject(i);
			JSONObject scopeObj = new JSONObject() ;
			jsonArray.put(objects.get("securityKey"));
			scopeObj.put("Read", objects.get("readAccessInd"));
			scopeObj.put("Write", objects.get("grantorAccessInd"));
			scopeObj.put("displayLabel", jsonArray.toString());
			JSONObject jsobObjects = filterSecurityScope(scopeObj);
			scopeArray.put(jsobObjects);
		}
		
		ArrayList<Map<String, Object>> securityScopeList = new ArrayList<>();
		for (int i = 0; i < scopeArray.length(); i++) {
			Map<String, Object> securityScopeKeyVal = new HashMap<>();
			jsonOb = scopeArray.getJSONObject(i);
			securityScopeKeyVal.put("displayLabel", jsonOb.get("displayLabel"));
			securityScopeKeyVal.put("canReadorWrite", jsonOb.getBoolean("canReadorWrite"));
			securityScopeList.add(securityScopeKeyVal);
		}
				
		searchDocumentResultsDto.setSecurityScope(securityScopeList);
		
		searchDocumentResultsDto.setScanStatus(scanStatus);

		String id = fileDetails.getString("fileId");
		searchDocumentResultsDto.setFileId(id);

		String json;
		ObjectMapper mapper = new ObjectMapper();

		try {
			json = mapper.writeValueAsString(searchDocumentResultsDto);
		} catch (JsonProcessingException e) {
			logger.error("json mapper failed: {}", e.getMessage());
			throw new RuntimeException("json mapper failed to convert index data to json: ", e);
		}
		
		// Form the indexing request, send it, and print the response
		logger.info("adding data into index" + indexName);
		IndexRequest<SearchDocumentResultsDto> indexRequest = new IndexRequest.Builder<SearchDocumentResultsDto>()
						.index(indexName).id(id).document(searchDocumentResultsDto).build();
		logger.info("create indexRequest");
		logger.info(indexRequest.toString());

		IndexResponse response = null;
		try {
			response = openSearchClient.index(indexRequest);
			logger.info("Response:" + response);
		} catch (Exception e) {
			logger.error("Error indexing document into open search: {}", e.getMessage());
			throw new OpenSearchException(e);
		} finally {
			if (openSearchClient != null) {
				try {
					logger.debug("Closing open search connection");
					if (httpClient != null) {
						httpClient.close();
						httpClient = null;
					}
					openSearchClient = null;
				} catch (Exception e) {
					logger.error("Error closing open search connection: {}", e.getMessage());
				}
			}
		}
		
		return response;
	}
	
	public OpenSearchClient openSearchClient(String openSearchEndpoint, String serviceName, Region region) {
		httpClient = ApacheHttpClient.builder().build();
		OpenSearchClient client = new OpenSearchClient(
				new AwsSdk2Transport(
						httpClient, 
						openSearchEndpoint, 
						serviceName, 
						region, 
						AwsSdk2TransportOptions.builder().build())
		);
		
		return client;
	}

	private static JSONArray filterDataFromFileDetailsMeta(String jsonarray, String metadataName, String metadataValue) {

		JSONArray jsonArray = new JSONArray(jsonarray);
		JSONArray jArray = new JSONArray();
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject json = jsonArray.getJSONObject(i);
			JSONObject jobject = new JSONObject();
			jobject.put(metadataName, json.getString(metadataName));

			if (json.has("metadataType")) {
				switch (json.getString("metadataType")) {
					case "BOOLEAN":
						jobject.put("metadataBooleanValue", json.getString(metadataValue));
						break;
					case "NUMBER":
						jobject.put("metadataNumberValue", json.getString(metadataValue));
						break;
					case "DATE":
						jobject.put("metadataDateValue", json.getString(metadataValue));
						break;
				}
			}
			// setting a default metaDataValue so a string version is  always available for defaults
			jobject.put(metadataValue, json.getString(metadataValue));
			jArray.put(jobject);
		}
		

		return jArray;
	}

	private static JSONArray filterDataFromFileDetails(String jsonarray, String metadataName, String metadataValue) {

		JSONArray jsonArray = new JSONArray(jsonarray);
		JSONArray jArray = new JSONArray();
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject json = jsonArray.getJSONObject(i);
			JSONObject jobject = new JSONObject();
			jobject.put(metadataName, json.getString(metadataName));
			jobject.put(metadataValue, json.getString(metadataValue));
			jArray.put(jobject);

		}
		return jArray;

	}
	



	private static JSONObject filterSecurityScope(JSONObject scopeObj) {
		JSONObject jobject = new JSONObject();
		boolean canRead = scopeObj.getBoolean("Read");
		boolean canWrite = scopeObj.getBoolean("Write");
		if (canRead || canWrite) {
			jobject.put("canReadorWrite", "true");
		} else {
			jobject.put("canReadorWrite", "false");
		}

		JSONArray jsonArray = new JSONArray(scopeObj.getString("displayLabel"));
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject json = jsonArray.getJSONObject(i);
			jobject.put("displayLabel", json.getString("displayLabel"));
		}

		return jobject;

	}
	
	
	
	//Copied from stackoverflow
	public static String humanReadableByteCountBin(long bytes) {
	    long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
	    if (absB < 1024) {
	        return bytes + " B";
	    }
	    long value = absB;
	    CharacterIterator ci = new StringCharacterIterator("KMGTPE");
	    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
	        value >>= 10;
	        ci.next();
	    }
	    value *= Long.signum(bytes);
	    return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}

	// Converting file size back to bytes from human readable
	public static long parsetoBytes(String arg0) {
		if (arg0.equals("0")) {
			return Long.valueOf(0);
		}
		int spaceNdx = arg0.indexOf(" ");
		if (spaceNdx < 0) {
			return Long.valueOf(arg0);
		}
		double ret = Double.parseDouble(arg0.substring(0, spaceNdx));
		String unitString = arg0.substring(spaceNdx + 1);
		int unitChar = unitString.charAt(0);
		int power = units.indexOf(unitChar);
		boolean isSi = unitString.indexOf('i') != -1;
		int factor = 1024;
		if (isSi) {
			factor = 1000;
		}

		return Double.valueOf(ret * Math.pow(factor, power)).longValue();
	}
	
}
