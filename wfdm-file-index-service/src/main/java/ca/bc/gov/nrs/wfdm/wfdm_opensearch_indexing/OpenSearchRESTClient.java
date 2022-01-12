package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
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
import org.json.JSONObject;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenSeachRESTClient provides access to the OpenSearch Restful API. This is used
 * by the SQS Message processor to push text content and WFDM File resource metadata
 * into the OpenSearch/Elastic service for searching
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
   * @return
   * 
   * @throws IOException
   */
	public IndexResponse addIndex(String content, String fileName, JSONObject fileDetails) throws IOException {
		restClient = searchClient(serviceName, region);

		String type = "_doc";

		Map<String, Object> document = new HashMap<>();
		document.put("key", fileName);
		document.put("text", content);
		document.put("fileName", fileName);
		document.put("metadata", fileDetails.getJSONArray("metadata").toString());
		document.put("security", fileDetails.getJSONArray("security").toString());
		String id = fileDetails.getString("fileId");

		String json;
		ObjectMapper mapper = new ObjectMapper();

		try {
			json = mapper.writeValueAsString(document);
		} catch (JsonProcessingException e) {
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
  
  
//Adds the intercepter to the OpenSearch REST client
	public  RestHighLevelClient searchClient(String serviceName, String region) {
		AWS4Signer signer = new AWS4Signer();
		signer.setServiceName(serviceName);
		signer.setRegionName(region);
		HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer,
				credentialsProvider);
		RestClientBuilder builder = RestClient
				.builder(HttpHost.create(domainEndpoint))
				.setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor));
		
		restClient = new RestHighLevelClient(builder);
	
		return restClient;
	}
}

