package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OpenSearchExceptionTest {

    @Test
    void shouldCreateExceptionWithCause() {

        Throwable cause =
                new RuntimeException("boom");

        OpenSearchException exception =
                new OpenSearchException(cause);

        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {

        Throwable cause =
                new RuntimeException("boom");

        OpenSearchException exception =
                new OpenSearchException(
                        "error message",
                        cause);

        assertEquals(
                "error message",
                exception.getMessage());

        assertEquals(
                cause,
                exception.getCause());
    }
}