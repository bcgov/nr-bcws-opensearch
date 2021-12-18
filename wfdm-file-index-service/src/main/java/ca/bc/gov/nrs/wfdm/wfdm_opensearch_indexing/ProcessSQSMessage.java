package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerConfigurationException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
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
  @Override
  public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
    LambdaLogger logger = context.getLogger();
    List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
    String messageBody = "";

    // Iterate the available messages
    for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
      try {
        // This MUST be verified/sanitized!!!!
        // messageBody should be a fileId or a URL to the resource on WFDM. We can
        // validate either condition
        messageBody = message.getBody();
        logger.log("Info: SQS Message Received: " + messageBody);

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
          logger.log("Info: File found on WFDM: " + fileInfo);
          // Fetch the bytes
          logger.log("Info: Fetching file bytes...");
          BufferedInputStream stream = GetFileFromWFDMAPI.getFileStream(wfdmToken, messageBody);
          // Tika Time! (If Necessary, check mime types)
          logger.log("Info: Tika Parser...");
          JSONObject fileDetailsJson = new JSONObject(fileInfo);

          String mimeType = fileDetailsJson.get("mimeType").toString();
          String content = "";

          if (mimeType.equalsIgnoreCase("text/plain") ||
              mimeType.equalsIgnoreCase("application/msword") ||
              mimeType.equalsIgnoreCase("application/pdf")) {
            content = TikaParseDocument.parseStream(stream);
          } else {
            // nothing to see here folks, we won't process this file. However
            // this isn't an error and we might want to handle metadata, etc.
            logger.log("Info: Mime type of " + fileDetailsJson.get("mimeType")
                + " is not processed for OpenSearch. Skipping Tika parse.");
          }
          // Push content and File meta up to our Opensearch Index
          logger.log("Info: Indexing with OpenSearch...");
          String filePath = fileDetailsJson.getString("filePath");
          String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

          OpenSearchRESTClient restClinet = new OpenSearchRESTClient();

          restClinet.addIndex(content, fileName, fileDetailsJson);
          // Cleanup
          logger.log("Info: Finalizing processing...");
          stream.close();
          // Push ID onto SQS for clamAV
          logger.log("Info: File parsing complete. Schedule ClamAV scan.");
        }
      } catch (UnirestException | TransformerConfigurationException | SAXException e) {
        logger.log("Error: Failure to recieve file " + messageBody + " from WFDM" + e.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } catch (TikaException tex) {
        logger.log("Tika Parsing Error: " + tex.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      } catch (Exception ex) {
        logger.log("Unhandled Error: " + ex.getLocalizedMessage());
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
      }
    }

    logger.log("Info: Close SQS batch");
    return new SQSBatchResponse(batchItemFailures);
  }
}
