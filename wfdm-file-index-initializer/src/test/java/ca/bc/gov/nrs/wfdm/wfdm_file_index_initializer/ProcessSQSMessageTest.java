package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mashape.unirest.http.HttpResponse;

class ProcessSQSMessageTest {

    @Test
    void shouldHandleNullEvent() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSBatchResponse response = handler.handleRequest(null, context);

        assertEquals(  0, response.getBatchItemFailures().size());
    }

    @Test
    void shouldHandleNullRecords() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent event = new SQSEvent();
        event.setRecords(null);

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertEquals( 0, response.getBatchItemFailures().size());
    }

    @Test
    void shouldReturnNullForInvalidFileId() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();

        message.setBody( "{\"fileId\":\"ABC123\",\"fileVersionNumber\":\"1\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        assertNull( handler.handleRequest(event, context));

    }



    @Test
    void shouldAcceptNumericFileId() {
        assertTrue(ProcessSQSMessage.isValidFileId("123456"));
    }

    @Test
    void shouldRejectAlphaNumericFileId() {
        assertFalse(ProcessSQSMessage.isValidFileId("123ABC"));
    }

    @Test
    void shouldRejectFileIdContainingSymbols() {
        assertFalse(ProcessSQSMessage.isValidFileId("123-456"));
    }

    @Test
    void shouldAcceptSingleDigitFileId() {

        assertTrue(ProcessSQSMessage.isValidFileId("1"));
    }

    @Test
    void shouldReturnProvidedEventType() {
        JSONObject messageDetails = new JSONObject();
        messageDetails.put("eventType", "bytes");

        String eventType = ProcessSQSMessage.getEventType(messageDetails);

        assertEquals("bytes", eventType);
    }

    @Test
    void shouldDefaultEventTypeToMeta() {
        JSONObject messageDetails = new JSONObject();

        String eventType = ProcessSQSMessage.getEventType(messageDetails);

        assertEquals("meta", eventType);
    }

    @Test
    void shouldIdentifyHeicFileExtension() {
        assertTrue(ProcessSQSMessage.isHeicOrHeif("HEIC"));
    }

    @Test
    void shouldIdentifyHeifFileExtension() {
        assertTrue(ProcessSQSMessage.isHeicOrHeif("HEIF"));
    }

    @Test
    void shouldRejectNonHeicFileExtension() {
        assertFalse(ProcessSQSMessage.isHeicOrHeif("PDF"));
    }

    @Test
    void shouldReturnMimeType() {
        JSONObject fileDetails = new JSONObject();
        fileDetails.put("mimeType", "application/pdf");

        String mimeType = ProcessSQSMessage.getMimeType(fileDetails);

        assertEquals("application/pdf", mimeType);
    }

    @Test
    void shouldReturnEmptyMimeTypeWhenMissing() {
        JSONObject fileDetails = new JSONObject();

        String mimeType =
                ProcessSQSMessage.getMimeType(fileDetails);

        assertEquals("", mimeType);
    }

    @Test
    void shouldReturnUppercaseFileExtension() {
        JSONObject fileDetails = new JSONObject();
        fileDetails.put("fileExtension", "pdf");

        String extension = ProcessSQSMessage.getFileExtension(fileDetails);

        assertEquals("PDF", extension);
    }

    @Test
    void shouldReturnEmptyFileExtensionWhenMissing() {
        JSONObject fileDetails = new JSONObject();

        String extension = ProcessSQSMessage.getFileExtension(fileDetails);

        assertEquals("", extension);
    }

    @Test
    void shouldIdentifyLargeFile() {
        JSONObject fileDetails = new JSONObject();
        fileDetails.put("fileSize", "10000001");

        assertTrue(
                ProcessSQSMessage.isFileTooLargeToConvert(
                        fileDetails));
    }

    @Test
    void shouldIdentifySmallFile() {
        JSONObject fileDetails = new JSONObject();
        fileDetails.put("fileSize", "10000000");

        assertFalse(
                ProcessSQSMessage.isFileTooLargeToConvert(
                        fileDetails));
    }

    @Test
    void shouldReturnFalseWhenFileSizeMissing() {
        JSONObject fileDetails = new JSONObject();

        assertFalse(
                ProcessSQSMessage.isFileTooLargeToConvert(
                        fileDetails));
    }

    @Test
    void shouldAbortImageConversionForLargeHeicFile() {
        assertTrue(
                ProcessSQSMessage.shouldAbortImageConversion(
                        true,
                        true));
    }

    @Test
    void shouldNotAbortImageConversionForSmallHeicFile() {
        assertFalse(
                ProcessSQSMessage.shouldAbortImageConversion(
                        false,
                        true));
    }

    @Test
    void shouldNotAbortImageConversionForLargePdfFile() {
        assertFalse(
                ProcessSQSMessage.shouldAbortImageConversion(
                        true,
                        false));
    }

    @Test
    void shouldInvokeImageConverterForSmallHeicFile() {
        assertTrue(
                ProcessSQSMessage.shouldInvokeImageConverter(
                        false,
                        true));
    }

    @Test
    void shouldNotInvokeImageConverterForLargeHeicFile() {
        assertFalse(
                ProcessSQSMessage.shouldInvokeImageConverter(
                        true,
                        true));
    }

    @Test
    void shouldNotInvokeImageConverterForPdfFile() {
        assertFalse(
                ProcessSQSMessage.shouldInvokeImageConverter(
                        false,
                        false));
    }

    @Test
    void shouldAddBatchFailureWhenAccessTokenCannotBeRetrieved() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                "{\"fileId\":\"12345\",\"fileVersionNumber\":\"1\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        try (MockedStatic<RetrieveSecret> secretMock =
                    mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                    mockStatic(GetFileFromWFDMAPI.class)) {

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn(null);

            SQSBatchResponse response =
                    handler.handleRequest(event, context);

            assertEquals(
                    1,
                    response.getBatchItemFailures().size());
        }
    }

    @Test
    void shouldAddBatchFailureWhenFileInformationCannotBeRetrieved() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                "{\"fileId\":\"12345\",\"fileVersionNumber\":\"1\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        try (MockedStatic<RetrieveSecret> secretMock =
                    mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                    mockStatic(GetFileFromWFDMAPI.class)) {

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn("token");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileInformation(
                            anyString(),
                            anyString()))
                    .thenReturn(null);

            SQSBatchResponse response =
                    handler.handleRequest(event, context);

            assertEquals(
                    1,
                    response.getBatchItemFailures().size());
        }
    }

    @Test
    void shouldInvokeImageConverterForHeicFile() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                "{\"fileId\":\"12345\",\"fileVersionNumber\":\"1\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        AWSLambda lambdaClient = mock(AWSLambda.class);

        InvokeResult invokeResult = new InvokeResult();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(invokeResult);

        HttpResponse<String> response = mock(HttpResponse.class);
        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders()).thenReturn(headers);
        when(headers.getFirst("ETag")).thenReturn("etag");

        when(response.getBody()).thenReturn(
                """
                {
                "fileId":"12345",
                "fileExtension":"HEIC",
                "mimeType":"image/heic",
                "fileSize":"1000"
                }
                """);

        try (MockedStatic<RetrieveSecret> secretMock =
                        mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                        mockStatic(GetFileFromWFDMAPI.class);
            MockedStatic<ProcessSQSMessage> processMock =
                        mockStatic(
                                ProcessSQSMessage.class,
                                Mockito.CALLS_REAL_METHODS)) {

            processMock.when(
                    ProcessSQSMessage::getSecretManagerName)
                    .thenReturn("test-secret");

            processMock.when(
                    ProcessSQSMessage::getImageConverterLambdaName)
                    .thenReturn("image-converter");

            processMock.when(
                    ProcessSQSMessage::createLambdaClient)
                    .thenReturn(lambdaClient);

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn("token");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileInformation(
                            anyString(),
                            anyString()))
                    .thenReturn(response);

            SQSBatchResponse result =
                    handler.handleRequest(event, context);

            assertEquals(
                    0,
                    result.getBatchItemFailures().size());

            verify(lambdaClient)
                    .invoke(any(InvokeRequest.class));
        }
    }

    @Test
    void shouldAbortImageConversionForLargeHeicFileInHandler() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                "{\"fileId\":\"12345\",\"fileVersionNumber\":\"1\"}");

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        AWSLambda lambdaClient = mock(AWSLambda.class);

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(new InvokeResult());

        HttpResponse<String> response = mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders()).thenReturn(headers);
        when(headers.getFirst("ETag")).thenReturn("etag");

        when(response.getBody()).thenReturn(
                """
                {
                "fileId":"12345",
                "fileExtension":"HEIC",
                "mimeType":"image/heic",
                "fileSize":"20000001"
                }
                """);

        try (MockedStatic<RetrieveSecret> secretMock =
                        mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                        mockStatic(GetFileFromWFDMAPI.class);
            MockedStatic<ProcessSQSMessage> processMock =
                        mockStatic(
                                ProcessSQSMessage.class,
                                Mockito.CALLS_REAL_METHODS)) {

            processMock.when(ProcessSQSMessage::getSecretManagerName)
                    .thenReturn("test-secret");

            processMock.when(ProcessSQSMessage::getIndexingLambdaName)
                    .thenReturn("indexer-lambda");

            processMock.when(ProcessSQSMessage::createLambdaClient)
                    .thenReturn(lambdaClient);

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn("token");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileInformation(
                            anyString(),
                            anyString()))
                    .thenReturn(response);

            SQSBatchResponse result =
                    handler.handleRequest(event, context);

            assertEquals(
                    0,
                    result.getBatchItemFailures().size());

            wfdmMock.verify(
                    () -> GetFileFromWFDMAPI.setImageConversionMetadata(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class),
                            anyString(),
                            anyString()));

            verify(lambdaClient)
                    .invoke(any(InvokeRequest.class));
        }
    }


    @Test
    void shouldInvokeIndexingLambdaForMetaEvent() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                """
                {
                "fileId":"12345",
                "fileVersionNumber":"1",
                "eventType":"meta"
                }
                """);

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        AWSLambda lambdaClient = mock(AWSLambda.class);

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(new InvokeResult());

        HttpResponse<String> response = mock(HttpResponse.class);
        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders()).thenReturn(headers);
        when(headers.getFirst("ETag")).thenReturn("etag");

        when(response.getBody()).thenReturn(
                """
                {
                "fileId":"12345",
                "fileExtension":"PDF",
                "mimeType":"application/pdf",
                "fileSize":"1000"
                }
                """);

        try (MockedStatic<RetrieveSecret> secretMock =
                        mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                        mockStatic(GetFileFromWFDMAPI.class);
            MockedStatic<ProcessSQSMessage> processMock =
                        mockStatic(
                                ProcessSQSMessage.class,
                                Mockito.CALLS_REAL_METHODS)) {

            processMock.when(ProcessSQSMessage::getSecretManagerName)
                    .thenReturn("test-secret");

            processMock.when(ProcessSQSMessage::getIndexingLambdaName)
                    .thenReturn("indexer");

            processMock.when(ProcessSQSMessage::createLambdaClient)
                    .thenReturn(lambdaClient);

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn("token");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileInformation(
                            anyString(),
                            anyString()))
                    .thenReturn(response);

            SQSBatchResponse result =
                    handler.handleRequest(event, context);

            assertEquals(
                    0,
                    result.getBatchItemFailures().size());

            verify(lambdaClient)
                    .invoke(any(InvokeRequest.class));
        }
    }

    @Test
    void shouldProcessBytesEvent() {

        ProcessSQSMessage handler = Mockito.spy(
        new ProcessSQSMessage());

        Mockito.doNothing().when(handler).delayProcessing();

        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(context.getLogger()).thenReturn(logger);

        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg1");
        message.setBody(
                """
                {
                "fileId":"12345",
                "fileVersionNumber":"1",
                "eventType":"bytes"
                }
                """);

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        AmazonS3 s3Client = mock(AmazonS3.class);

        Bucket bucket = new Bucket();
        bucket.setName("clam-bucket");

        when(s3Client.listBuckets())
                .thenReturn(List.of(bucket));

        HttpResponse<String> response = mock(HttpResponse.class);

        com.mashape.unirest.http.Headers headers =
                mock(com.mashape.unirest.http.Headers.class);

        when(response.getHeaders()).thenReturn(headers);
        when(headers.getFirst("ETag")).thenReturn("etag");

        when(response.getBody()).thenReturn(
                """
                {
                "fileId":"12345",
                "fileExtension":"PDF",
                "mimeType":"application/pdf",
                "fileSize":"1000"
                }
                """);

        BufferedInputStream stream =
                mock(BufferedInputStream.class);

        try (MockedStatic<RetrieveSecret> secretMock =
                        mockStatic(RetrieveSecret.class);
            MockedStatic<GetFileFromWFDMAPI> wfdmMock =
                        mockStatic(GetFileFromWFDMAPI.class);
            MockedStatic<ProcessSQSMessage> processMock =
                        mockStatic(
                                ProcessSQSMessage.class,
                                Mockito.CALLS_REAL_METHODS)) {

            processMock.when(ProcessSQSMessage::getSecretManagerName)
                    .thenReturn("test-secret");

            processMock.when(ProcessSQSMessage::createS3Client)
                    .thenReturn(s3Client);

            processMock.when(ProcessSQSMessage::getClamAvBucketName)
                .thenReturn("clam-bucket");

            secretMock.when(
                    () -> RetrieveSecret.RetrieveSecretValue(anyString()))
                    .thenReturn("\"client\"\"password\"");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getAccessToken(
                            anyString(),
                            anyString()))
                    .thenReturn("token");

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileInformation(
                            anyString(),
                            anyString()))
                    .thenReturn(response);

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.setVirusScanMetadata(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class),
                            anyString()))
                    .thenReturn(true);

            wfdmMock.when(
                    () -> GetFileFromWFDMAPI.getFileStream(
                            anyString(),
                            anyString(),
                            anyString()))
                    .thenReturn(stream);

            SQSBatchResponse result =
                    handler.handleRequest(event, context);

            assertEquals(
                    0,
                    result.getBatchItemFailures().size());

            verify(s3Client)
                    .putObject(any(PutObjectRequest.class));
        }
    }

}