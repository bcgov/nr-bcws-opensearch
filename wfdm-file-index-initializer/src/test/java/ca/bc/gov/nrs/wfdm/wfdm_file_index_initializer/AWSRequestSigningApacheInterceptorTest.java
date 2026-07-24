package ca.bc.gov.nrs.wfdm.wfdm_file_index_initializer;

import static org.apache.http.protocol.HttpCoreContext.HTTP_TARGET_HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.apache.http.HttpHost;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.Signer;

class AWSRequestSigningApacheInterceptorTest {

    @Test
    void shouldProcessAndSignRequest() throws Exception {

        Signer signer = mock(Signer.class);

        AWSCredentialsProvider credentialsProvider =
                mock(AWSCredentialsProvider.class);

        AWSCredentials credentials =
                mock(AWSCredentials.class);

        when(credentialsProvider.getCredentials())
                .thenReturn(credentials);

        AWSRequestSigningApacheInterceptor interceptor =
                new AWSRequestSigningApacheInterceptor(
                        "es",
                        signer,
                        credentialsProvider);

        BasicHttpRequest request =
                new BasicHttpRequest(
                        "GET",
                        "/test?foo=bar");

        HttpContext context =
                new BasicHttpContext();

        context.setAttribute(
                HTTP_TARGET_HOST,
                new HttpHost(
                        "localhost",
                        443,
                        "https"));

        interceptor.process(request, context);

        verify(signer)
                .sign(
                        any(DefaultRequest.class),
                        eq(credentials));
    }

    @Test
    void shouldProcessRequestWithoutHost()
            throws Exception {

        Signer signer = mock(Signer.class);

        AWSCredentialsProvider credentialsProvider =
                mock(AWSCredentialsProvider.class);

        AWSCredentials credentials =
                mock(AWSCredentials.class);

        when(credentialsProvider.getCredentials())
                .thenReturn(credentials);

        AWSRequestSigningApacheInterceptor interceptor =
                new AWSRequestSigningApacheInterceptor(
                        "es",
                        signer,
                        credentialsProvider);

        BasicHttpRequest request =
                new BasicHttpRequest(
                        "GET",
                        "/test");

        HttpContext context =
                new BasicHttpContext();

        interceptor.process(request, context);

        verify(signer)
                .sign(
                        any(DefaultRequest.class),
                        eq(credentials));
    }

    @Test
    void shouldHandleEntityRequestWithNullEntity()
            throws Exception {

        Signer signer = mock(Signer.class);

        AWSCredentialsProvider credentialsProvider =
                mock(AWSCredentialsProvider.class);

        AWSCredentials credentials =
                mock(AWSCredentials.class);

        when(credentialsProvider.getCredentials())
                .thenReturn(credentials);

        AWSRequestSigningApacheInterceptor interceptor =
                new AWSRequestSigningApacheInterceptor(
                        "es",
                        signer,
                        credentialsProvider);

        BasicHttpEntityEnclosingRequest request =
                new BasicHttpEntityEnclosingRequest(
                        "POST",
                        "/test");

        HttpContext context =
                new BasicHttpContext();

        interceptor.process(request, context);

        verify(signer)
                .sign(
                        any(DefaultRequest.class),
                        eq(credentials));
    }

    @Test
    void shouldHandleEntityRequestWithContent()
            throws Exception {

        Signer signer = mock(Signer.class);

        AWSCredentialsProvider credentialsProvider =
                mock(AWSCredentialsProvider.class);

        AWSCredentials credentials =
                mock(AWSCredentials.class);

        when(credentialsProvider.getCredentials())
                .thenReturn(credentials);

        AWSRequestSigningApacheInterceptor interceptor =
                new AWSRequestSigningApacheInterceptor(
                        "es",
                        signer,
                        credentialsProvider);

        BasicHttpEntityEnclosingRequest request =
                new BasicHttpEntityEnclosingRequest(
                        "POST",
                        "/test");

        BasicHttpEntity entity =
                new BasicHttpEntity();

        entity.setContent(
                new ByteArrayInputStream(
                        "hello".getBytes()));

        request.setEntity(entity);

        HttpContext context =
                new BasicHttpContext();

        interceptor.process(request, context);

        verify(signer)
                .sign(
                        any(DefaultRequest.class),
                        eq(credentials));
    }
}