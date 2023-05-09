package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	static RestHighLevelClient restClient;

	static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();

	/**
	 * Adds the provided content and metadata to the OpenSearch index
	 * 
	 * @param content
	 * @param fileName
	 * @param scanStatus 
	 * @return
	 * 
	 * @throws IOException
	 */
	public IndexResponse addIndex(String content, String fileName, JSONObject fileDetails, String scanStatus) throws IOException {
		String indexName = System.getenv("WFDM_DOCUMENT_OPENSEARCH_INDEXNAME").trim();
		restClient = searchClient(serviceName, region);
		
		if(restClient == null) {
			logger.info("rest client is null");
		}
		logger.info("content" + content + "\n" + fileDetails+"\n status"+scanStatus);

		String type = "_doc";

		Map<String, Object> document = new HashMap<>();
		document.put("key", fileName);
		document.put("absoluteFilePath",fileDetails.getString("filePath"));
		
		if(!fileDetails.isNull("lastUpdatedTimestamp"))
			document.put("lastModified", fileDetails.get("lastUpdatedTimestamp"));
		else
			document.put("lastModified", null);
		
		if(!fileDetails.isNull("lastUpdatedBy"))
			document.put("lastUpdatedBy", fileDetails.get("lastUpdatedBy"));
		else
			document.put("lastUpdatedBy", null);
		
		//Directories/Folders will not have a mime type and it needs to be set to "" to be processed 
		if (fileDetails.get("mimeType") == "null") {
			document.put("mimeType", "DIRECTORY");
		} else {
			document.put("mimeType", fileDetails.get("mimeType").toString() );
		}
		
		if (fileDetails.get("fileType") != "null"  ){
			document.put("fileType", fileDetails.get("fileType").toString());
		}

		document.put("fileName", fileName);


		if(!fileDetails.isNull("retention"))
			document.put("fileRetention", fileDetails.get("retention"));
		else
			document.put("fileRetention", null);

		if (content != null && !content.isEmpty()) {
				JSONObject jsonObj = new JSONObject(content);
				document.put("fileContent", jsonObj.getString("Text"));
		}
		
		JSONObject parent = fileDetails.getJSONObject("parent");
		JSONArray parentLinkArray = parent.getJSONArray("links");
		JSONObject parentLinkObj = parentLinkArray.getJSONObject(0);
		document.put("fileLink", parentLinkObj.get("href"));
		document.put("filePath", parent.getString("filePath"));
		
		if (!fileDetails.isNull("fileSize")) {
			Integer fileSizeLong = (Integer) fileDetails.get("fileSize");
			String fileSize =  humanReadableByteCountBin(fileSizeLong);
			document.put("fileSize", fileSize);
		} else {
			document.put("fileSize", 0);
		}

	    JSONArray metadataArray = filterDataFromFileDetailsMeta(fileDetails.getJSONArray("metadata").toString(),
				"metadataName", "metadataValue");



	    ArrayList<Map<String, Object>> metadataList = new ArrayList<>();
	    JSONObject jsonOb = new JSONObject();
	    for(int i= 0 ; i < metadataArray.length() ; i++) {
	    	Map<String, Object> metadataKeyVal = new HashMap<>();
	    	jsonOb = metadataArray.getJSONObject(i);
	    	metadataKeyVal.put("metadataName", jsonOb.get("metadataName"));
			metadataKeyVal.put("metadataValue", jsonOb.get("metadataValue"));
			
			// Currently the dates that are passed in do not qualify for the date format the index is using, will have to be addressed later
		//	if (jsonOb.has("metadataDateValue") && jsonOb.get("metadataDateValue") != null) {
		//		metadataKeyVal.put("metadataDateValue", jsonOb.get("metadataDateValue"));
		//	}

			if (jsonOb.has("metadataBooleanValue") && jsonOb.get("metadataBooleanValue") != null) {
				metadataKeyVal.put("metadataBooleanValue", jsonOb.get("metadataBooleanValue"));
			}

			if (jsonOb.has("metadataNumberValue") && jsonOb.get("metadataNumberValue") != null) {
				metadataKeyVal.put("metadataNumberValue", jsonOb.get("metadataNumberValue"));
			}

	    	metadataList.add(metadataKeyVal);
	    }
	    document.put("metadata", metadataList);
	    
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
		document.put("security", securityList);
		
		
		
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
				
		document.put("securityScope", securityScopeList);
	
		document.put("scanStatus", scanStatus);
		String id = fileDetails.getString("fileId");
		

		String json;
		ObjectMapper mapper = new ObjectMapper();

		try {
			json = mapper.writeValueAsString(document);
		} catch (JsonProcessingException e) {
			logger.error("json mapper failed :" +  e);
			throw new ElasticsearchException("JSON?????", e);
		}

		IndexRequest indexRequest = new IndexRequest(indexName, type, id);
		Set<String> keys1 = document.keySet();
		XContentBuilder builder1 = jsonBuilder().startObject();
		for (String key : keys1) {
			builder1.field(key, document.get(key));
		}

		builder1.endObject();
		indexRequest.source(builder1);

		Set<String> keys = document.keySet();
		XContentBuilder builder = jsonBuilder().startObject();
		for (String key : keys) {
			builder.field(key, document.get(key));
		}
		builder.endObject();

		UpdateRequest updateRequest = new UpdateRequest();
		updateRequest.index(indexName);
		updateRequest.type(type);
		updateRequest.id(id);
		updateRequest.doc(builder);
		updateRequest.upsert(indexRequest);

		// Form the indexing request, send it, and print the response
		logger.info("adding data into index"+indexName);
		IndexRequest createRequest = new IndexRequest(indexName, type, id).source(document);
		logger.info("createRequest");
		logger.info(createRequest.getDescription());


		IndexResponse response = null;
		try {
			response = restClient.index(createRequest, RequestOptions.DEFAULT);
			logger.info("Response:"+response);
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				restClient.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return response;
	}

	// Adds the intercepter to the OpenSearch REST client
	public RestHighLevelClient searchClient(String serviceName, String region) {
		AWS4Signer signer = new AWS4Signer();
		String domainEndpoint = System.getenv("WFDM_DOCUMENT_OPENSEARCH_DOMAIN_ENDPOINT").trim();
		logger.info(domainEndpoint);
		signer.setServiceName(serviceName);
		signer.setRegionName(region);
		HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer,
				credentialsProvider);
		RestClientBuilder builder = RestClient.builder(HttpHost.create(domainEndpoint))
				.setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor));

		restClient = new RestHighLevelClient(builder);

		return restClient;
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
	
	
}
