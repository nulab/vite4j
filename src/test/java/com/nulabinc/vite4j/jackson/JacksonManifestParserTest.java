package com.nulabinc.vite4j.jackson;

import com.nulabinc.vite4j.Chunk;
import com.nulabinc.vite4j.ViteManifest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonManifestParserTest {

    private final JacksonManifestParser parser = new JacksonManifestParser();

    // The example from Vite's Backend Integration guide.
    private static final String MANIFEST = "{\n"
            + "  \"_shared-B7PI925R.js\": {\n"
            + "    \"file\": \"assets/shared-B7PI925R.js\",\n"
            + "    \"name\": \"shared\",\n"
            + "    \"css\": [\"assets/shared-ChJ_j-JJ.css\"]\n"
            + "  },\n"
            + "  \"views/foo.js\": {\n"
            + "    \"file\": \"assets/foo-BRBmoGS9.js\",\n"
            + "    \"name\": \"foo\",\n"
            + "    \"src\": \"views/foo.js\",\n"
            + "    \"isEntry\": true,\n"
            + "    \"imports\": [\"_shared-B7PI925R.js\"],\n"
            + "    \"css\": [\"assets/foo-5UjPuW-k.css\"]\n"
            + "  }\n"
            + "}";

    @Test
    void readsTheFieldsAChunkNeeds() {
        Map<String, Chunk> chunks = parser.parse(MANIFEST);

        Chunk entry = chunks.get("views/foo.js");
        assertEquals("assets/foo-BRBmoGS9.js", entry.file());
        assertEquals(Arrays.asList("assets/foo-5UjPuW-k.css"), entry.css());
        assertEquals(Arrays.asList("_shared-B7PI925R.js"), entry.imports());
    }

    @Test
    void walksWhatItParsed() {
        ViteManifest manifest = ViteManifest.of(parser.parse(MANIFEST));

        assertEquals(Arrays.asList("assets/shared-ChJ_j-JJ.css", "assets/foo-5UjPuW-k.css"),
                manifest.stylesheets("views/foo.js"));
    }

    // Vite adds keys over time; an older reader should not fall over a newer manifest.
    @Test
    void ignoresFieldsItDoesNotKnow() {
        Map<String, Chunk> chunks = parser.parse("{\"main.js\": {\"file\": \"index.js\", \"somethingNew\": {\"a\": 1}}}");

        assertEquals("index.js", chunks.get("main.js").file());
    }

    @Test
    void treatsMissingListsAsEmpty() {
        Map<String, Chunk> chunks = parser.parse("{\"main.js\": {\"file\": \"index.js\"}}");

        assertTrue(chunks.get("main.js").css().isEmpty());
        assertTrue(chunks.get("main.js").imports().isEmpty());
    }

    @Test
    void readsFromAStream() {
        Map<String, Chunk> chunks = parser.parse(
                new java.io.ByteArrayInputStream(MANIFEST.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals(2, chunks.size());
    }

    @Test
    void refusesSomethingThatIsNotAManifest() {
        assertThrows(java.io.UncheckedIOException.class, () -> parser.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("[]"));
    }
}
