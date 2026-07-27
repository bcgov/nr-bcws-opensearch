package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

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
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;

public class GetFileFromWFDMAPITest {
    
    @Test
    void shouldInstantiatePrivateConstructorViaReflection() throws Exception {

        Constructor<GetFileFromWFDMAPI> constructor = GetFileFromWFDMAPI.class.getDeclaredConstructor();

        constructor.setAccessible(true);
        constructor.newInstance();

        assertEquals(GetFileFromWFDMAPI.class, constructor.getDeclaringClass());
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
    void shouldCreateMetadataObject() {

        JSONObject result =
                GetFileFromWFDMAPI.addMeta(
                        "Title",
                        "Sample");

        assertEquals(
                "Title",
                result.getString("metadataName"));

        assertEquals(
                "Sample",
                result.getString("metadataValue"));

        assertEquals(
                "http://resources.wfdm.nrs.gov.bc.ca/fileMetadataResource",
                result.getString("@type"));
    }

    @Test
    void shouldAddDefaultMetadataWhenMissing()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("uploadedBy", "cbergin");
            fileDetails.put("metadata", new JSONArray());

            JSONArray versions = new JSONArray();

            JSONObject versionOne = new JSONObject();
            versionOne.put("versionNumber", 1);
            versionOne.put(
                    "uploadedOnTimestamp",
                    "2024-01-01T10:11:12.123456");

            versions.put(versionOne);

            fileDetails.put("versions", versions);

            var request = mock(com.mashape.unirest.request.HttpRequestWithBody.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            RequestBodyEntity bodyEntity = mock(RequestBodyEntity.class);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            boolean result =
                    GetFileFromWFDMAPI.setIndexedMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "etag");

            assertTrue(result);

            JSONArray metadata =
                    fileDetails.getJSONArray("metadata");

            assertTrue(
                    metadata.toString().contains("Creator"));

            assertTrue(
                    metadata.toString().contains("UploadedBy"));

            assertTrue(
                    metadata.toString().contains("Title"));

            assertTrue(
                    metadata.toString()
                            .contains("WFDMIndexVersion-1"));

            assertTrue(
                    metadata.toString()
                            .contains("WFDMIndexDate-1"));
        }
    }

    @Test
    void shouldFormatDateCreatedFromVersionOneTimestamp()
            throws Exception {

        JSONObject fileDetails = new JSONObject();

        fileDetails.put("uploadedBy", "user1");
        fileDetails.put("metadata", new JSONArray());

        JSONArray versions = new JSONArray();

        JSONObject version = new JSONObject();

        version.put("versionNumber", 1);

        version.put(
                "uploadedOnTimestamp",
                "2024-02-03T11:22:33.123456");

        versions.put(version);

        fileDetails.put("versions", versions);

        JSONArray metadata =
                fileDetails.getJSONArray("metadata");

        metadata.put(
                GetFileFromWFDMAPI.addMeta(
                        "Creator",
                        "value"));

        JSONObject versionMeta =
                GetFileFromWFDMAPI.addMeta(
                        "DateCreated",
                        "2024-02-03 11:22:33");

        assertNotNull(versionMeta);
    }

    @Test
    void shouldKeepRawTimestampWhenDateParsingFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONObject fileDetails = new JSONObject();

            fileDetails.put("uploadedBy", "user1");
            fileDetails.put("metadata", new JSONArray());

            JSONArray versions = new JSONArray();

            JSONObject version = new JSONObject();
            version.put("versionNumber", 1);
            version.put("uploadedOnTimestamp", "BAD_DATE");

            versions.put(version);

            fileDetails.put("versions", versions);

            var request =
                    mock(com.mashape.unirest.request.HttpRequestWithBody.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            RequestBodyEntity bodyEntity = mock(RequestBodyEntity.class);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            GetFileFromWFDMAPI.setIndexedMetadata(
                    "token",
                    "123",
                    "1",
                    fileDetails,
                    "etag");

            assertTrue(
                    fileDetails.toString().contains("BAD_DATE"));
        }
    }

    @Test
    void shouldReturnFalseWhenMetadataUpdateFails()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("metadata", new JSONArray());

            var request =
                    mock(com.mashape.unirest.request.HttpRequestWithBody.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            RequestBodyEntity bodyEntity = mock(RequestBodyEntity.class);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(request.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(500);

            boolean result =
                    GetFileFromWFDMAPI.setIndexedMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "etag");

            assertFalse(result);
        }
    }

    @Test
    void shouldNotDuplicateExistingMetadata()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONArray metadata = new JSONArray();

            metadata.put(GetFileFromWFDMAPI.addMeta("Creator", "user"));
            metadata.put(GetFileFromWFDMAPI.addMeta("UploadedBy", "user"));
            metadata.put(GetFileFromWFDMAPI.addMeta("Title", "title"));
            metadata.put(GetFileFromWFDMAPI.addMeta("DateCreated", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("Description", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("Format", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("UniqueIdentifier", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("InformationSchedule", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("SecurityClassification", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("OPR", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("IncidentNumber", "value"));
            metadata.put(GetFileFromWFDMAPI.addMeta("AppAcronym", "value"));

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("uploadedBy", "user");
            fileDetails.put("metadata", metadata);

            HttpRequestWithBody request =
                    mock(HttpRequestWithBody.class);

            RequestBodyEntity bodyEntity =
                    mock(RequestBodyEntity.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(
                    () -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            GetFileFromWFDMAPI.setIndexedMetadata(
                    "token",
                    "123",
                    "1",
                    fileDetails,
                    "etag");

            JSONArray metadataResult =
                    fileDetails.getJSONArray("metadata");

            boolean foundCreator = false;
            boolean foundUploadedBy = false;
            boolean foundTitle = false;
            boolean foundDateCreated = false;
            boolean foundDateModified = false;
            boolean foundDescription = false;
            boolean foundFormat = false;
            boolean foundUniqueIdentifier = false;
            boolean foundInformationSchedule = false;
            boolean foundSecurityClassification = false;
            boolean foundOpr = false;
            boolean foundIncidentNumber = false;
            boolean foundAppAcronym = false;
            boolean foundIndexVersion = false;
            boolean foundIndexDate = false;

            for (int i = 0; i < metadataResult.length(); i++) {

                String metadataName =
                        metadataResult.getJSONObject(i)
                                .getString("metadataName");

                if ("Creator".equals(metadataName)) {
                    foundCreator = true;
                }

                if ("UploadedBy".equals(metadataName)) {
                    foundUploadedBy = true;
                }

                if ("Title".equals(metadataName)) {
                    foundTitle = true;
                }

                if ("DateCreated".equals(metadataName)) {
                    foundDateCreated = true;
                }

                if ("DateModified".equals(metadataName)) {
                    foundDateModified = true;
                }

                if ("Description".equals(metadataName)) {
                    foundDescription = true;
                }

                if ("Format".equals(metadataName)) {
                    foundFormat = true;
                }

                if ("UniqueIdentifier".equals(metadataName)) {
                    foundUniqueIdentifier = true;
                }

                if ("InformationSchedule".equals(metadataName)) {
                    foundInformationSchedule = true;
                }

                if ("SecurityClassification".equals(metadataName)) {
                    foundSecurityClassification = true;
                }

                if ("OPR".equals(metadataName)) {
                    foundOpr = true;
                }

                if ("IncidentNumber".equals(metadataName)) {
                    foundIncidentNumber = true;
                }

                if ("AppAcronym".equals(metadataName)) {
                    foundAppAcronym = true;
                }

                if ("WFDMIndexVersion-1".equals(metadataName)) {
                    foundIndexVersion = true;
                }

                if ("WFDMIndexDate-1".equals(metadataName)) {
                    foundIndexDate = true;
                }
            }

            assertTrue(foundCreator);
            assertTrue(foundUploadedBy);
            assertTrue(foundTitle);
            assertTrue(foundDateCreated);
            assertTrue(foundDateModified);
            assertTrue(foundDescription);
            assertTrue(foundFormat);
            assertTrue(foundUniqueIdentifier);
            assertTrue(foundInformationSchedule);
            assertTrue(foundSecurityClassification);
            assertTrue(foundOpr);
            assertTrue(foundIncidentNumber);
            assertTrue(foundAppAcronym);
            assertTrue(foundIndexVersion);
            assertTrue(foundIndexDate);
        }
    }

    @Test
    void shouldReplaceExistingIndexMetadata()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONArray metadata = new JSONArray();

            metadata.put(
                    GetFileFromWFDMAPI.addMeta(
                            "WFDMIndexVersion-1",
                            "true"));

            metadata.put(
                    GetFileFromWFDMAPI.addMeta(
                            "WFDMIndexDate-1",
                            "old"));

            metadata.put(
                    GetFileFromWFDMAPI.addMeta(
                            "wfdm-indexed-v1",
                            "true"));

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("uploadedBy", "user");
            fileDetails.put("metadata", metadata);

            HttpRequestWithBody request =
                    mock(HttpRequestWithBody.class);

            RequestBodyEntity bodyEntity =
                    mock(RequestBodyEntity.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(() -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            GetFileFromWFDMAPI.setIndexedMetadata(
                    "token",
                    "123",
                    "1",
                    fileDetails,
                    "etag");

            String metadataString =
                    fileDetails.getJSONArray("metadata").toString();

            assertTrue(metadataString.contains("WFDMIndexVersion-1"));
            assertTrue(metadataString.contains("WFDMIndexDate-1"));
        }
    }

    @Test
    void shouldReplaceNullCreatorAndUploadedByMetadata()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONArray metadata = new JSONArray();

            metadata.put(GetFileFromWFDMAPI.addMeta("Creator", "null"));
            metadata.put(GetFileFromWFDMAPI.addMeta("UploadedBy", "null"));

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("uploadedBy", "actualUser");
            fileDetails.put("metadata", metadata);

            HttpRequestWithBody request =
                    mock(HttpRequestWithBody.class);

            RequestBodyEntity bodyEntity =
                    mock(RequestBodyEntity.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(() -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            boolean result =
                    GetFileFromWFDMAPI.setIndexedMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "etag");

            assertTrue(result);
        }
    }

    @Test
    void shouldUseNullDateCreatedWhenVersionOneMissing()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONObject fileDetails = new JSONObject();
            fileDetails.put("uploadedBy", "user");
            fileDetails.put("metadata", new JSONArray());

            JSONArray versions = new JSONArray();

            JSONObject version = new JSONObject();
            version.put("versionNumber", 2);

            versions.put(version);

            fileDetails.put("versions", versions);

            HttpRequestWithBody request =
                    mock(HttpRequestWithBody.class);

            RequestBodyEntity bodyEntity =
                    mock(RequestBodyEntity.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(() -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            GetFileFromWFDMAPI.setIndexedMetadata(
                    "token",
                    "123",
                    "1",
                    fileDetails,
                    "etag");

            assertTrue(
                    fileDetails.toString().contains("DateCreated"));
        }
    }

    @Test
    void shouldHandleNullUploadedBy()
            throws Exception {

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class);
            MockedStatic<GetFileFromWFDMAPI> apiMock =
                    mockStatic(
                            GetFileFromWFDMAPI.class,
                            Mockito.CALLS_REAL_METHODS);
            MockedStatic<PropertyLoader> propertyMock =
                    mockStatic(PropertyLoader.class)) {

            apiMock.when(GetFileFromWFDMAPI::getApiUrl)
                    .thenReturn("https://api/");

            propertyMock.when(
                    () -> PropertyLoader.getProperty(anyString()))
                    .thenReturn("https://api/");

            JSONObject fileDetails = new JSONObject();

            fileDetails.put("uploadedBy", JSONObject.NULL);
            fileDetails.put("metadata", new JSONArray());

            HttpRequestWithBody request =
                    mock(HttpRequestWithBody.class);

            RequestBodyEntity bodyEntity =
                    mock(RequestBodyEntity.class);

            @SuppressWarnings("unchecked")
            HttpResponse<String> response =
                    mock(HttpResponse.class);

            unirestMock.when(() -> Unirest.put(anyString()))
                    .thenReturn(request);

            when(request.header(anyString(), anyString()))
                    .thenReturn(request);

            when(request.body(anyString()))
                    .thenReturn(bodyEntity);

            when(bodyEntity.asString())
                    .thenReturn(response);

            when(response.getStatus())
                    .thenReturn(200);

            boolean result =
                    GetFileFromWFDMAPI.setIndexedMetadata(
                            "token",
                            "123",
                            "1",
                            fileDetails,
                            "etag");

            assertTrue(result);
        }
    }
}
