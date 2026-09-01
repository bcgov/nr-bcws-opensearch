package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import com.mashape.unirest.http.HttpResponse;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.xml.sax.SAXException;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.mashape.unirest.http.exceptions.UnirestException;

/**
 * Processor for the received SQS messages. As messages are placed onto the Queue
 * they'll be pulled by this handler. The message should be a WFDM fileID. This file
 * will then be fetched from WFDM. The file will be parsed by Tika, and the parsed
 * text and some metadata will be pushed into the OpenSearch store
 * 
 * Once this process is complete, this handler will place a message on another Queue
 * that will instruct the ClamAV lambda to execute
 */
public class ProcessSQSMessage implements RequestHandler<Map<String,Object>, String> {
  private static String region = "ca-central-1";
  static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
  private static final String MIME_TYPE = "mimeType";
  private static final String FILE_VERSION_NUMBER = "fileVersionNumber";
  private static final String MESSAGE = "message";

  protected String getBucketName() {
    return System.getenv("WFDM_DOCUMENT_CLAMAV_S3BUCKET");
  }

  protected String getSecretManagerName() {
    return System.getenv("WFDM_DOCUMENT_SECRET_MANAGER");
  }

  protected OpenSearchRESTClient createOpenSearchClient() {
    return new OpenSearchRESTClient();
  }

  protected String retrieveSecret(String secretName) {
    return RetrieveSecret.RetrieveSecretValue(secretName);
  }

  protected String getAccessToken(String clientId, String password)
      throws Exception {
    return GetFileFromWFDMAPI.getAccessToken(clientId, password);
  }

  protected HttpResponse<String> getFileInformation(
      String token,
      String fileId)
      throws Exception {
    return GetFileFromWFDMAPI.getFileInformation(token, fileId);
  }

  protected boolean setIndexedMetadata(
      String token,
      String fileId,
      String versionNumber,
      JSONObject fileDetailsJson,
      String etag)
      throws Exception {
    return GetFileFromWFDMAPI.setIndexedMetadata(
        token,
        fileId,
        versionNumber,
        fileDetailsJson,
        etag);
  }

  private static class RequestInfo {
    String versionNumber;
    String eventType;
    String scanStatus;
  }

