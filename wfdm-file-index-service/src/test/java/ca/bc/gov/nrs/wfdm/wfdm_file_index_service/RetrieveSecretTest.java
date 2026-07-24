package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

class RetrieveSecretTest {

    @Test
    void shouldReturnSecretValue() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        GetSecretValueResult result =
                new GetSecretValueResult();

        result.setSecretString("secret-value");

        when(client.getSecretValue(any()))
                .thenReturn(result);

        String secret =
                RetrieveSecret.getValue(
                        client,
                        "my-secret");

        assertEquals(
                "secret-value",
                secret);
    }

    @Test
    void shouldReturnNullWhenSecretStringMissing() {

        AWSSecretsManager client =
                mock(AWSSecretsManager.class);

        GetSecretValueResult result =
                new GetSecretValueResult();

        when(client.getSecretValue(any()))
                .thenReturn(result);

        String secret =
                RetrieveSecret.getValue(
                        client,
                        "my-secret");

        assertNull(secret);
    }

    @Test
    void shouldRetrieveSecretValue() {

        try (
            MockedStatic<AWSSecretsManagerClientBuilder> builderMock =
                mockStatic(AWSSecretsManagerClientBuilder.class);
            MockedStatic<RetrieveSecret> secretMock =
                mockStatic(
                    RetrieveSecret.class,
                    Mockito.CALLS_REAL_METHODS)
        ) {

            AWSSecretsManagerClientBuilder builder =
                    mock(AWSSecretsManagerClientBuilder.class);

            AWSSecretsManager client =
                    mock(AWSSecretsManager.class);

            builderMock.when(
                    AWSSecretsManagerClientBuilder::standard)
                    .thenReturn(builder);

            when(builder.withRegion("ca-central-1"))
                    .thenReturn(builder);

            when(builder.build())
                    .thenReturn(client);

            secretMock.when(
                    () -> RetrieveSecret.getValue(
                            client,
                            "secret"))
                    .thenReturn("value");

            String result =
                    RetrieveSecret.RetrieveSecretValue(
                            "secret");

            assertEquals(
                    "value",
                    result);
        }
    }
}