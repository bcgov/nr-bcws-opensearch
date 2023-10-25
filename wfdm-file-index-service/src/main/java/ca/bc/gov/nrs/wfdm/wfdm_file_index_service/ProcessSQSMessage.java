package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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

  @Override
  public String handleRequest(Map<String, Object> event, Context context) {
    LambdaLogger logger = context.getLogger();
    String bucketName = System.getenv("WFDM_DOCUMENT_CLAMAV_S3BUCKET").trim();
    // null check sqsEvents!
    if (event == null) {
      logger.log("\nInfo: No messages to handle\nInfo: Closeing");
      return "";
    }

    BufferedInputStream stream = null;
    try {
      // messageBody is the complete file resource
      logger.log("\nInfo: Event Received on WFDM -open-search: " + event);
      JSONObject fileDetailsJson = new JSONObject(event);
      logger.log("fileDetailsJson" + fileDetailsJson.getString("fileId"));

      String fileId = fileDetailsJson.getString("fileId");

      String versionNumber;
      if (fileDetailsJson.has("fileVersionNumber")) {
        if (fileDetailsJson.getString("fileVersionNumber").equals("null")) {
          versionNumber = "1";
        } else {
          versionNumber = fileDetailsJson.getString("fileVersionNumber");
        }
      } else {
        versionNumber = "1";
      }
      //TODO:Update to correct event type from WFDM-API
      String eventType;
      if (fileDetailsJson.has("eventType")) {
        eventType = fileDetailsJson.getString("eventType");
      } else {
        eventType = "meta";
        logger.log("\nInfo: eventType key/value was not found, setting eventType to: " + eventType);
      }

      String scanStatus;
      if (fileDetailsJson.has("message") && !fileDetailsJson.isNull("message"))
        scanStatus = fileDetailsJson.getString("message");
      else
        scanStatus = "-";
      // Should come for preferences, Client ID and secret for authentication with
      // WFDM
      logger.log(eventType);
      String wfdmSecretName = System.getenv("WFDM_DOCUMENT_SECRET_MANAGER").trim();
      String secret = RetrieveSecret.RetrieveSecretValue(wfdmSecretName);
      String[] secretCD = StringUtils.substringsBetween(secret, "\"", "\"");
      String CLIENT_ID = secretCD[0];
      String PASSWORD = secretCD[1];

      //logger.log("message"+fileDetailsJson.getString("message"));
      // Fetch an authentication token. We fetch this each time so the tokens
      // themselves
      // aren't in a cache slowly getting stale. Could be replaced by a check token
      // and
      // a cached token
      String wfdmToken = GetFileFromWFDMAPI.getAccessToken(CLIENT_ID, PASSWORD);
      logger.log("wfdmToken :" + wfdmToken);
      if (wfdmToken == null)
        throw new Exception("Could not authorize access for WFDM");

      // attempt to fetch the file from WFDM, as a verification that the file actually exists
      String fileInfo = GetFileFromWFDMAPI.getFileInformation(wfdmToken, fileId);

      logger.log("\nInfo: fileInfo is: " + fileInfo);

      if (fileInfo == null) {
        throw new Exception("File not found!");
      } else {
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

          Bucket clamavBucket = null;
          List<Bucket> buckets = s3client.listBuckets();
          for (Bucket bucket : buckets) {
            if (bucket.getName().equalsIgnoreCase(bucketName)) {
              clamavBucket = bucket;
            }
          }

          // If the bucket doesn't exist, re-create it
          // For some reason, calling doesBucketExistV2 returns false???
          if (clamavBucket == null) {
            throw new Exception("S3 Bucket " + bucketName + " does not exist. Virus scan will be skipped");
          }

          logger.log("\nInfo: Fetching file bytes...");

          S3Object scannedObject = s3client.getObject(new GetObjectRequest(bucketName, fileId + "-" + versionNumber));
          stream = new BufferedInputStream(scannedObject.getObjectContent());

          // Tika Time! (If Necessary, check mime types)
          logger.log("\nInfo: Tika Parser...");

          String mimeType = fileDetailsJson.get("mimeType").toString();

          if (mimeType.equalsIgnoreCase("text/plain") ||
              mimeType.equalsIgnoreCase("application/msword") ||
              mimeType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
              mimeType.equalsIgnoreCase("application/pdf") ||
              mimeType.equalsIgnoreCase("application/vnd.ms-excel.sheet.macroEnabled.12") ||
              mimeType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
              mimeType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.presentationml.presentation") ) {
            content = TikaParseDocument.parseStream(stream, mimeType);
            logger.log("\nInfo: content after parsing " + content);
          } else {
            // nothing to see here folks, we won't process this file. However
            // this isn't an error and we might want to handle metadata, etc.
            logger.log("\nInfo: Mime type of " + fileDetailsJson.get("mimeType")
                + " is not processed for OpenSearch. Skipping Tika parse.");
          }

          // We've finished with the file, delete the file from the s3 Bucket
          s3client.deleteObject(new DeleteObjectRequest(clamavBucket.getName(), fileId + "-" + versionNumber));
        }

        // Push content and File meta up to our Opensearch Index
        logger.log("\nInfo: Indexing with OpenSearch...");
        String filePath = fileDetailsJson.getString("filePath");
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

        OpenSearchRESTClient restClient = new OpenSearchRESTClient();

        // We are disabling indexing of files with a security classification of Protected B or Protected C
        JSONArray metaArray = fileDetailsJson.getJSONArray("metadata");
        boolean skipIndexing = false;
        for (int i = 0; i < metaArray.length(); i++) {
          String metadataName = metaArray.getJSONObject(i).getString("metadataName");
          String metadataValue = metaArray.getJSONObject(i).getString("metadataValue");

          if (metadataName.equals("SecurityClassification")
              && (metadataValue.equals("Protected B") || metadataValue.equals("Protected C"))) {
            skipIndexing = true;
          }
        }

        if (!skipIndexing) {

          restClient.addIndex(content, fileName, fileDetailsJson, scanStatus);
          // Push ID onto SQS for clamAV
          logger.log("\nInfo: File parsing complete. Schedule ClamAV scan.");

          // update metadata
          boolean metaAdded = GetFileFromWFDMAPI.setIndexedMetadata(wfdmToken, fileId, versionNumber, fileDetailsJson);
          if (!metaAdded) {
            // We failed to apply the metadata regarding the virus scan status...
            // Should we continue to process the data from this point, or just choke?
            logger.log("\nERROR: Failed to add metadata to file resource");
          }

          // after updating metadata, get file info again and update index
          fileInfo = GetFileFromWFDMAPI.getFileInformation(wfdmToken, fileId);
          fileDetailsJson = new JSONObject(fileInfo);

          restClient.addIndex(content, fileName, fileDetailsJson, scanStatus);

        }
      }
    } catch (UnirestException | TransformerConfigurationException | SAXException e) {
      logger.log("\nError: Failure to recieve file from WFDM" + e.getLocalizedMessage());
    } catch (TikaException tex) {
      logger.log("\nTika Parsing Error: " + tex.getLocalizedMessage());
    } catch (OpenSearchException e) {
      logger.log("\nOpen Search Error: " + e.getLocalizedMessage());
    } catch (Exception ex) {
      logger.log("\nUnhandled Error: " + ex.getLocalizedMessage());
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

    logger.log("\nInfo: Close Handler");
    return "Closed";
  }
  
}
