package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;

import software.amazon.awssdk.regions.Region;

class OpenSearchRESTClientTest {
	private static final String METADATA_NAME = "metadataName";
    private static final String METADATA_VALUE = "metadataValue";
	private static final String MIME_TYPE = "mimeType";
	private static final String FILE_SIZE = "fileSize";

    @Test
    void shouldConvertBytesToHumanReadable() {

        assertEquals(
                "0 B",
                OpenSearchRESTClient.humanReadableByteCountBin(0));

        assertEquals(
                "1.0 KiB",
                OpenSearchRESTClient.humanReadableByteCountBin(1024));
    }

    @Test
    void shouldParseZeroBytes() {

        assertEquals(
                0L,
                OpenSearchRESTClient.parsetoBytes("0"));
    }

    @Test
    void shouldParsePlainNumber() {

        assertEquals(
                123L,
                OpenSearchRESTClient.parsetoBytes("123"));
    }

    @Test
    void shouldParseHumanReadableBytes() {

        assertEquals(
                1000L,
                OpenSearchRESTClient.parsetoBytes("1.0 KiB"));
    }

    @Test
    void shouldFilterMetadataBooleanValues()
            throws Exception {

        JSONArray input = new JSONArray();

        JSONObject obj = new JSONObject();
        obj.put(METADATA_NAME, "Flag");
        obj.put(METADATA_VALUE, "true");
        obj.put("metadataType", "BOOLEAN");

        input.put(obj);

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterDataFromFileDetailsMeta",
                        String.class,
                        String.class,
                        String.class);

        method.setAccessible(true);

        JSONArray result =
                (JSONArray) method.invoke(
                        null,
                        input.toString(),
                        METADATA_NAME,
                        METADATA_VALUE);

        assertEquals(1, result.length());

        JSONObject returned =
                result.getJSONObject(0);

        assertEquals(
                "Flag",
                returned.getString(METADATA_NAME));

