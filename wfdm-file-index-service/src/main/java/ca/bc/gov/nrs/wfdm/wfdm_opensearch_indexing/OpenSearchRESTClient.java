package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

/**
 * OpenSeachRESTClient provides access to the OpenSearch Restful API. This is used
 * by the SQS Message processor to push text content and WFDM File resource metadata
 * into the OpenSearch/Elastic service for searching
 */
public class OpenSearchRESTClient {
  // should likely be moved into a config file...
  private static String serviceName = "es";
  private static String region = "ca-central-1";
  private static String domainEndpoint = "add URL here";
  private static String index = "wfdm-opensearch-index";
  private static String id = String.valueOf(System.currentTimeMillis());

  static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();

  /**
   * Adds the provided content and metadata to the OpenSearch index
   * 
   * @param content
   * @param fileName
   * @return
   * @throws IOException
   */
  public IndexResponse addIndex(String content, String fileName, JSONObject fileDetails) throws IOException {
    RestHighLevelClient searchClient = searchClient(serviceName, region);
    String type = "_doc";

    Map<String, Object> document = new HashMap<>();
    document.put("key", fileName);
    document.put("text", content);
    // attach the metadata and security
    document.put("metadata", fileDetails.getJSONArray("metadata").toString());
    document.put("security", fileDetails.getJSONArray("security").toString());

    // Form the indexing request, send it, and print the response
    IndexRequest request = new IndexRequest(index, type, id).source(document);

    return searchClient.index(request, RequestOptions.DEFAULT);
  }

  // Adds the interceptor to the OpenSearch REST client
  private static RestHighLevelClient searchClient(String serviceName, String region) {
    AWS4Signer signer = new AWS4Signer();
    signer.setServiceName(serviceName);
    signer.setRegionName(region);

    HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer,
        credentialsProvider);
    return new RestHighLevelClient(RestClient.builder(HttpHost.create(domainEndpoint))
        .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
  }
}
