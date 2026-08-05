package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.HttpResponse;

/**
 * Processor for the received SQS messages. As messages are placed onto the Queue
 * they'll be pulled by this handler. The message should be a WFDM file Resource,
 * and a message type of "BYTES" or "META". 
 * For "BYTES" messages, this file will then be fetched from WFDM, and pushed onto
 * the clamAV bucket for virus scanning. This process will have a handler that can
 * then trigger the tika parsing.
 * For "META" messages, the Indexer lambda will be triggered directly, with no bytes
 * and that lambda will only update metadata to the opensearch index
 */
public class ProcessSQSMessage implements RequestHandler<SQSEvent, SQSBatchResponse> {
  private static String region = "ca-central-1";
  static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
  private static final String MIME_TYPE = "mimeType";
  private static final String FILE_SIZE = "fileSize";

  static boolean isValidFileId(String fileId) {
    return fileId.chars().allMatch(Character::isDigit);
  }

  static String getEventType(JSONObject messageDetails) {
    if (messageDetails.has("eventType")) {
      return messageDetails.getString("eventType");
    }

    return "meta";
  }

  static boolean isHeicOrHeif(String fileExtension) {
    return fileExtension.equals("HEIC") || fileExtension.equals("HEIF");
  }

  static String getMimeType(JSONObject fileDetailsJson) {
    if (fileDetailsJson.has(MIME_TYPE)) {
      return fileDetailsJson.get(MIME_TYPE).toString();
    }

    return "";
  }

  static String getFileExtension(JSONObject fileDetailsJson) {
    if (fileDetailsJson.has("fileExtension")) {
      return fileDetailsJson.get("fileExtension").toString().toUpperCase();
    }

    return "";
  }

  static boolean isFileTooLargeToConvert(JSONObject fileDetailsJson) {
    if (!fileDetailsJson.has(FILE_SIZE)) {
      return false;
    }

    int fileSize = Integer.parseInt(fileDetailsJson.get(FILE_SIZE).toString());

    return fileSize > 10000000;
  }

  static boolean shouldAbortImageConversion(boolean fileTooLargeToConvert, boolean isHeicOrHeif) {
    return fileTooLargeToConvert && isHeicOrHeif;
  }

  static boolean shouldInvokeImageConverter( boolean fileTooLargeToConvert, boolean isHeicOrHeif) {
    return isHeicOrHeif && !fileTooLargeToConvert;
  }

  static AWSLambda createLambdaClient() {
      return AWSLambdaClient.builder()
              .withRegion(region)
              .build();
  }

  static String getSecretManagerName() {
    return System.getenv("WFDM_DOCUMENT_SECRET_MANAGER");
  }

  static String getImageConverterLambdaName() {
    return System.getenv(
            "WFDM_IMAGE_CONVERTER_LAMBDA_NAME");
  }

  static String getIndexingLambdaName() {
    return System.getenv("WFDM_INDEXING_LAMBDA_NAME");
  }

  static AmazonS3 createS3Client() {
    return AmazonS3ClientBuilder
            .standard()
            .withCredentials(credentialsProvider)
            .withRegion(region)
            .build();
  }

  static String getClamAvBucketName() {
    return System.getenv("WFDM_DOCUMENT_CLAMAV_S3BUCKET");
  }

