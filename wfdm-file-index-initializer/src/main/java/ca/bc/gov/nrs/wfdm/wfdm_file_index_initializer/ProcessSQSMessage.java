package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.commons.lang.StringUtils;
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
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mashape.unirest.http.exceptions.UnirestException;

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

  @Override
  public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
    LambdaLogger logger = context.getLogger();
    String bucketName = System.getenv("WFDM_DOCUMENT_CLAMAV_S3BUCKET").trim();
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
        logger.log("\nInfo: SQS Message Received on wfdm_file_index_initialize : " + messageBody);

        JSONObject messageDetails = new JSONObject(messageBody);
        String fileId = messageDetails.getString("fileId");
        
        boolean isNumeric = fileId.chars().allMatch( Character::isDigit );
        if(!isNumeric) {
        	logger.log("\nInfo: file id is not valid"+fileId);
        	return null;
        }

        
        // Where will we receive the event type? Message Body or attributes?
        String eventType = messageDetails.getString("eventType");
        System.out.println("file id and event Type: "+fileId+" "+eventType);
        // Check the event type. If this is a BYTES event, write the bytes
        // otherwise, handle meta only and skip clam scan.
        if (eventType.equalsIgnoreCase("bytes")) {
          String versionNumber = messageDetails.getString("fileVersionNumber");

          String wfdmSecretName = System.getenv("WFDM_DOCUMENT_SECRET_MANAGER").trim();
          String secret = RetrieveSecret.RetrieveSecretValue(wfdmSecretName);
    	  String[] secretCD = StringUtils.substringsBetween(secret, "\"", "\"");
    	  String CLIENT_ID = secretCD[0];
    	  String PASSWORD = secretCD[1];

          String wfdmToken = GetFileFromWFDMAPI.getAccessToken(CLIENT_ID, PASSWORD);
          if (wfdmToken == null)
            throw new Exception("Could not authorize access for WFDM");

          String fileInfo = GetFileFromWFDMAPI.getFileInformation(wfdmToken, fileId);

          if (fileInfo == null) {
            throw new Exception("File not found!");
          } else {
            JSONObject fileDetailsJson = new JSONObject(fileInfo);
            String mimeType = fileDetailsJson.get("mimeType").toString();

            logger.log("\nInfo: File found on WFDM: " + fileInfo);
            // Update Virus scan metadata
            // Note, current user likely lacks access to update metadata so we'll need to update webade
            boolean metaAdded = GetFileFromWFDMAPI.setVirusScanMetadata(wfdmToken, fileId, versionNumber, fileDetailsJson);
            if (!metaAdded) {
              // We failed to apply the metadata regarding the virus scan status...
              // Should we continue to process the data from this point, or just choke?
              logger.log("\nERROR: Failed to add metadata to file resource");
            }

            AmazonS3 s3client = AmazonS3ClientBuilder
              .standard()
              .withCredentials(credentialsProvider)
              .withRegion(region)
              .build();

            Bucket clamavBucket = null;
            List<Bucket> buckets = s3client.listBuckets();
            for(Bucket bucket : buckets) {
              if (bucket.getName().equalsIgnoreCase(bucketName)) {
                clamavBucket = bucket;
              }
            }

            if(clamavBucket == null) {
              throw new Exception("S3 Bucket " + bucketName + " does not exist.");
            }

            BufferedInputStream stream = GetFileFromWFDMAPI.getFileStream(wfdmToken, fileId, versionNumber);

            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(mimeType);
            meta.setContentLength(Long.parseLong(fileDetailsJson.get("fileSize").toString()));
            meta.addUserMetadata("title", fileId + "-" + versionNumber);
            System.out.println("putting into s3 bucket");
            s3client.putObject(new PutObjectRequest(clamavBucket.getName(), fileDetailsJson.get("fileId").toString() + "-" + versionNumber, stream, meta));
          }
        } else {
          // Meta only update, so fire a message to the Indexer Lambda
          System.out.println("Calling lambda name: "+System.getenv("WFDM_INDEXING_LAMBDA_NAME").trim()+" lambda: "+messageBody);
          AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(region).build();
          InvokeRequest request = new InvokeRequest();
          request.withFunctionName(System.getenv("WFDM_INDEXING_LAMBDA_NAME").trim()).withPayload(messageBody);
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
