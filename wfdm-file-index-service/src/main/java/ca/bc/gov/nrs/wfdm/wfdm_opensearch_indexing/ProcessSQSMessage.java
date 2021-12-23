package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
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

import org.apache.tika.exception.TikaException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

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
  private static String bucketName = "wfdm-clamav-bucket"; // open-search-index-bucket already exists? Use that?
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
      BufferedInputStream stream = null;
      try {
        // This MUST be verified/sanitized!!!!
        // messageBody should be a fileId or a URL to the resource on WFDM. We can
        // validate either condition
        messageBody = message.getBody();
        logger.log("\nInfo: SQS Message Received: " + messageBody);

        // Should come for preferences, Client ID and secret for authentication with
        // WFDM
        String CLIENT_ID = "Clinet ID Goes Here";
        String PASSWORD = "Password Goes Here";

        // Fetch an authentication token. We fetch this each time so the tokens
        // themselves
        // aren't in a cache slowly getting stale. Could be replaced by a check token
        // and
        // a cached token
        String wfdmToken = GetFileFromWFDMAPI.getAccessToken(CLIENT_ID, PASSWORD);
        if (wfdmToken == null)
          throw new Exception("Could not authorize access for WFDM");

        String fileInfo = GetFileFromWFDMAPI.getFileInformation(wfdmToken, messageBody);

        if (fileInfo == null) {
          throw new Exception("File not found!");
        } else {
          JSONObject fileDetailsJson = new JSONObject(fileInfo);

          logger.log("\nInfo: File found on WFDM: " + fileInfo);
          // Fetch the bytes
          logger.log("\nInfo: Fetching file bytes...");
          stream = GetFileFromWFDMAPI.getFileStream(wfdmToken, messageBody);
          // Update Virus scan metadata
          // Note, current user likely lacks access to update metadata so we'll need to update webade
          boolean metaAdded = GetFileFromWFDMAPI.setVirusScanMetadata(wfdmToken, messageBody, fileDetailsJson);
          if (!metaAdded) {
            // We failed to apply the metadata regarding the virus scan status...
            // Should we continue to process the data from this point, or just choke?
          }
          // Tika Time! (If Necessary, check mime types)
          logger.log("\nInfo: Tika Parser...");

          String mimeType = fileDetailsJson.get("mimeType").toString();
          String content = "";

          if (mimeType.equalsIgnoreCase("text/plain") ||
              mimeType.equalsIgnoreCase("application/msword") ||
              mimeType.equalsIgnoreCase("application/pdf")) {
            content = TikaParseDocument.parseStream(stream);
          } else {
            // nothing to see here folks, we won't process this file. However
            // this isn't an error and we might want to handle metadata, etc.
            logger.log("\nInfo: Mime type of " + fileDetailsJson.get("mimeType")
                + " is not processed for OpenSearch. Skipping Tika parse.");
          }
          // Push content and File meta up to our Opensearch Index
          logger.log("\nInfo: Indexing with OpenSearch...");
          String filePath = fileDetailsJson.getString("filePath");
          String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

          OpenSearchRESTClient restClient = new OpenSearchRESTClient();
          restClient.addIndex(content, fileName, fileDetailsJson);
          // Push ID onto SQS for clamAV
          logger.log("\nInfo: File parsing complete. Schedule ClamAV scan.");

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

          // If the bucket doesn't exist, re-create it
          // For some reason, calling doesBucketExistV2 returns false???
          if(clamavBucket == null) {
            throw new Exception("S3 Bucket " + bucketName + " does not exist. Virus scan will be skipped");
          }

          // push the stream up into the bucket for virus scanning
          if (stream != null) {
            // If the stream was not passed to Tika it might be open still, so close it and refresh
            // the stream for s3
            stream.close();
          }
          stream = GetFileFromWFDMAPI.getFileStream(wfdmToken, messageBody);

          ObjectMetadata meta = new ObjectMetadata();
          meta.setContentType(mimeType);
          meta.setContentLength(Long.parseLong(fileDetailsJson.get("fileSize").toString()));
          meta.addUserMetadata("title", messageBody);
          s3client.putObject(new PutObjectRequest(clamavBucket.getName(), messageBody, stream, meta));
        }
      } catch (UnirestException | TransformerConfigurationException | SAXException e) {
        logger.log("\nError: Failure to recieve file " + messageBody + " from WFDM" + e.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } catch (TikaException tex) {
        logger.log("\nTika Parsing Error: " + tex.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } catch (Exception ex) {
        logger.log("\nUnhandled Error: " + ex.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } finally {
        // Cleanup
        logger.log("\nInfo: Finalizing processing...");
        // If the stream was fetched, but never passed to tika, it might still be open, so close it now
        // we send a fresh stream for the bucket and it should already be closed by the s3client, but it
        // never hurts to be sure!
        if (stream != null) {
          try {
            stream.close();
          } catch (IOException e) {
            logger.log("\nError: File stream cleanup failed: " + e.getLocalizedMessage());
          }
        }
      }
    }

    logger.log("\nInfo: Close SQS batch");
    return new SQSBatchResponse(batchItemFailures);
  }
}
