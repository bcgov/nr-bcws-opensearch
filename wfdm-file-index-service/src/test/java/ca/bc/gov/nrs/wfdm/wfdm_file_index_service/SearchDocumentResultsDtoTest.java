package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SearchDocumentResultsDtoTest {

    @Test
    void shouldGetAndSetAllProperties() {

        SearchDocumentResultsDto dto =
                new SearchDocumentResultsDto();

        List<Map<String, Object>> metadata =
                new ArrayList<>();

        metadata.add(new HashMap<>());

        dto.setFileId("1");
        dto.setFileName("file.txt");
        dto.setMetadata(metadata);
        dto.setKey("key");
        dto.setSecurityKey("securityKey");
        dto.setSecurity(metadata);
        dto.setSecurityScope(metadata);
        dto.setFilePath("/path");
        dto.setAbsoluteFilePath("/absolute/path");
        dto.setMimeType("text/plain");
        dto.setLastUpdatedBy("user");
        dto.setFileLink("link");
        dto.setFileSize("100");
        dto.setLastModified("today");
        dto.setFileRetention("retention");
        dto.setUploadedBy("uploadedBy");
        dto.setFileExtension("txt");
        dto.setFileSizeBytes(100L);
        dto.setFileType("document");
        dto.setFileContent("content");
        dto.setLockedInd("Y");
        dto.setRetentionTerm("term");
        dto.setUploadedOnTimestampString("timestamp");
        dto.setValidEndTimestamp("end");
        dto.setValidStartTimestamp("start");
        dto.setVersionNumber("1");
        dto.setScanStatus("CLEAN");

        assertEquals("1", dto.getFileId());
        assertEquals("file.txt", dto.getFileName());
        assertEquals(metadata, dto.getMetadata());
        assertEquals("key", dto.getKey());
        assertEquals("securityKey", dto.getSecurityKey());
        assertEquals(metadata, dto.getSecurity());
        assertEquals(metadata, dto.getSecurityScope());
        assertEquals("/path", dto.getFilePath());
        assertEquals("/absolute/path", dto.getAbsoluteFilePath());
        assertEquals("text/plain", dto.getMimeType());
        assertEquals("user", dto.getLastUpdatedBy());
        assertEquals("link", dto.getFileLink());
        assertEquals("100", dto.getFileSize());
        assertEquals("today", dto.getLastModified());
        assertEquals("retention", dto.getFileRetention());
        assertEquals("uploadedBy", dto.getUploadedBy());
        assertEquals("txt", dto.getFileExtension());
        assertEquals(100L, dto.getFileSizeBytes());
        assertEquals("document", dto.getFileType());
        assertEquals("content", dto.getFileContent());
        assertEquals("Y", dto.getLockedInd());
        assertEquals("term", dto.getRetentionTerm());
        assertEquals("timestamp", dto.getUploadedOnTimestampString());
        assertEquals("end", dto.getValidEndTimestamp());
        assertEquals("start", dto.getValidStartTimestamp());
        assertEquals("1", dto.getVersionNumber());
        assertEquals("CLEAN", dto.getScanStatus());
    }
}