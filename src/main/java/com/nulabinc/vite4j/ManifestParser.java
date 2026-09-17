package com.nulabinc.vite4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Turns the manifest's JSON into chunks.
 *
 * <p>This is an interface rather than a fixed choice of library so that nothing here dictates which
 * JSON library, or which version of it, an application runs. {@link
 * com.nulabinc.vite4j.jackson.JacksonManifestParser} implements it for the common case; an
 * application that reaches for Gson, Moshi or play-json instead implements it in a few lines.
 */
@FunctionalInterface
public interface ManifestParser {

    /** Parses a whole manifest document, keyed the way Vite writes it. */
    Map<String, Chunk> parse(String json);

    /** Reads the document and parses it. The stream is closed. */
    default Map<String, Chunk> parse(InputStream json) {
        try (Reader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            StringBuilder document = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                document.append(buffer, 0, read);
            }
            return parse(document.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the Vite manifest", e);
        }
    }
}
