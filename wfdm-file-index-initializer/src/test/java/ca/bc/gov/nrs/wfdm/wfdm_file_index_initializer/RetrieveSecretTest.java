package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.InvalidParameterException;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.DecryptionFailureException;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.InternalServiceErrorException;
import com.amazonaws.services.secretsmanager.model.InvalidRequestException;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;


class RetrieveSecretTest {

    @Test
    void shouldReturnSecretValue() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        GetSecretValueResult result = new GetSecretValueResult();
        result.setSecretString("test-secret");

        when(secretsClient.getSecretValue(any()))
                .thenReturn(result);

        String secret =
                RetrieveSecret.getValue(
                        secretsClient,
                        "my-secret");

        assertEquals("test-secret", secret);
    }

    @Test
    void shouldReturnNullWhenSecretStringIsNull() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        GetSecretValueResult result = new GetSecretValueResult();
        result.setSecretString(null);

        when(secretsClient.getSecretValue(any()))
                .thenReturn(result);

        String secret =
                RetrieveSecret.getValue(
                        secretsClient,
                        "my-secret");

        assertNull(secret);
    }

    @Test
    void shouldThrowResourceNotFoundException() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        when(secretsClient.getSecretValue(any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThrows(
                ResourceNotFoundException.class,
                () -> RetrieveSecret.getValue(
                        secretsClient,
                        "missing-secret"));
    }

    @Test
    void shouldThrowDecryptionFailureException() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        when(secretsClient.getSecretValue(any()))
                .thenThrow(new DecryptionFailureException("decrypt error"));

        assertThrows(
                DecryptionFailureException.class,
                () -> RetrieveSecret.getValue(
                        secretsClient,
                        "secret"));
    }

    @Test
    void shouldThrowInvalidParameterException() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        when(secretsClient.getSecretValue(any()))
                .thenThrow(new InvalidParameterException("bad parameter"));

        assertThrows(
                InvalidParameterException.class,
                () -> RetrieveSecret.getValue(
                        secretsClient,
                        "secret"));
    }

    @Test
    void shouldThrowInternalServiceErrorException() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        when(secretsClient.getSecretValue(any()))
                .thenThrow(new InternalServiceErrorException("internal error"));

        assertThrows(
                InternalServiceErrorException.class,
                () -> RetrieveSecret.getValue(
                        secretsClient,
                        "secret"));
    }

    @Test
    void shouldThrowInvalidRequestException() {

        AWSSecretsManager secretsClient = mock(AWSSecretsManager.class);

        when(secretsClient.getSecretValue(any()))
                .thenThrow(new InvalidRequestException("invalid request"));

        assertThrows(
                InvalidRequestException.class,
                () -> RetrieveSecret.getValue(
                        secretsClient,
                        "secret"));
    }


}