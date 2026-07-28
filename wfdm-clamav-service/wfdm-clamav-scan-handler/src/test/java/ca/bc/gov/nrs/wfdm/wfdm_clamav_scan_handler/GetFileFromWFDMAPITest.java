package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;

class GetFileFromWFDMAPITest {
    private static final String METADATA_NAME = "metadataName";
    private static final String METADATA_VALUE = "metadataValue";

    @Test
    void shouldInstantiatePrivateConstructor()
            throws Exception {

        Constructor<GetFileFromWFDMAPI> constructor =
                GetFileFromWFDMAPI.class.getDeclaredConstructor();

        constructor.setAccessible(true);

        GetFileFromWFDMAPI instance =
                constructor.newInstance();

        assertNotNull(instance);
    }

    @Test
    void shouldReturnTrueWhenMetadataUpdateSucceeds()
            throws Exception {

        JSONObject fileDetails =
                new JSONObject();

        JSONArray metadata =
                new JSONArray();

        fileDetails.put(
                "metadata",
                metadata);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        when(response.getStatus())
                .thenReturn(200);

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                     mockStatic(
                             GetFileFromWFDMAPI.class,
                             CALLS_REAL_METHODS)) {

            mocked.when(
                    () -> GetFileFromWFDMAPI.executeMetadataUpdate(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class)))
                    .thenReturn(response);

