package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.GetRequest;

class GetFileFromWFDMAPITest {
    private static final String METADATA_NAME = "metadataName";
	private static final String METADATA_VALUE = "metadataValue";

    @Test
    void shouldInstantiatePrivateConstructorViaReflection() throws Exception {

        Constructor<GetFileFromWFDMAPI> constructor = GetFileFromWFDMAPI.class.getDeclaredConstructor();

        constructor.setAccessible(true);
        constructor.newInstance();

        assertEquals(GetFileFromWFDMAPI.class, constructor.getDeclaringClass());
    }

    @Test
    void shouldAddVirusScanMetadata() {

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", new JSONArray());

        GetFileFromWFDMAPI.updateVirusScanMetadata(fileDetails, "1");

        JSONArray metadata = fileDetails.getJSONArray("metadata");

        assertEquals(1, metadata.length());

        JSONObject result = metadata.getJSONObject(0);

        assertEquals(
                "WFDMScanStatus-1",
                result.getString(METADATA_NAME));

        assertEquals(
                "PENDING",
                result.getString(METADATA_VALUE));
    }

    @Test
    void shouldReplaceExistingVirusMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put(METADATA_NAME, "WFDMScanStatus-1");
        existingMeta.put(METADATA_VALUE, "FAILED");

        JSONArray metadata = new JSONArray();
        metadata.put(existingMeta);

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", metadata);

        GetFileFromWFDMAPI.updateVirusScanMetadata(fileDetails, "1");

        JSONArray resultMetadata = fileDetails.getJSONArray("metadata");

        assertEquals(1, resultMetadata.length());

        JSONObject result = resultMetadata.getJSONObject(0);

        assertEquals(
                "WFDMScanStatus-1",
                result.getString(METADATA_NAME));

        assertEquals(
                "PENDING",
                result.getString(METADATA_VALUE));
    }

    @Test
    void shouldAddImageConversionMetadata() {

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", new JSONArray());

        GetFileFromWFDMAPI.updateImageConversionMetadata(
                fileDetails,
                "5",
                "UPDATED");

        JSONArray metadata = fileDetails.getJSONArray("metadata");

        assertEquals(1, metadata.length());

        JSONObject result = metadata.getJSONObject(0);

        assertEquals(
                "WFDMConversionStatus-5",
                result.getString(METADATA_NAME));

        assertEquals(
                "UPDATED",
                result.getString(METADATA_VALUE));
    }

    @Test
    void shouldReplaceExistingImageConversionMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put(METADATA_NAME, "WFDMConversionStatus-5");
        existingMeta.put(METADATA_VALUE, "FAILED");

        JSONArray metadata = new JSONArray();
        metadata.put(existingMeta);

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", metadata);

        GetFileFromWFDMAPI.updateImageConversionMetadata(
                fileDetails,
                "5",
                "UPDATED");

        JSONArray resultMetadata = fileDetails.getJSONArray("metadata");

        assertEquals(1, resultMetadata.length());

        JSONObject result = resultMetadata.getJSONObject(0);

        assertEquals(
                "WFDMConversionStatus-5",
                result.getString(METADATA_NAME));

        assertEquals(
                "UPDATED",
                result.getString(METADATA_VALUE));
    }

    @Test
    void shouldKeepExistingUnrelatedVirusMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put(METADATA_NAME, "SomeOtherMetadata");
        existingMeta.put(METADATA_VALUE, "ABC");

        JSONArray metadata = new JSONArray();
        metadata.put(existingMeta);

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", metadata);

        GetFileFromWFDMAPI.updateVirusScanMetadata(fileDetails, "1");

        JSONArray resultMetadata = fileDetails.getJSONArray("metadata");

        assertEquals(2, resultMetadata.length());
    }

    @Test
    void shouldKeepExistingUnrelatedConversionMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put(METADATA_NAME, "SomeOtherMetadata");
        existingMeta.put(METADATA_VALUE, "ABC");

        JSONArray metadata = new JSONArray();
        metadata.put(existingMeta);

        JSONObject fileDetails = new JSONObject();
        fileDetails.put("metadata", metadata);

        GetFileFromWFDMAPI.updateImageConversionMetadata(
                fileDetails,
                "5",
                "UPDATED");

        JSONArray resultMetadata = fileDetails.getJSONArray("metadata");

        assertEquals(2, resultMetadata.length());
    }

    @Test
    void shouldReturnAccessTokenWhenRequestSucceeds()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock = mockStatic(
                        GetFileFromWFDMAPI.class,
                        Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getTokenUrl).thenReturn("https://token-url");

            GetRequest request = mock(GetRequest.class);
            HttpResponse<JsonNode> response = mock(HttpResponse.class);
            JsonNode jsonNode = mock(JsonNode.class);

            JSONObject json = new JSONObject();
            json.put("access_token", "abc123");

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.basicAuth(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asJson())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            when(response.getBody())
                    .thenReturn(jsonNode);

            when(jsonNode.getObject())
                    .thenReturn(json);

            String result =
                    GetFileFromWFDMAPI.getAccessToken(
                            "client",
                            "password");

            assertEquals("abc123", result);
        }
    }

    @Test
    void shouldReturnNullWhenAccessTokenRequestFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getTokenUrl)
                    .thenReturn("https://token-url");

            GetRequest request = mock(GetRequest.class);
            HttpResponse<JsonNode> response = mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.basicAuth(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asJson())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(401);

            String result =
                    GetFileFromWFDMAPI.getAccessToken(
                            "client",
                            "password");

            assertNull(result);
        }
    }

    @Test
    void shouldReturnFileInformationWhenRequestSucceeds()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            GetRequest request = mock(GetRequest.class);
            HttpResponse<String> response = mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            HttpResponse<String> result =
                    GetFileFromWFDMAPI.getFileInformation(
                            "token",
                            "123");

            assertEquals(response, result);
        }
    }

    @Test
    void shouldReturnNullWhenFileInformationRequestFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            GetRequest request = mock(GetRequest.class);
            HttpResponse<String> response = mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(404);

            HttpResponse<String> result =
                    GetFileFromWFDMAPI.getFileInformation(
                            "token",
                            "123");

            assertNull(result);
        }
    }

    @Test
    void shouldReturnBufferedInputStreamWhenRequestSucceeds()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            GetRequest request = mock(GetRequest.class);

            @SuppressWarnings("unchecked")
            HttpResponse<InputStream> response =
                    mock(HttpResponse.class);

            InputStream inputStream =
                    new ByteArrayInputStream("test".getBytes());

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asBinary())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            when(response.getBody())
                    .thenReturn(inputStream);

            BufferedInputStream result =
                    GetFileFromWFDMAPI.getFileStream(
                            "token",
                            "123",
                            "1");

            assertNotNull(result);
        }
    }

    @Test
    void shouldReturnNullWhenFileStreamRequestFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            GetRequest request = mock(GetRequest.class);

            @SuppressWarnings("unchecked")
            HttpResponse<InputStream> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asBinary())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(500);

            BufferedInputStream result =
                    GetFileFromWFDMAPI.getFileStream(
                            "token",
                            "123",
                            "1");

            assertNull(result);
        }
    }
}