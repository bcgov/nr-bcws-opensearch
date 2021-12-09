package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class ProcessSQSMessage implements RequestHandler<SQSEvent, SQSBatchResponse> {
	private static LambdaLogger logger;

	@Override
    public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
	     logger = context.getLogger();

         List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<SQSBatchResponse.BatchItemFailure>();
         String messageBody = "";
         for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
             try {
            	 messageBody = message.getBody();
            	 logger.log("Info: Message Body: "+messageBody);
            	  try {
            		  GetFileFromWFDMAPI getFileAPI  = new GetFileFromWFDMAPI();
            		  getFileAPI.getAccessToken(messageBody);
            	  } catch (Exception e){
            		  e.printStackTrace();
            	  }

                // ParseDocuments parseDoc= new ParseDocuments();
                // parseDoc.pardeDcoument(messageBody);
                 
             } catch (Exception e) {
            	 e.printStackTrace();
                 //batchItemFailures.add(new StreamsEventResponse.BatchItemFailure(messageId));
             }
         }
         return new SQSBatchResponse(batchItemFailures);
     }
}
