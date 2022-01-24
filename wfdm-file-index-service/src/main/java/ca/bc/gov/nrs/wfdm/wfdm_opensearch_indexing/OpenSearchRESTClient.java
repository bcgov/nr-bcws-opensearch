package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
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

/**
 * OpenSeachRESTClient provides access to the OpenSearch Restful API. This is
 * used by the SQS Message processor to push text content and WFDM File resource
 * metadata into the OpenSearch/Elastic service for searching
 */
public class OpenSearchRESTClient {
	// should likely be moved into a config file...
	private static String serviceName = "es";
	private static String region = "ca-central-1";
	private static String domainEndpoint = "";
	private static String indexName = "";
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
		restClient = searchClient(serviceName, region);
		System.out.println("content" + content + "\n" + fileDetails+"\n status"+scanStatus);

		String type = "_doc";

		Map<String, Object> document = new HashMap<>();
		document.put("key", fileName);
		document.put("absoluteFilePath",fileDetails.getString("filePath"));
		
		if (content != null && !content.isEmpty()) {
			JSONObject jsonObj = new JSONObject(content);
			document.put("fileContent", jsonObj.getString("Text"));
		}

		DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US);
		String lastUpdatedTime =String.valueOf(fileDetails.get("lastUpdatedTimestamp"));
		LocalDateTime localDate = LocalDateTime.parse(lastUpdatedTime, datetimeFormatter);
		document.put("lastModified", fileDetails.get("lastUpdatedTimestamp"));
		
		document.put("lastUpdatedBy", fileDetails.get("lastUpdatedBy"));
		document.put("mimeType",fileDetails.get("mimeType"));
		
		document.put("fileName", fileName);
		document.put("fileRetention", fileDetails.get("retention"));
		
		JSONObject parent = fileDetails.getJSONObject("parent");
		JSONArray parentLinkArray = parent.getJSONArray("links");
		JSONObject parentLinkObj = parentLinkArray.getJSONObject(0);
		document.put("fileLink", parentLinkObj.get("href"));
		document.put("filePath", parent.getString("filePath"));
		
		Integer fileSizeLong = (Integer) fileDetails.get("fileSize");
	    String fileSize =  humanReadableByteCountBin(fileSizeLong);
	    document.put("fileSize", fileSize);
		

		JSONArray metadataArray = filterDataFromFileDetails(fileDetails.getJSONArray("metadata").toString(),
				"metadataName", "metadataValue");
		System.out.println("metadataArray :" + metadataArray);
		document.put("metadata", metadataArray.toString());
		
		JSONArray securityArray = fileDetails.getJSONArray("security");
		JSONArray jsonArray = new JSONArray();
		for (int i = 0; i < securityArray.length(); i++) {
			JSONObject objects = securityArray.getJSONObject(i);
			jsonArray.put(objects.get("securityKey"));

		}
	    
		JSONArray jsonSecurityArray = filterDataFromFileDetails(jsonArray.toString(), "displayLabel", "securityKey");
		System.out.println("jsonSecurityArray :" + jsonSecurityArray);
		document.put("security", jsonSecurityArray.toString());
		
		
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
		
		document.put("securityScope", scopeArray.toString());
	
		
		
		document.put("scanStatus", scanStatus);
		String id = fileDetails.getString("fileId");

		String json;
		ObjectMapper mapper = new ObjectMapper();

		try {
			json = mapper.writeValueAsString(document);
		} catch (JsonProcessingException e) {
			System.out.println("json mapper failed :" +  e);
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
		IndexRequest createRequest = new IndexRequest(indexName, type, id).source(document);

		IndexResponse response = null;
		try {
			response = restClient.index(createRequest, RequestOptions.DEFAULT);
			System.out.println("Response from OpenSearch:" + response.toString());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Close the rest connection
		if (restClient != null) {
			try {
				System.out.println("closing rest connection");
				restClient.close();
				restClient = null;
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}

		return response;
	}

	// Adds the intercepter to the OpenSearch REST client
	public RestHighLevelClient searchClient(String serviceName, String region) {
		AWS4Signer signer = new AWS4Signer();
		signer.setServiceName(serviceName);
		signer.setRegionName(region);
		HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer,
				credentialsProvider);
		RestClientBuilder builder = RestClient.builder(HttpHost.create(domainEndpoint))
				.setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor));

		restClient = new RestHighLevelClient(builder);

		return restClient;
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