  @Override
  public String handleRequest(Map<String, Object> event, Context context) {
    LambdaLogger logger = context.getLogger();
    
    // null check sqsEvents!
    if (event == null) {
      logger.log("\nInfo: No messages to handle\nInfo: Closeing");
      return "";
    }

    String bucketName = getBucketName().trim();

    BufferedInputStream stream = null;
    try {
      // messageBody is the complete file resource
      logger.log("\nInfo: Event Received on WFDM -open-search: " + event);
      JSONObject fileDetailsJson = new JSONObject(event);

      if (fileDetailsJson.has("body-json")) {
          fileDetailsJson = fileDetailsJson.getJSONObject("body-json");
      }

      logger.log("fileDetailsJson" + fileDetailsJson.getString("fileId"));

      String fileId = fileDetailsJson.getString("fileId");

      RequestInfo requestInfo = extractRequestInfo(fileDetailsJson, logger);

      String versionNumber = requestInfo.versionNumber;
      String eventType = requestInfo.eventType;
      String scanStatus = requestInfo.scanStatus;

      // Should come for preferences, Client ID and secret for authentication with WFDM
      logger.log(eventType);
      String wfdmToken = retrieveWFDMToken();

      logger.log("wfdmToken :" + wfdmToken);

      // attempt to fetch the file from WFDM, as a verification that the file actually exists
      HttpResponse<String> fileResponse =  getFileInformation(wfdmToken, fileId);

      if (fileResponse == null) {
        throw new Exception("File not found!");
      } 

      logger.log("\nInfo: fileResponse.getBody() is: " + fileResponse.getBody());

      String fileInfo = fileResponse.getBody();
      String etag = fileResponse.getHeaders().getFirst("ETag");

      // replace the passed-in file details with the details fetched
      fileDetailsJson = new JSONObject(fileInfo);

      logger.log("\nInfo: File found on WFDM: " + fileInfo);

      String content = "";
      // if this is a "bytes" event, we need to pull the bytes from
      // the s3 bucket. ClamAV process will be finished now.
      if (eventType.equalsIgnoreCase("bytes")) {
        // Fetch the bytes from the bucket, not the WFDM API
        AmazonS3 s3client = AmazonS3ClientBuilder
            .standard()
            .withCredentials(credentialsProvider)
            .withRegion(region)
            .build();

        Bucket clamavBucket = getClamavBucket(s3client, bucketName);

        logger.log("\nInfo: Fetching file bytes...");

        S3Object scannedObject = s3client.getObject(new GetObjectRequest(bucketName, fileId + "-" + versionNumber));
        stream = new BufferedInputStream(scannedObject.getObjectContent());

        // Tika Time! (If Necessary, check mime types)
        logger.log("\nInfo: Tika Parser...");
        logger.log("\nInfo: S3 object key: " + fileId + "-" + versionNumber);

        logger.log("\nInfo: S3 content length: "  + scannedObject.getObjectMetadata().getContentLength());

        logger.log("\nInfo: MIME type from WFDM: " + fileDetailsJson.optString("mimeType"));

        content = parseFileContent(stream, fileDetailsJson, logger);

        // We've finished with the file, delete the file from the s3 Bucket
        s3client.deleteObject(new DeleteObjectRequest(clamavBucket.getName(), fileId + "-" + versionNumber));
      }

      // Push content and File meta up to our Opensearch Index
      logger.log("\nInfo: Indexing with OpenSearch...");
      String fileName = getFileName(fileDetailsJson);

      OpenSearchRESTClient restClient = createOpenSearchClient();

      // We are disabling indexing of files with a security classification of Protected B or Protected C
      boolean skipIndexing = shouldSkipIndexing(fileDetailsJson);

      if (!skipIndexing) {
        processIndexing(restClient, content, fileName, fileDetailsJson,
            scanStatus, logger, wfdmToken, fileId, versionNumber, etag);
      }
      
    } catch (UnirestException | TransformerConfigurationException | SAXException e) {
      logger.log("\nError: Failure to recieve file from WFDM" + e.getLocalizedMessage());
    } catch (TikaException tex) {

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        tex.printStackTrace(pw);

        logger.log(sw.toString());
    }catch (OpenSearchException e) {
      logger.log("\nOpen Search Error: " + e.getLocalizedMessage());
    } catch (Exception ex) {
      logger.log("\nUnhandled Error: " + ex.getLocalizedMessage());
    } finally {
      logger.log("\nInfo: Finalizing processing...");
      cleanupStream(stream, logger);
    }

    logger.log("\nInfo: Close Handler");
    return "Closed";
  }

