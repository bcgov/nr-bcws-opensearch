package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PropertyLoaderTest {

    @Test
    void shouldInstantiateClass() {

        PropertyLoader loader = new PropertyLoader();

        assertNotNull(loader);
    }

    @Test
    void shouldReturnPropertyOrNull() {

        String value =
                PropertyLoader.getProperty(
                        "some.key");

        assertTrue(
                value == null || !value.isEmpty());
    }
}