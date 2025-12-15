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
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
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
public class ProcessSQSMessage implements RequestHandler<SQSEvent, SQSBatchResponse> {
  private static String region = "ca-central-1";
  static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();


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

    // Iterate the available messages
    for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
      try {
        messageBody = message.getBody();
        logger.log("\nInfo: SQS Message Received: " + messageBody);
        JSONObject messageDetails = new JSONObject(messageBody);
        String inputKey = messageDetails.getJSONObject("responsePayload").getString("input_key");
        String status = messageDetails.getJSONObject("responsePayload").getString("status");
        //if status is infected send an email to SNS topic
		if (status.equals("INFECTED")) {
			SendSNSNotification.publicshMessagetoSNS(messageDetails);
		}
        
        if(!inputKey.contains("-")) {
        	logger.log("\nInfo: This is not a valid file name:" + inputKey+".\n Program might exit.");	
        }
        String fileId = inputKey.split("-")[0];
        String versionNumber = inputKey.split("-")[1];
        String summary = messageDetails.getJSONObject("responsePayload").getString("message");
        logger.log("\nInfo: SQS Message Received: " + messageBody+summary);

        // Should come for preferences, Client ID and secret for authentication with
        // WFDM
        String wfdmSecretName = System.getenv("WFDM_DOCUMENT_SECRET_MANAGER").trim();
        String secret = RetrieveSecret.RetrieveSecretValue(wfdmSecretName);
        String[] secretCD = StringUtils.substringsBetween(secret, "\"", "\"");
  	  	String CLIENT_ID = secretCD[0];
  	  	String PASSWORD = secretCD[1];

        // Fetch an authentication token. We fetch this each time so the tokens
        // themselves
        // aren't in a cache slowly getting stale. Could be replaced by a check token
        // and
        // a cached token
        logger.log("Retrieving access token");
        String wfdmToken = GetFileFromWFDMAPI.getAccessToken(CLIENT_ID, PASSWORD);
        if (wfdmToken == null)
          throw new Exception("Could not authorize access for WFDM");
        logger.log("Retrieved access token: " + wfdmToken);

        logger.log("Retrieving file " + fileId);
        String fileInfo = GetFileFromWFDMAPI.getFileInformation(wfdmToken, fileId);

        if (fileInfo == null) {
          throw new Exception("File not found!");
        } else {
          JSONObject fileDetailsJson = new JSONObject(fileInfo);

          logger.log("\nInfo: File found on WFDM: " + fileInfo);
          // Update Virus scan metadata
          // Note, current user likely lacks access to update metadata so we'll need to update webade
          boolean metaAdded = GetFileFromWFDMAPI.setVirusScanMetadata(wfdmToken, fileId, versionNumber, fileDetailsJson, status);
          if (!metaAdded) {
            // We failed to apply the metadata regarding the virus scan status...
            // Should we continue to process the data from this point, or just choke?
            logger.log("\nERROR: Failed to add metadata to file resource");
          }

          // Meta only update, so fire a message to the Indexer Lambda
          AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(region).build();

          // ensure the default eventType of "Bytes" is appended
          // so the tika parser lambda knows to check for data
          // and not just meta
          fileDetailsJson.put("eventType", "bytes");
          fileDetailsJson.put("fileVersionNumber", versionNumber);

          fileDetailsJson.put("status", status);
          fileDetailsJson.put("message", summary);
          logger.log("\n Calling lambda name: "+System.getenv("WFDM_INDEXING_LAMBDA_NAME").trim()+" Lambda. "+fileDetailsJson.toString());
          InvokeRequest request = new InvokeRequest();
          request.withFunctionName(System.getenv("WFDM_INDEXING_LAMBDA_NAME").trim()).withPayload(fileDetailsJson.toString());
          InvokeResult invoke = client.invoke(request);
        }
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
}
