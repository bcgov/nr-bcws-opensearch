package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.mashape.unirest.http.HttpResponse;

class ProcessSQSMessageTest {

    @Test
    void shouldReturnEmptyStringWhenEventIsNull() {

        ProcessSQSMessage handler =
                new ProcessSQSMessage();

        Context context = mock(Context.class);

        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        String result =
                handler.handleRequest(
                        null,
                        context);

        assertEquals("", result);
    }

    @Test
    void shouldReturnClosedWhenTokenIsNull()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context = mock(Context.class);

        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn(null)
                .when(handler)
                .getAccessToken(anyString(), anyString());

        Map<String, Object> event =
                new HashMap<>();

        event.put("fileId", "123");

        String result =
                handler.handleRequest(
                        event,
                        context);

        assertEquals(
                "Closed",
                result);
    }

    @Test
    void shouldDefaultMissingEventTypeVersionAndMessage()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context = mock(Context.class);

        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        HttpResponse<String> response =
                mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders())
                .thenReturn(headers);

        when(headers.getFirst("ETag"))
                .thenReturn("etag");

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put("fileId", "123");
        fileDetails.put("filePath", "/folder/test.txt");
        fileDetails.put("metadata", new JSONArray());

        when(response.getBody())
                .thenReturn(fileDetails.toString());

        doReturn(response)
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        OpenSearchRESTClient restClient =
                mock(OpenSearchRESTClient.class);

        doReturn(restClient)
                .when(handler)
                .createOpenSearchClient();

        doNothing()
                .when(handler)
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        doReturn(true)
                .when(handler)
                .setIndexedMetadata(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString());

        String result =
                handler.handleRequest(
                        Map.of(
                                "fileId",
                                "123"),
                        context);

        verify(handler, times(2))
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        assertEquals(
                "Closed",
                result);
    }

    @Test
    void shouldSkipProtectedBFiles()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context = mock(Context.class);

        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        HttpResponse<String> response =
                mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders())
                .thenReturn(headers);

        when(headers.getFirst("ETag"))
                .thenReturn("etag");

        JSONArray metadata =
                new JSONArray();

        JSONObject securityMeta =
                new JSONObject();

        securityMeta.put(
                "metadataName",
                "SecurityClassification");

        securityMeta.put(
                "metadataValue",
                "Protected B");

        metadata.put(securityMeta);

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put("fileId", "123");
        fileDetails.put("filePath", "/folder/test.txt");
        fileDetails.put("metadata", metadata);

        when(response.getBody())
                .thenReturn(fileDetails.toString());

        doReturn(response)
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        OpenSearchRESTClient restClient =
                mock(OpenSearchRESTClient.class);

        doReturn(restClient)
                .when(handler)
                .createOpenSearchClient();

        doNothing()
                .when(handler)
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        String result =
                handler.handleRequest(
                        Map.of(
                                "fileId",
                                "123"),
                        context);

        verify(handler, never())
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        assertEquals(
                "Closed",
                result);
    }

    @Test
    void shouldHandleNullVersionNumber()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        HttpResponse<String> response =
                mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders())
                .thenReturn(headers);

        when(headers.getFirst("ETag"))
                .thenReturn("etag");

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put("fileId", "123");
        fileDetails.put("fileVersionNumber", "null");
        fileDetails.put("filePath", "/folder/test.txt");
        fileDetails.put("metadata", new JSONArray());

        when(response.getBody())
                .thenReturn(fileDetails.toString());

        doReturn(response)
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        OpenSearchRESTClient restClient =
                mock(OpenSearchRESTClient.class);

        doReturn(restClient)
                .when(handler)
                .createOpenSearchClient();

        doNothing()
                .when(handler)
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        doReturn(true)
                .when(handler)
                .setIndexedMetadata(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString());

        String result =
                handler.handleRequest(
                        Map.of(
                                "fileId", "123",
                                "fileVersionNumber", "null"),
                        context);

        verify(handler, times(2))
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        assertEquals(
                "Closed",
                result);
    }

    @Test
    void shouldHandleGenericException()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doThrow(new RuntimeException("boom"))
                .when(handler)
                .retrieveSecret(anyString());

        String result =
                handler.handleRequest(
                        Map.of("fileId", "123"),
                        context);

        assertEquals("Closed", result);

        verify(logger)
                .log(contains("Unhandled Error"));
    }

    @Test
    void shouldHandleOpenSearchException()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        HttpResponse<String> response =
                mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders())
                .thenReturn(headers);

        when(headers.getFirst("ETag"))
                .thenReturn("etag");

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put("fileId", "123");
        fileDetails.put("filePath", "/folder/test.txt");
        fileDetails.put("metadata", new JSONArray());

        when(response.getBody())
                .thenReturn(fileDetails.toString());

        doReturn(response)
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        OpenSearchRESTClient restClient =
                mock(OpenSearchRESTClient.class);

        doReturn(restClient)
                .when(handler)
                .createOpenSearchClient();

        doThrow(new OpenSearchException(new RuntimeException("os")))
                .when(handler)
                .addIndexWithRetry(
                        any(),
                        anyString(),
                        anyString(),
                        any(JSONObject.class),
                        anyString(),
                        any());

        String result =
                handler.handleRequest(
                        Map.of("fileId", "123"),
                        context);

        assertEquals("Closed", result);

        verify(logger)
                .log(contains("Open Search Error"));
    }

    @Test
    void shouldHandleUnirestException()
            throws Exception {

        ProcessSQSMessage handler =
                spy(new ProcessSQSMessage());

        Context context =
                mock(Context.class);

        LambdaLogger logger =
                mock(LambdaLogger.class);

        when(context.getLogger())
                .thenReturn(logger);

        doReturn("bucket")
                .when(handler)
                .getBucketName();

        doReturn("secret")
                .when(handler)
                .getSecretManagerName();

        doReturn("\"client\" \"password\"")
                .when(handler)
                .retrieveSecret(anyString());

        doReturn("token")
                .when(handler)
                .getAccessToken(anyString(), anyString());

        doThrow(new com.mashape.unirest.http.exceptions.UnirestException(
                "wfdm failure"))
                .when(handler)
                .getFileInformation(
                        anyString(),
                        anyString());

        String result =
                handler.handleRequest(
                        Map.of("fileId", "123"),
                        context);

        assertEquals("Closed", result);

        verify(logger)
                .log(contains("Failure to recieve file from WFDM"));
    }
}