    // Explicit timeouts set to avoid hanging on stale connections.
    // Default ApacheHttpClient has a 30s socket timeout with no idle connection cleanup,
    // which causes SocketTimeoutExceptions when Lambda reuses a warm instance with a
    // dead connection to OpenSearch.
    protected void addIndexWithRetry(OpenSearchRESTClient restClient, String content, String fileName,
                                    JSONObject fileDetailsJson, String scanStatus, LambdaLogger logger) throws OpenSearchException {
      int maxRetries = 3;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          restClient.addIndex(content, fileName, fileDetailsJson, scanStatus);
          return;   
        } catch (OpenSearchException e) {
          logger.log("\nWarn: OpenSearch addIndex attempt " + attempt + " of " + maxRetries
              + " failed: " + e.getLocalizedMessage());
          if (attempt == maxRetries) throw e;
          try {
            Thread.sleep(2000L * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OpenSearchException(ie);
          }
        }
      }
  }

  private boolean shouldSkipIndexing(JSONObject fileDetailsJson) {

    JSONArray metaArray = fileDetailsJson.getJSONArray("metadata");

    for (int i = 0; i < metaArray.length(); i++) {

      JSONObject metadata = metaArray.getJSONObject(i);

      String metadataName = metadata.getString("metadataName");
      String metadataValue = metadata.getString("metadataValue");

      if (metadataName.equals("SecurityClassification")
          && ("Protected B".equals(metadataValue)
              || "Protected C".equals(metadataValue))) {
        return true;
      }
    }

    return false;
  }

  private RequestInfo extractRequestInfo(JSONObject fileDetailsJson, LambdaLogger logger) {
    RequestInfo requestInfo = new RequestInfo();

    if (fileDetailsJson.has(FILE_VERSION_NUMBER)) {
      if (fileDetailsJson.getString(FILE_VERSION_NUMBER).equals("null")) {
        requestInfo.versionNumber = "1";
      } else {
        requestInfo.versionNumber =
            fileDetailsJson.getString(FILE_VERSION_NUMBER);
      }
    } else {
      requestInfo.versionNumber = "1";
    }

    // TODO: Update to correct event type from WFDM-API
    if (fileDetailsJson.has("eventType")) {
      requestInfo.eventType = fileDetailsJson.getString("eventType");
    } else {
      requestInfo.eventType = "meta";

      logger.log("\nInfo: eventType key/value was not found, setting eventType to: " + requestInfo.eventType);
    }

    if (fileDetailsJson.has(MESSAGE) && !fileDetailsJson.isNull(MESSAGE)) {
      requestInfo.scanStatus = fileDetailsJson.getString(MESSAGE);
    } else {
      requestInfo.scanStatus = "-";
    }

    return requestInfo;
  }

  private String retrieveWFDMToken() throws Exception {
    String wfdmSecretName = getSecretManagerName().trim();

    String secret = retrieveSecret(wfdmSecretName);

    String[] secretCD = StringUtils.substringsBetween(secret, "\"", "\"");

    String clientId = secretCD[0];
    String password = secretCD[1];

    String wfdmToken = getAccessToken(clientId, password);

    if (wfdmToken == null) {
      throw new Exception("Could not authorize access for WFDM");
    }

    return wfdmToken;
  }
  
  private void processIndexing(OpenSearchRESTClient restClient, String content, String fileName,
      JSONObject fileDetailsJson, String scanStatus, LambdaLogger logger,
      String wfdmToken, String fileId, String versionNumber, String etag)
      throws Exception {

    addIndexWithRetry(restClient, content, fileName, fileDetailsJson, scanStatus, logger);

    logger.log("\nInfo: File parsing complete. Schedule ClamAV scan.");

    boolean metaAdded = setIndexedMetadata(
        wfdmToken, fileId, versionNumber, fileDetailsJson, etag);

    if (!metaAdded) {
      logger.log("\nERROR: Failed to add metadata to file resource");
    }

    HttpResponse<String> fileResponse =
        getFileInformation(wfdmToken, fileId);

    addIndexWithRetry(restClient, content, fileName,
        new JSONObject(fileResponse.getBody()), scanStatus,logger);
  }

  private Bucket getClamavBucket(AmazonS3 s3client, String bucketName)
      throws Exception {

    List<Bucket> buckets = s3client.listBuckets();

    for (Bucket bucket : buckets) {
      if (bucket.getName().equalsIgnoreCase(bucketName)) {
        return bucket;
      }
    }

    throw new Exception(
        "S3 Bucket " + bucketName + " does not exist. Virus scan will be skipped");
  }

  private boolean isSupportedMimeType(String mimeType) {
    return mimeType.equalsIgnoreCase("text/plain")
        || mimeType.equalsIgnoreCase("application/msword")
        || mimeType.equalsIgnoreCase(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        || mimeType.equalsIgnoreCase("application/pdf")
        || mimeType.equalsIgnoreCase(
            "application/vnd.ms-excel.sheet.macroEnabled.12")
        || mimeType.equalsIgnoreCase(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        || mimeType.equalsIgnoreCase(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
  }

  private String parseFileContent(
      BufferedInputStream stream,
      JSONObject fileDetailsJson,
      LambdaLogger logger)
      throws TransformerConfigurationException,
            IOException,
            SAXException,
            TikaException {

    String mimeType = fileDetailsJson.getString(MIME_TYPE);

    if (!isSupportedMimeType(mimeType)) {
      logger.log(
          "\nInfo: Mime type of "
              + mimeType
              + " is not processed for OpenSearch. Skipping Tika parse.");

      return "";
    }

    String content = TikaParseDocument.parseStream(stream, mimeType);

    logger.log("\nInfo: content after parsing " + content);

    return content;
  }

  private String getFileName(JSONObject fileDetailsJson) {
    String filePath = fileDetailsJson.getString("filePath");

    return filePath.substring(filePath.lastIndexOf("/") + 1);
  }

  private void cleanupStream(
      BufferedInputStream stream,
      LambdaLogger logger) {

    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        logger.log("\nError: File stream cleanup failed: "
            + e.getLocalizedMessage());
      }
    }
  }

}
