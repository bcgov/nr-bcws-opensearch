package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;

class ProcessSQSMessageTest {

    @Test
    void shouldReturnEmptyBatchWhenEventIsNull() {

        ProcessSQSMessage handler =
                new ProcessSQSMessage();

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        SQSBatchResponse result =
                handler.handleRequest(
                        null,
                        context);

        assertEquals(
                0,
                result.getBatchItemFailures().size());
    }

    @Test
    void shouldReturnEmptyBatchWhenRecordsAreNull() {

        ProcessSQSMessage handler =
                new ProcessSQSMessage();

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        SQSEvent event =
                mock(SQSEvent.class);

        when(event.getRecords())
                .thenReturn(null);

        SQSBatchResponse result =
                handler.handleRequest(
                        event,
                        context);

        assertEquals(
                0,
                result.getBatchItemFailures().size());
    }

    @Test
    void shouldAddFailureWhenTokenIsNull()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("secretName")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn(null)
                .when(handler)
                .getAccessToken(anyString(), anyString());

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson(
                "123-1",
                "CLEAN"));

        SQSEvent event =
                new SQSEvent();

        event.setRecords(
                List.of(message));

        SQSBatchResponse result =
                handler.handleRequest(
                        event,
                        context);

        assertEquals(
                1,
                result.getBatchItemFailures().size());
    }

    @Test
    void shouldAddFailureWhenFileNotFound()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("secretName")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        doReturn(null)
                .when(handler)
                .getFileInformation(anyString(), anyString());

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson(
                "123-1",
                "CLEAN"));

        SQSEvent event =
                new SQSEvent();

        event.setRecords(
                List.of(message));

        SQSBatchResponse result =
                handler.handleRequest(
                        event,
                        context);

        assertEquals(
                1,
                result.getBatchItemFailures().size());
    }

    @Test
    void shouldPublishVirusNotificationWhenInfected()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        setupSuccessfulProcessing(handler);

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson(
                "123-1",
                "INFECTED"));

        SQSEvent event =
                new SQSEvent();

        event.setRecords(
                List.of(message));

        handler.handleRequest(
                event,
                context);

        verify(handler)
                .publishVirusNotification(
                        any(JSONObject.class));
    }

    @Test
    void shouldInvokeIndexerLambda()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        AWSLambda lambda =
                mock(AWSLambda.class);

        when(lambda.invoke(any()))
                .thenReturn(
                        new InvokeResult());

        setupSuccessfulProcessing(handler);

        doReturn("indexer-lambda")
                .when(handler)
                .getIndexingLambdaName();

        doReturn(lambda)
                .when(handler)
                .createLambdaClient();

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson(
                "123-1",
                "CLEAN"));

        SQSEvent event =
                new SQSEvent();

        event.setRecords(
                List.of(message));

        handler.handleRequest(
                event,
                context);

        verify(lambda)
                .invoke(any());
    }

    @Test
    void shouldAddFailureWhenUnirestExceptionOccurs()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        doReturn("secretName")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        doThrow(new UnirestException("boom"))
                .when(handler)
                .getFileInformation(anyString(), anyString());

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson("123-1", "CLEAN"));

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        SQSBatchResponse result =
                handler.handleRequest(event, context);

        assertEquals(
                1,
                result.getBatchItemFailures().size());
    }

    @Test
    void shouldAddFailureWhenUnhandledExceptionOccurs()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doThrow(new RuntimeException("boom"))
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("secretName")
                .when(handler)
                .getSecretManagerName();

        SQSEvent.SQSMessage message =
                new SQSEvent.SQSMessage();

        message.setMessageId("id1");
        message.setBody(createMessageJson(
                "123-1",
                "CLEAN"));

        SQSEvent event =
                new SQSEvent();

        event.setRecords(
                List.of(message));

        SQSBatchResponse result =
                handler.handleRequest(
                        event,
                        context);

        assertEquals(
                1,
                result.getBatchItemFailures().size());
    }

    private void setupSuccessfulProcessing(
            ProcessSQSMessage handler)
            throws Exception {

        doReturn("secretName")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        Headers headers =
                mock(Headers.class);

        when(response.getHeaders())
                .thenReturn(headers);

        when(headers.getFirst("ETag"))
                .thenReturn("etag");

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put(
                "fileId",
                "123");

        fileDetails.put(
                "metadata",
                new org.json.JSONArray());

        when(response.getBody())
                .thenReturn(
                        fileDetails.toString());

        doReturn(response)
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        doReturn(true)
                .when(handler)
                .setVirusScanMetadata(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        anyString());
    }

    private String createMessageJson(
            String inputKey,
            String status) {

        JSONObject payload =
                new JSONObject();

        payload.put(
                "input_key",
                inputKey);

        payload.put(
                "status",
                status);

        payload.put(
                "message",
                "scan summary");

        JSONObject root =
                new JSONObject();

        root.put(
                "responsePayload",
                payload);

        return root.toString();
    }
}