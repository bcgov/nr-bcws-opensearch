package ca.bc.gov.nrs.wfdm.wfdm_clamav_scan_handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;

class RetrieveSecretTest {

    @Test
    void shouldReturnSecretValue() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        GetSecretValueResult result =
                new GetSecretValueResult()
                        .withSecretString("secret");

        when(client.getSecretValue(any()))
                .thenReturn(result);

        String value =
                RetrieveSecret.getValue(
                        client,
                        "test");

        assertEquals(
                "secret",
                value);
    }

    @Test
    void shouldReturnNullWhenSecretStringMissing() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        GetSecretValueResult result =
                new GetSecretValueResult();

        when(client.getSecretValue(any()))
                .thenReturn(result);

        assertNull(
                RetrieveSecret.getValue(
                        client,
                        "test"));
    }

    @Test
    void shouldThrowResourceNotFoundException() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        when(client.getSecretValue(any()))
                .thenThrow(
                        new ResourceNotFoundException(
                                "missing"));

        assertThrows(
                ResourceNotFoundException.class,
                () -> RetrieveSecret.getValue(
                        client,
                        "test"));
    }

    @Test
    void shouldThrowInvalidRequestException() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        when(client.getSecretValue(any()))
                .thenThrow(
                        new com.amazonaws.services.secretsmanager.model.InvalidRequestException(
                                "bad"));

        assertThrows(
                com.amazonaws.services.secretsmanager.model.InvalidRequestException.class,
                () -> RetrieveSecret.getValue(
                        client,
                        "test"));
    }

    @Test
    void shouldThrowInternalServiceException() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        when(client.getSecretValue(any()))
                .thenThrow(
                        new com.amazonaws.services.secretsmanager.model.InternalServiceErrorException(
                                "error"));

        assertThrows(
                com.amazonaws.services.secretsmanager.model.InternalServiceErrorException.class,
                () -> RetrieveSecret.getValue(
                        client,
                        "test"));
    }
}