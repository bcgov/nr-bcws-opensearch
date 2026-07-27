package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishResult;

class SendSNSNotificationTest {

    @Test
    void shouldPublishMessageToSns() {

        AmazonSNS snsClient =
                mock(AmazonSNS.class);

        PublishResult publishResult =
                mock(PublishResult.class);

        when(snsClient.publish(any()))
                .thenReturn(publishResult);

        when(publishResult.getMessageId())
                .thenReturn("message-id");

        JSONObject responsePayload =
                new JSONObject();

        responsePayload.put(
                "source",
                "clamav");

        responsePayload.put(
                "input_key",
                "123-1");

        responsePayload.put(
                "input_bucket",
                "test-bucket");

        responsePayload.put(
                "message",
                "INFECTED");

        JSONObject messageDetails =
                new JSONObject();

        messageDetails.put(
                "timestamp",
                "2024-01-01T00:00:00");

        messageDetails.put(
                "responsePayload",
                responsePayload);

        try (MockedStatic<SendSNSNotification> mocked =
                     mockStatic(
                             SendSNSNotification.class,
                             CALLS_REAL_METHODS)) {

            mocked.when(
                    SendSNSNotification::getTopicArn)
                    .thenReturn("arn:test:sns");

            mocked.when(
                    SendSNSNotification::createSnsClient)
                    .thenReturn(snsClient);

            SendSNSNotification.publicshMessagetoSNS(
                    messageDetails);

            verify(snsClient)
                    .publish(any());
        }
    }
}