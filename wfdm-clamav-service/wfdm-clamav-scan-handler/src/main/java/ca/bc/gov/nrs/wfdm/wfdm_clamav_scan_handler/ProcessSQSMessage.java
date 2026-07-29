package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

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
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.HttpResponse;

/**
 * Processor for the received SQS messages. As messages are placed onto the Queue
 * they'll be pulled by this handler. The message should be a WFDM fileID. This file
 * will then be fetched from WFDM. The file will be parsed by Tika, and the parsed
 * text and some metadata will be pushed into the OpenSearch store
 * 
 * Once this process is complete, this handler will place a message on another Queue
 * that will instruct the ClamAV lambda to execute
 */
public class ProcessSQSMessage implements RequestHandler<SQSEvent, SQSBatchResponse> {
  private static String region = "ca-central-1";
  static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
  private static final String RESPONSE_PAYLOAD = "responsePayload";

  protected String getSecretManagerName() {
    return System.getenv("WFDM_DOCUMENT_SECRET_MANAGER");
  }

  protected String getIndexingLambdaName() {
    return System.getenv("WFDM_INDEXING_LAMBDA_NAME");
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

  protected boolean setVirusScanMetadata(
      String token,
      String fileId,
      String versionNumber,
      JSONObject fileDetailsJson,
      String status,
      String etag)
      throws Exception {

    return GetFileFromWFDMAPI.setVirusScanMetadata(
        token,
        fileId,
        versionNumber,
        fileDetailsJson,
        status,
        etag);
  }

  protected void publishVirusNotification(JSONObject messageDetails) {
    SendSNSNotification.publicshMessagetoSNS(messageDetails);
  }

  private static class MessageInfo {
    String fileId;
    String versionNumber;
    String status;
    String summary;
  }

  protected AWSLambda createLambdaClient() {

    return AWSLambdaClient.builder()
        .withRegion(region)
        .build();
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
    LambdaLogger logger = context.getLogger();
    List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

    // null check sqsEvents!
    if (sqsEvent == null || sqsEvent.getRecords() == null) {
      logger.log("\nInfo: No messages to handle\nInfo: Close SQS batch");
      return new SQSBatchResponse(batchItemFailures);
    }

    // Iterate the available messages
    for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
      try {
        processMessage(message, logger);

      } catch (UnirestException | TransformerConfigurationException | SAXException e) {
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

  private void invokeIndexer(JSONObject fileDetailsJson, String versionNumber,
     String status, String summary,  LambdaLogger logger) {

    AWSLambda client = createLambdaClient();

    fileDetailsJson.put("eventType", "bytes");
    fileDetailsJson.put("fileVersionNumber", versionNumber);
    fileDetailsJson.put("status", status);
    fileDetailsJson.put("message", summary);

    logger.log("\n Calling lambda name: "
            + getIndexingLambdaName().trim()
            + " Lambda. "
            + fileDetailsJson.toString());

    InvokeRequest request = new InvokeRequest();

    request.withFunctionName(getIndexingLambdaName().trim())
        .withPayload(fileDetailsJson.toString());

    client.invoke(request);
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

  private MessageInfo parseMessage(
    String messageBody,
    LambdaLogger logger) {

    JSONObject messageDetails = new JSONObject(messageBody);
    JSONObject payload = messageDetails.getJSONObject(RESPONSE_PAYLOAD);

    String inputKey = payload.getString("input_key");

    MessageInfo info = new MessageInfo();

    info.status = payload.getString("status");

    if ("INFECTED".equals(info.status)) {
      publishVirusNotification(messageDetails);
    }

    if (!inputKey.contains("-")) {
      logger.log(
          "\nInfo: This is not a valid file name:"
          + inputKey
          + ".\n Program might exit.");
    }

    info.fileId = inputKey.split("-")[0];
    info.versionNumber = inputKey.split("-")[1];
    info.summary = payload.getString("message");

    logger.log(
        "\nInfo: SQS Message Received: "
        + messageBody
        + info.summary);

    return info;
  }


  private void processFile(String wfdmToken, String fileId, String versionNumber,
      String status, String summary, LambdaLogger logger) throws Exception {

    HttpResponse<String> fileResponse = getFileInformation(wfdmToken, fileId);

    if (fileResponse == null) {
      throw new Exception("File not found!");
    }

    String fileInfo = fileResponse.getBody();
    String etag = fileResponse.getHeaders().getFirst("ETag");
    JSONObject fileDetailsJson = new JSONObject(fileInfo);

    logger.log("\nInfo: File found on WFDM: " + fileInfo);

    boolean metaAdded = setVirusScanMetadata( wfdmToken, fileId, versionNumber,
        fileDetailsJson, status, etag);

    if (!metaAdded) {
      logger.log("\nERROR: Failed to add metadata to file resource");
    }

    invokeIndexer(
        fileDetailsJson,
        versionNumber,
        status,
        summary,
        logger);
  }

  private void processMessage(SQSEvent.SQSMessage message, LambdaLogger logger) throws Exception {

    String messageBody = message.getBody();

    logger.log("\nInfo: SQS Message Received: " + messageBody);

    MessageInfo messageInfo = parseMessage(messageBody, logger);

    String wfdmToken = retrieveWFDMToken();

    processFile( wfdmToken, messageInfo.fileId, messageInfo.versionNumber,
        messageInfo.status, messageInfo.summary, logger);
  }

}
