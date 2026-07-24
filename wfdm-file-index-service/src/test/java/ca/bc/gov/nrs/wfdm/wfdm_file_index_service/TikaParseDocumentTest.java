package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;

class TikaParseDocumentTest {

    @Test
    void shouldInstantiatePrivateConstructorViaReflection()
            throws Exception {

        Constructor<TikaParseDocument> constructor =
                TikaParseDocument.class.getDeclaredConstructor();

        constructor.setAccessible(true);
        constructor.newInstance();

        assertEquals(
                TikaParseDocument.class,
                constructor.getDeclaringClass());
    }

    @Test
    void shouldAttemptPdfParsing()
            throws Exception {

        InputStream stream =
                new ByteArrayInputStream(
                        "not a pdf".getBytes());

        assertThrows(
                Exception.class,
                () -> TikaParseDocument.parseStream(
                        stream,
                        "application/pdf"));
    }

    @Test
    void shouldUseAutoDetectParserForUnknownMimeType()
            throws Exception {

        InputStream stream =
                new ByteArrayInputStream(
                        "hello world".getBytes());

        String result =
                TikaParseDocument.parseStream(
                        stream,
                        "application/unknown");

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldRemoveStopWords()
            throws Exception {

        Method method =
                TikaParseDocument.class.getDeclaredMethod(
                        "removeStopWords",
                        String.class);

        method.setAccessible(true);

        String result =
                (String) method.invoke(
                        null,
                        "the quick brown fox");

        assertFalse(result.contains("the"));
        assertTrue(result.contains("quick"));
        assertTrue(result.contains("brown"));
        assertTrue(result.contains("fox"));
    }

    @Test
    void shouldAssembleExtractionResult()
            throws Exception {

        Method method =
                TikaParseDocument.class.getDeclaredMethod(
                        "assembleExtractionResult",
                        String.class,
                        Metadata.class);

        method.setAccessible(true);

        Metadata metadata =
                new Metadata();

        metadata.add(
                "Content-Type",
                "text/plain");

        metadata.add(
                "Content-Length",
                "10");

        metadata.add(
                "Author",
                "Person");

        String result =
                (String) method.invoke(
                        null,
                        "hello world",
                        metadata);

        assertNotNull(result);

        assertNotNull(result);

        assertTrue(result.contains("hello world"));
        assertTrue(result.contains("text\\/plain"));
        assertTrue(result.contains("\"ContentLength\":\"10\""));
        assertTrue(result.contains("Person"));
    }

    @Test
    void shouldUseDefaultMetadataValues()
            throws Exception {

        Method method =
                TikaParseDocument.class.getDeclaredMethod(
                        "assembleExtractionResult",
                        String.class,
                        Metadata.class);

        method.setAccessible(true);

        Metadata metadata =
                new Metadata();

        String result =
                (String) method.invoke(
                        null,
                        "hello",
                        metadata);

        assertNotNull(result);

        assertNotNull(result);

        assertTrue(result.contains("content"));
        assertTrue(result.contains("unknown"));
        assertTrue(result.contains("ContentLength"));
    }
}