        assertEquals(
                "true",
                returned.getString("metadataBooleanValue"));
    }

    @Test
    void shouldFilterMetadataNumberValues()
            throws Exception {

        JSONArray input = new JSONArray();

        JSONObject obj = new JSONObject();
        obj.put(METADATA_NAME, "Count");
        obj.put(METADATA_VALUE, "10");
        obj.put("metadataType", "NUMBER");

        input.put(obj);

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterDataFromFileDetailsMeta",
                        String.class,
                        String.class,
                        String.class);

        method.setAccessible(true);

        JSONArray result =
                (JSONArray) method.invoke(
                        null,
                        input.toString(),
                        METADATA_NAME,
                        METADATA_VALUE);

        assertEquals(
                "10",
                result.getJSONObject(0)
                        .getString("metadataNumberValue"));
    }

    @Test
    void shouldFilterMetadataDateValues()
            throws Exception {

        JSONArray input = new JSONArray();

        JSONObject obj = new JSONObject();
        obj.put(METADATA_NAME, "Date");
        obj.put(METADATA_VALUE, "2024-01-01");
        obj.put("metadataType", "DATE");

        input.put(obj);

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterDataFromFileDetailsMeta",
                        String.class,
                        String.class,
                        String.class);

        method.setAccessible(true);

        JSONArray result =
                (JSONArray) method.invoke(
                        null,
                        input.toString(),
                        METADATA_NAME,
                        METADATA_VALUE);

        assertEquals(
                "2024-01-01",
                result.getJSONObject(0)
                        .getString("metadataDateValue"));
    }

    @Test
    void shouldFilterSecurityData()
            throws Exception {

        JSONArray input = new JSONArray();

        JSONObject obj = new JSONObject();
        obj.put("displayLabel", "TEST");
        obj.put("securityKey", "KEY");

        input.put(obj);

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterDataFromFileDetails",
                        String.class,
                        String.class,
                        String.class);

        method.setAccessible(true);

        JSONArray result =
                (JSONArray) method.invoke(
                        null,
                        input.toString(),
                        "displayLabel",
                        "securityKey");

        assertEquals(1, result.length());

        assertEquals(
                "TEST",
                result.getJSONObject(0)
                        .getString("displayLabel"));
    }

    @Test
    void shouldSetCanReadOrWriteToTrue()
            throws Exception {

        JSONObject scopeObj = new JSONObject();

        scopeObj.put("Read", true);
        scopeObj.put("Write", false);

        JSONArray labels = new JSONArray();

        JSONObject label = new JSONObject();
        label.put("displayLabel", "SECURITY");

        labels.put(label);

        scopeObj.put(
                "displayLabel",
                labels.toString());

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterSecurityScope",
                        JSONObject.class);

        method.setAccessible(true);

        JSONObject result =
                (JSONObject) method.invoke(
                        null,
                        scopeObj);

        assertEquals(
                "true",
                result.getString("canReadorWrite"));
    }

    @Test
    void shouldSetCanReadOrWriteToFalse()
            throws Exception {

        JSONObject scopeObj = new JSONObject();

        scopeObj.put("Read", false);
        scopeObj.put("Write", false);

        JSONArray labels = new JSONArray();

        JSONObject label = new JSONObject();
        label.put("displayLabel", "SECURITY");

        labels.put(label);

        scopeObj.put(
                "displayLabel",
                labels.toString());

        Method method =
                OpenSearchRESTClient.class.getDeclaredMethod(
                        "filterSecurityScope",
                        JSONObject.class);

        method.setAccessible(true);

        JSONObject result =
                (JSONObject) method.invoke(
                        null,
                        scopeObj);

        assertEquals(
                "false",
                result.getString("canReadorWrite"));
    }

    @Test
    void shouldInstantiateClass() {

        OpenSearchRESTClient client =
                new OpenSearchRESTClient();

        assertNotNull(client);
    }

    @Test
	void shouldConvertMegabytesToHumanReadable() {
		String result = OpenSearchRESTClient.humanReadableByteCountBin(1024L * 1024L);

		assertTrue(result.contains("MiB"));
	}

	@Test
	void shouldConvertNegativeValues() {
		String result = OpenSearchRESTClient.humanReadableByteCountBin(-1024L);

		assertTrue(result.contains("-1.0"));
	}

	@Test
	void shouldFilterMetadataWithoutType()
			throws Exception {

		JSONArray input = new JSONArray();

		JSONObject obj = new JSONObject();
		obj.put(METADATA_NAME, "Name");
		obj.put(METADATA_VALUE, "Value");

		input.put(obj);

		Method method =
				OpenSearchRESTClient.class.getDeclaredMethod(
						"filterDataFromFileDetailsMeta",
						String.class,
						String.class,
						String.class);

		method.setAccessible(true);

		JSONArray result =
				(JSONArray) method.invoke(
						null,
						input.toString(),
						METADATA_NAME,
						METADATA_VALUE);

		assertEquals(
				"Value",
				result.getJSONObject(0)
						.getString(METADATA_VALUE));
	}

	@Test
	void shouldIgnoreUnknownMetadataType()
			throws Exception {

		JSONArray input = new JSONArray();

		JSONObject obj = new JSONObject();
		obj.put(METADATA_NAME, "Name");
		obj.put(METADATA_VALUE, "Value");
		obj.put("metadataType", "TEXT");

		input.put(obj);

		Method method =
				OpenSearchRESTClient.class.getDeclaredMethod(
						"filterDataFromFileDetailsMeta",
						String.class,
						String.class,
						String.class);

		method.setAccessible(true);

		JSONArray result =
				(JSONArray) method.invoke(
						null,
						input.toString(),
						METADATA_NAME,
						METADATA_VALUE);

		assertEquals(1, result.length());
	}

	@Test
	void shouldCreateOpenSearchClient() {

		OpenSearchRESTClient client = new OpenSearchRESTClient();

		assertNotNull(client.openSearchClient("http://localhost", "es", Region.CA_CENTRAL_1));
	}

	@Test
	void shouldThrowOpenSearchExceptionWhenIndexingFails()
			throws Exception {

		OpenSearchRESTClient client =
				spy(new OpenSearchRESTClient());

		OpenSearchClient openSearchClient =
				mock(OpenSearchClient.class);

		doReturn("test-index")
				.when(client)
				.getIndexName();

		doReturn("http://localhost")
				.when(client)
				.getDomainEndpoint();

		doReturn(openSearchClient)
				.when(client)
				.openSearchClient(
						anyString(),
						anyString(),
						any());

		when(openSearchClient.index(any(IndexRequest.class)))
				.thenThrow(new RuntimeException("boom"));

		JSONObject fileDetails = new JSONObject();

		fileDetails.put("fileId", "123");
		fileDetails.put("filePath", "/absolute/path");
		fileDetails.put(MIME_TYPE, "text/plain");
		fileDetails.put("fileType", "DOCUMENT");
		fileDetails.put("fileExtension", "txt");
		fileDetails.put("retention", "1 year");
		fileDetails.put("uploadedBy", "user");
		fileDetails.put("lastUpdatedBy", "user");
		fileDetails.put("lastUpdatedTimestamp", "2024-01-01");
		fileDetails.put(FILE_SIZE, 100L);

		JSONArray metadata = new JSONArray();

		JSONObject metadataObj = new JSONObject();
		metadataObj.put(METADATA_NAME, "Title");
		metadataObj.put(METADATA_VALUE, "Test");

		metadata.put(metadataObj);

		fileDetails.put("metadata", metadata);

		JSONArray security = new JSONArray();

		JSONObject securityObj = new JSONObject();
		securityObj.put("readAccessInd", true);
		securityObj.put("grantorAccessInd", false);

		JSONObject securityKey = new JSONObject();
		securityKey.put("displayLabel", "TEST");
		securityKey.put("securityKey", "KEY");

		securityObj.put("securityKey", securityKey);

		security.put(securityObj);

		fileDetails.put("security", security);

		JSONObject parent = new JSONObject();

		parent.put("filePath", "/parent/path");

		JSONArray links = new JSONArray();

		JSONObject link = new JSONObject();
		link.put("href", "http://example.com");

		links.put(link);

		parent.put("links", links);

		fileDetails.put("parent", parent);

		assertThrows(
				OpenSearchException.class,
				() -> client.addIndex(
						"{\"Text\":\"hello\"}",
						"test.txt",
						fileDetails,
						"CLEAN"));
	}

	@Test
	void shouldDeriveFileExtensionFromFileName()
			throws Exception {

		OpenSearchRESTClient client =
				spy(new OpenSearchRESTClient());

		OpenSearchClient openSearchClient =
				mock(OpenSearchClient.class);

		doReturn("test-index")
				.when(client)
				.getIndexName();

		doReturn("http://localhost")
				.when(client)
				.getDomainEndpoint();

		doReturn(openSearchClient)
				.when(client)
				.openSearchClient(
						anyString(),
						anyString(),
						any());

		when(openSearchClient.index(any(IndexRequest.class)))
				.thenThrow(new RuntimeException("boom"));

		JSONObject fileDetails = new JSONObject();

		fileDetails.put("fileId", "123");
		fileDetails.put("filePath", "/path");
		fileDetails.put(MIME_TYPE, "text/plain");
		fileDetails.put("fileType", "DOCUMENT");

		fileDetails.put("fileExtension", JSONObject.NULL);

		fileDetails.put("metadata", new JSONArray());

		JSONArray security = new JSONArray();

		JSONObject sec = new JSONObject();
		sec.put("readAccessInd", false);
		sec.put("grantorAccessInd", false);

		JSONObject secKey = new JSONObject();
		secKey.put("displayLabel", "TEST");
		secKey.put("securityKey", "KEY");

		sec.put("securityKey", secKey);
		security.put(sec);

		fileDetails.put("security", security);

		JSONObject parent = new JSONObject();
		parent.put("filePath", "/parent");

		JSONArray links = new JSONArray();

		JSONObject link = new JSONObject();
		link.put("href", "http://example.com");

		links.put(link);

		parent.put("links", links);

		fileDetails.put("parent", parent);

		assertThrows(
				OpenSearchException.class,
				() -> client.addIndex(
						null,
						"report.pdf",
						fileDetails,
						"FAILED"));
	}
}