            boolean result =
                    GetFileFromWFDMAPI.setVirusScanMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "CLEAN",
                            "etag");

            assertTrue(result);

            assertTrue(
                    metadata.length() >= 2);
        }
    }

    @Test
    void shouldReturnFalseWhenMetadataUpdateFails()
            throws Exception {

        JSONObject fileDetails =
                new JSONObject();

        fileDetails.put(
                "metadata",
                new JSONArray());

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        when(response.getStatus())
                .thenReturn(500);

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                     mockStatic(
                             GetFileFromWFDMAPI.class,
                             CALLS_REAL_METHODS)) {

            mocked.when(
                    () -> GetFileFromWFDMAPI.executeMetadataUpdate(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class)))
                    .thenReturn(response);

            boolean result =
                    GetFileFromWFDMAPI.setVirusScanMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "INFECTED",
                            "etag");

            assertFalse(result);
        }
    }

    @Test
    void shouldReplaceExistingScanStatusMetadata()
            throws Exception {

        JSONObject fileDetails =
                new JSONObject();

        JSONArray metadata =
                new JSONArray();

        JSONObject existing =
                new JSONObject();

        existing.put(
                METADATA_NAME,
                "WFDMScanStatus-1");

        existing.put(
                METADATA_VALUE,
                "OLD");

        metadata.put(existing);

        fileDetails.put(
                "metadata",
                metadata);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        when(response.getStatus())
                .thenReturn(200);

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                     mockStatic(
                             GetFileFromWFDMAPI.class,
                             CALLS_REAL_METHODS)) {

            mocked.when(
                    () -> GetFileFromWFDMAPI.executeMetadataUpdate(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class)))
                    .thenReturn(response);

            assertTrue(
                    GetFileFromWFDMAPI.setVirusScanMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "CLEAN",
                            "etag"));
        }
    }

    @Test
    void shouldReplaceExistingVirusScanDateMetadata()
            throws Exception {

        JSONObject fileDetails =
                new JSONObject();

        JSONArray metadata =
                new JSONArray();

        JSONObject existing =
                new JSONObject();

        existing.put(
                METADATA_NAME,
                "WFDMVirusScanDate-1");

        existing.put(
                METADATA_VALUE,
                "OLD");

        metadata.put(existing);

        fileDetails.put(
                "metadata",
                metadata);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        when(response.getStatus())
                .thenReturn(200);

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                     mockStatic(
                             GetFileFromWFDMAPI.class,
                             CALLS_REAL_METHODS)) {

            mocked.when(
                    () -> GetFileFromWFDMAPI.executeMetadataUpdate(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class)))
                    .thenReturn(response);

            assertTrue(
                    GetFileFromWFDMAPI.setVirusScanMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "CLEAN",
                            "etag"));
        }
    }

    @Test
    void shouldReturnTokenUrl() {

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                mockStatic(
                        GetFileFromWFDMAPI.class,
                        CALLS_REAL_METHODS)) {

            mocked.when(
                    GetFileFromWFDMAPI::getTokenUrl)
                    .thenReturn("http://token");

            assertEquals(
                    "http://token",
                    GetFileFromWFDMAPI.getTokenUrl());
        }
    }

    @Test
    void shouldReturnApiUrl() {

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                mockStatic(
                        GetFileFromWFDMAPI.class,
                        CALLS_REAL_METHODS)) {

            mocked.when(
                    GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("http://api");

            assertEquals(
                    "http://api",
                    GetFileFromWFDMAPI.getApiUrl());
        }
    }

    @Test
    void shouldKeepExistingUnrelatedMetadata()
            throws Exception {

        JSONObject fileDetails =
                new JSONObject();

        JSONArray metadata =
                new JSONArray();

        JSONObject existing =
                new JSONObject();

        existing.put(
                METADATA_NAME,
                "SomeOtherMetadata");

        existing.put(
                METADATA_VALUE,
                "ABC");

        metadata.put(existing);

        fileDetails.put(
                "metadata",
                metadata);

        @SuppressWarnings("unchecked")
        HttpResponse<String> response =
                mock(HttpResponse.class);

        when(response.getStatus())
                .thenReturn(200);

        try (MockedStatic<GetFileFromWFDMAPI> mocked =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            CALLS_REAL_METHODS)) {

            mocked.when(
                    () -> GetFileFromWFDMAPI.executeMetadataUpdate(
                            anyString(),
                            anyString(),
                            anyString(),
                            any(JSONObject.class)))
                    .thenReturn(response);

            assertTrue(
                    GetFileFromWFDMAPI.setVirusScanMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "CLEAN",
                            "etag"));

            assertEquals(
                    3,
                    fileDetails
                            .getJSONArray("metadata")
                            .length());
        }
    }

    @Test
    void shouldReturnAccessTokenWhenRequestSucceeds()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock =
                        mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                        mockStatic(
                                GetFileFromWFDMAPI.class,
                                CALLS_REAL_METHODS)) {

            apiMock.when(
                    GetFileFromWFDMAPI::getTokenUrl)
                    .thenReturn("https://token");

            com.mashape.unirest.request.GetRequest request =
                    mock(com.mashape.unirest.request.GetRequest.class);

            @SuppressWarnings("unchecked")
            HttpResponse<JsonNode> response =
                    mock(HttpResponse.class);

            JsonNode jsonNode =
                    mock(JsonNode.class);

            JSONObject json =
                    new JSONObject();

            json.put(
                    "access_token",
                    "abc123");

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.basicAuth(
                    anyString(),
                    anyString()))
                    .thenReturn(request);

            when(request.asJson())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            when(response.getBody())
                    .thenReturn(jsonNode);

            when(jsonNode.getObject())
                    .thenReturn(json);

            assertEquals(
                    "abc123",
                    GetFileFromWFDMAPI.getAccessToken(
                            "client",
                            "password"));
        }
    }

    @Test
    void shouldReturnNullWhenAccessTokenRequestFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock =
                        mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                        mockStatic(
                                GetFileFromWFDMAPI.class,
                                CALLS_REAL_METHODS)) {

            apiMock.when(
                    GetFileFromWFDMAPI::getTokenUrl)
                    .thenReturn("https://token");

            com.mashape.unirest.request.GetRequest request =
                    mock(com.mashape.unirest.request.GetRequest.class);

            @SuppressWarnings("unchecked")
            HttpResponse<JsonNode> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.basicAuth(
                    anyString(),
                    anyString()))
                    .thenReturn(request);

            when(request.asJson())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(401);

            assertNull(
                    GetFileFromWFDMAPI.getAccessToken(
                            "client",
                            "password"));
        }
    }

    @Test
    void shouldReturnFileInformationWhenRequestSucceeds()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock =
                        mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                        mockStatic(
                                GetFileFromWFDMAPI.class,
                                CALLS_REAL_METHODS)) {

            apiMock.when(
                    GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            com.mashape.unirest.request.GetRequest request =
                    mock(com.mashape.unirest.request.GetRequest.class);

            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            assertEquals(
                    response,
                    GetFileFromWFDMAPI.getFileInformation(
                            "token",
                            "123"));
        }
    }

    @Test
    void shouldReturnNullWhenFileInformationRequestFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock =
                        mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                        mockStatic(
                                GetFileFromWFDMAPI.class,
                                CALLS_REAL_METHODS)) {

            apiMock.when(
                    GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            com.mashape.unirest.request.GetRequest request =
                    mock(com.mashape.unirest.request.GetRequest.class);

            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.get(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(404);

            assertNull(
                    GetFileFromWFDMAPI.getFileInformation(
                            "token",
                            "123"));
        }
    }
}