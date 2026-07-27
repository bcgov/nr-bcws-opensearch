package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PropertyLoaderTest {

    @Test
    void shouldReturnPropertyValueOrNull() {

        String result =
                PropertyLoader.getProperty(
                        "wfdm.document.api.url");

        // Property may exist or may not depending on test resources.
        // We only care that method executes without exception.

        assertNotNull(result);
    }

    @Test
    void shouldReturnNullForUnknownProperty() {

        String result =
                PropertyLoader.getProperty(
                        "this.property.does.not.exist");

        assertNull(result);
    }
}