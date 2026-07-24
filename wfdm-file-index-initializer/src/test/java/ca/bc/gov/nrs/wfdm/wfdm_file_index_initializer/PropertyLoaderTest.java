package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PropertyLoaderTest {

    @Test
    void shouldReturnNullWhenPropertiesFileDoesNotExist() {

        String value = PropertyLoader.getProperty("anyKey");

        assertNull(value);
    }
}