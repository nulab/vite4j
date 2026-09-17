package com.nulabinc.vite4j.jackson;

import com.nulabinc.vite4j.Chunk;
import com.nulabinc.vite4j.ViteAssets;
import com.nulabinc.vite4j.ViteManifest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a manifest a real {@code vite build} produced, from the project under {@code fixtures/}.
 *
 * <p>Hand-written JSON only contains the shapes you thought of. A real manifest carries records for
 * assets rather than chunks, records for a stylesheet on its own, and the split-out chunk whose CSS
 * an entry needs but does not list - which is the whole reason this library exists.
 */
class RealManifestTest {

    private final ViteManifest manifest = load("manifests/vite-8.json");

    private static ViteManifest load(String resource) {
        try (InputStream in = RealManifestTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return ViteManifest.of(new JacksonManifestParser().parse(in));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // The entry lists only its own stylesheet. Reading that and stopping there is the bug.
    @Test
    void picksUpTheStylesheetOfTheSplitOutChunk() {
        Chunk entry = manifest.chunk("src/main.js").orElseThrow(AssertionError::new);

        assertEquals(1, entry.css().size());
        assertEquals(2, manifest.stylesheets("src/main.js").size());
    }

    @Test
    void putsTheSharedChunksStylesheetFirst() {
        List<String> stylesheets = manifest.stylesheets("src/main.js");

        assertTrue(stylesheets.get(0).contains("shared"), stylesheets.toString());
        assertTrue(stylesheets.get(1).contains("main"), stylesheets.toString());
    }

    // A dynamic import is fetched at runtime and brings its own stylesheet with it, so linking it up
    // front would load CSS for code the page may never run.
    @Test
    void leavesOutWhatOnlyDynamicImportsReach() {
        List<String> stylesheets = manifest.stylesheets("src/main.js");

        assertFalse(stylesheets.stream().anyMatch(css -> css.contains("lazy")), stylesheets.toString());
    }

    // Both entries share one chunk, so both need its stylesheet.
    @Test
    void servesEveryEntryInTheManifest() {
        assertTrue(manifest.stylesheets("src/admin.js").stream().anyMatch(css -> css.contains("shared")));
        assertTrue(manifest.stylesheets("src/admin.js").stream().anyMatch(css -> css.contains("admin")));
    }

    // "src/logo.svg" and the standalone stylesheet record have no css and no imports. They must read
    // as empty rather than blow up on a missing field.
    @Test
    void readsRecordsThatAreNotChunks() {
        Chunk asset = manifest.chunk("src/logo.svg").orElseThrow(AssertionError::new);

        assertTrue(asset.file().endsWith(".svg"));
        assertTrue(asset.css().isEmpty());
        assertTrue(asset.imports().isEmpty());
    }

    @Test
    void preloadsTheSharedChunkAndNotTheEntryItself() {
        List<String> files = manifest.importedFiles("src/main.js");

        assertEquals(1, files.size());
        assertTrue(files.get(0).contains("shared"));
    }

    @Test
    void rendersThePageTagsInOrder() {
        List<String> tags = ViteAssets.builder(manifest)
                .assetUrl(path -> "/static/" + path)
                .modulePreload(true)
                .build()
                .tags("src/main.js");

        assertEquals(Arrays.asList("link", "link", "link", "script"), kindsOf(tags));
        assertTrue(tags.get(3).contains("/static/assets/main-"), tags.get(3));
    }

    private static List<String> kindsOf(List<String> tags) {
        return tags.stream()
                .map(tag -> tag.startsWith("<link") ? "link" : "script")
                .collect(java.util.stream.Collectors.toList());
    }
}