  protected void delayProcessing() {
    try {
        Thread.sleep(300000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
    LambdaLogger logger = context.getLogger();
    
    List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
    String messageBody = "";

    // null check sqsEvents!
    if (sqsEvent == null || sqsEvent.getRecords() == null) {
      logger.log("\nInfo: No messages to handle\nInfo: Close SQS batch");
      return new SQSBatchResponse(batchItemFailures);
    }

    // Add a sleep here to delay message handling to avoid potential file update racing condition with other services 
    // calling WFDM api
    logger.log("\nInfo: delay running file index initializer lambda for 5 min to avoid file update racing condition");
    delayProcessing();
   
    // Iterate the available messages
    for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
      try {
        messageBody = message.getBody();
        logger.log("\nInfo: SQS Message Received on wfdm_file_index_initialize : " + messageBody);

        JSONObject messageDetails = new JSONObject(messageBody);

        if (messageDetails.has("body-json")) {
            messageDetails = messageDetails.getJSONObject("body-json");
        }

        String fileId = messageDetails.getString("fileId");
        
        if (!isValidFileId(fileId)){
        	logger.log("\nInfo: file id is not valid"+fileId);
        	return null;
        }

        // Where will we receive the event type? Message Body or attributes?
        String eventType = getEventType(messageDetails);

        logger.log("file id and event Type: "+fileId+" "+eventType);

        String versionNumber = messageDetails.getString("fileVersionNumber");

        String wfdmToken = retrieveWFDMToken(logger);

        HttpResponse<String> fileResponse = getFileResponse(wfdmToken, fileId);

        String fileInfo = fileResponse.getBody();
        String etag = fileResponse.getHeaders().getFirst("ETag");

        JSONObject fileDetailsJson = new JSONObject(fileInfo);

        String mimeType = getMimeType(fileDetailsJson);
        
        String fileExtension = getFileExtension(fileDetailsJson);

        boolean fileTooLargeToConvert = isFileTooLargeToConvert(fileDetailsJson);

        boolean isHeicOrHeif = isHeicOrHeif(fileExtension);

        if (!processImageConversion(fileTooLargeToConvert, isHeicOrHeif, wfdmToken,
        fileId, versionNumber, fileDetailsJson, etag, mimeType, logger)) {

      processEventType(eventType, wfdmToken, fileId, versionNumber, fileInfo,
          fileDetailsJson, etag, mimeType, messageBody, logger);
    }
        
      } catch (UnirestException | TransformerConfigurationException | SAXException e) {
        logger.log("logged exception" + e);
        logger.log("\nError: Failure to recieve file from WFDM: " + e.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } catch (Exception ex) {
        logger.log("\nUnhandled Error: " + ex.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } finally {
        // Cleanup
        logger.log("\nInfo: Finalizing processing...");
      }
    }
    logger.log("\nInfo: Close SQS batch");
    return new SQSBatchResponse(batchItemFailures);
  }

  private String retrieveWFDMToken(LambdaLogger logger) throws Exception {
    String wfdmSecretName = getSecretManagerName().trim();
    String secret = RetrieveSecret.RetrieveSecretValue(wfdmSecretName);
    String[] secretCD = StringUtils.substringsBetween(secret, "\"", "\"");

    String clientId = secretCD[0];
    String password = secretCD[1];

    logger.log("retrieved secret and client_ID and PASSWORD");

    String wfdmToken = GetFileFromWFDMAPI.getAccessToken(clientId, password);

    if (wfdmToken == null) {
      throw new Exception("Could not authorize access for WFDM");
    }

    return wfdmToken;
  }

  private void updateVirusScanMetadata(String wfdmToken, String fileId, String versionNumber,
      JSONObject fileDetailsJson, String etag, LambdaLogger logger) throws Exception {

    boolean metaAdded = GetFileFromWFDMAPI.setVirusScanMetadata( wfdmToken, fileId, versionNumber,
        fileDetailsJson, etag);

    if (!metaAdded) {
      logger.log("\nERROR: Failed to add metadata to file resource");
    }
  }

  private Bucket getClamAvBucket( AmazonS3 s3client) throws Exception {

    String bucketName = getClamAvBucketName().trim();

    for (Bucket bucket : s3client.listBuckets()) {
      if (bucket.getName().equalsIgnoreCase(bucketName)) {
        return bucket;
      }
    }

    throw new Exception( "S3 Bucket " + bucketName + " does not exist.");
  }

  private void uploadFileToClamAvBucket(AmazonS3 s3client, Bucket clamavBucket, String wfdmToken, String fileId,
    String versionNumber, String mimeType, JSONObject fileDetailsJson, LambdaLogger logger)throws Exception {

    BufferedInputStream stream = GetFileFromWFDMAPI.getFileStream(wfdmToken, fileId, versionNumber);

    ObjectMetadata meta = new ObjectMetadata();
    meta.setContentType(mimeType);
    meta.setContentLength(Long.parseLong(fileDetailsJson.get(FILE_SIZE).toString()));
    meta.addUserMetadata("title", fileId + "-" + versionNumber);

    logger.log("putting into s3 bucket");

    s3client.putObject(new PutObjectRequest(clamavBucket.getName(),
        fileDetailsJson.get("fileId").toString() + "-" + versionNumber,
        stream, meta));
  }

  private void invokeImageConverter(JSONObject fileDetailsJson, String mimeType, LambdaLogger logger) {
    logger.log("\nInfo: File with mimeType of " + mimeType + " calling image conversion lambda");

    AWSLambda client = createLambdaClient();
    InvokeRequest request = new InvokeRequest();

    request.withFunctionName(getImageConverterLambdaName().trim()).withPayload(fileDetailsJson.toString());

    client.invoke(request);
  }

  private void invokeIndexerWithFileDetails(JSONObject fileDetailsJson) {
    AWSLambda client = createLambdaClient();
    InvokeRequest request = new InvokeRequest();

    request.withFunctionName(getIndexingLambdaName().trim())
        .withPayload(fileDetailsJson.toString());

    client.invoke(request);
  }

  private void invokeIndexer(String messageBody, LambdaLogger logger) {
    logger.log("Calling lambda name: " + getIndexingLambdaName().trim() + " lambda: " + messageBody);

    AWSLambda client = createLambdaClient();
    InvokeRequest request = new InvokeRequest();

    request.withFunctionName(getIndexingLambdaName().trim())
      .withPayload(messageBody);

    client.invoke(request);
  }

  private void processBytesEvent(String wfdmToken, String fileId, String versionNumber, String fileInfo,
    JSONObject fileDetailsJson, String etag, String mimeType,
    LambdaLogger logger) throws Exception {

    logger.log("\nInfo: File found on WFDM: " + fileInfo);

    updateVirusScanMetadata(wfdmToken, fileId, versionNumber, fileDetailsJson, etag, logger);

    AmazonS3 s3client = createS3Client();

    Bucket clamavBucket = getClamAvBucket(s3client);

    uploadFileToClamAvBucket(s3client, clamavBucket, wfdmToken, fileId,
        versionNumber, mimeType, fileDetailsJson, logger);
  }

  private void processEventType(String eventType, String wfdmToken, String fileId,
      String versionNumber, String fileInfo, JSONObject fileDetailsJson,
      String etag, String mimeType, String messageBody,
      LambdaLogger logger) throws Exception {

    if (eventType.equalsIgnoreCase("bytes")) {
      processBytesEvent(wfdmToken, fileId, versionNumber, fileInfo,
          fileDetailsJson, etag, mimeType, logger);

    } else if (eventType.equalsIgnoreCase("meta")
        && fileDetailsJson.get(MIME_TYPE).toString().equals("null")) {

      invokeIndexerWithFileDetails(fileDetailsJson);

    } else {
      invokeIndexer(messageBody, logger);
    }
  }

  private HttpResponse<String> getFileResponse(String wfdmToken, String fileId) throws Exception {

    HttpResponse<String> fileResponse = GetFileFromWFDMAPI.getFileInformation(wfdmToken, fileId);

    if (fileResponse == null) {
      throw new Exception("File not found!");
    }

    return fileResponse;
  }

  private boolean processImageConversion(boolean fileTooLargeToConvert,
      boolean isHeicOrHeif, String wfdmToken, String fileId,
      String versionNumber, JSONObject fileDetailsJson,
      String etag, String mimeType, LambdaLogger logger) throws Exception {

    if (shouldAbortImageConversion(fileTooLargeToConvert, isHeicOrHeif)) {
      GetFileFromWFDMAPI.setImageConversionMetadata(wfdmToken, fileId,
          versionNumber, fileDetailsJson,
          "Image Conversion aborted due to file size", etag);
    }

    if (shouldInvokeImageConverter(fileTooLargeToConvert, isHeicOrHeif)) {
      invokeImageConverter(fileDetailsJson, mimeType, logger);
      return true;
    }

    return false;
  }

}
