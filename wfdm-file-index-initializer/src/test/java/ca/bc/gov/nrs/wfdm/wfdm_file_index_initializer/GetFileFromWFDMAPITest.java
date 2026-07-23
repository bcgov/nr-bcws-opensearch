package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class GetFileFromWFDMAPITest {

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
                result.getString("metadataName"));

        assertEquals(
                "PENDING",
                result.getString("metadataValue"));
    }

    @Test
    void shouldReplaceExistingVirusMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put("metadataName", "WFDMScanStatus-1");
        existingMeta.put("metadataValue", "FAILED");

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
                result.getString("metadataName"));

        assertEquals(
                "PENDING",
                result.getString("metadataValue"));
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
                result.getString("metadataName"));

        assertEquals(
                "UPDATED",
                result.getString("metadataValue"));
    }

    @Test
    void shouldReplaceExistingImageConversionMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put("metadataName", "WFDMConversionStatus-5");
        existingMeta.put("metadataValue", "FAILED");

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
                result.getString("metadataName"));

        assertEquals(
                "UPDATED",
                result.getString("metadataValue"));
    }

    @Test
    void shouldKeepExistingUnrelatedVirusMetadata() {

        JSONObject existingMeta = new JSONObject();
        existingMeta.put("metadataName", "SomeOtherMetadata");
        existingMeta.put("metadataValue", "ABC");

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
        existingMeta.put("metadataName", "SomeOtherMetadata");
        existingMeta.put("metadataValue", "ABC");

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
}