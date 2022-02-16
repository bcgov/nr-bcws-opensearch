package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

public class SendSNSNotification {
	private static String region = "ca-central-1";
	static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
	private static String subject = "Virus Alert on WFDM-File-Indexing";

	public static void publicshMessagetoSNS(JSONObject messageDetails) {
		
		String topicArn = System.getenv("WFDM_SNS_VIRUS_ALERT").trim();

		AmazonSNS snsClient = AmazonSNSClient.builder().withRegion(region).withCredentials(credentialsProvider).build();

		final Map<String, MessageAttributeValue> attributes = new HashMap<String, MessageAttributeValue>();
		attributes.put("subject", new MessageAttributeValue().withDataType("String").withStringValue(subject));

		final String message = "The source " + messageDetails.getJSONObject("responsePayload").getString("source")
				+ " found a file " + messageDetails.getJSONObject("responsePayload").getString("input_key")
				+ " on S3 bucket " + messageDetails.getJSONObject("responsePayload").getString("input_bucket") + " at "
				+ messageDetails.getString("timestamp") + ".\n\n The scan status from ClamAv \n "
				+ messageDetails.getJSONObject("responsePayload").getString("message");

		
		final PublishRequest publishRequest = new PublishRequest().withTopicArn(topicArn).withSubject(subject)
				.withMessage(message).withMessageAttributes(attributes);
		final PublishResult publishResponse = snsClient.publish(publishRequest);

		// Print the MessageId of the message.
		System.out.println("MessageId from sns publicsh response: " + publishResponse.getMessageId());

	}

}
