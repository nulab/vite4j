package com.nulabinc.vite4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteManifestTest {

    @Nested
    @DisplayName("stylesheets")
    class Stylesheets {

        @Test
        void listsTheEntrysOwnStylesheet() {
            ViteManifest manifest = manifestOf(entry("main.js", "index.js", singletonList("index.css"), emptyList()));

            assertEquals(singletonList("index.css"), manifest.stylesheets("main.js"));
        }

        // What this library exists for: an imported chunk keeps its stylesheets in its own record,
        // so reading the entry alone misses them.
        @Test
        void followsTheChunksTheEntryImports() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", singletonList("index.css"), singletonList("_shared.js")),
                    entry("_shared.js", "shared.js", singletonList("shared.css"), emptyList()));

            assertTrue(manifest.stylesheets("main.js").contains("shared.css"));
        }

        @Test
        void followsImportsOfImports() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), singletonList("_a.js")),
                    entry("_a.js", "a.js", singletonList("a.css"), singletonList("_b.js")),
                    entry("_b.js", "b.js", singletonList("b.css"), emptyList()));

            assertEquals(Arrays.asList("b.css", "a.css"), manifest.stylesheets("main.js"));
        }

        // The order the modules run in, and the order Vite emits. It decides which rule wins when two
        // stylesheets declare the same selector at the same specificity.
        @Test
        void putsImportedStylesheetsBeforeTheImportersOwn() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", singletonList("index.css"), singletonList("_shared.js")),
                    entry("_shared.js", "shared.js", singletonList("shared.css"), emptyList()));

            assertEquals(Arrays.asList("shared.css", "index.css"), manifest.stylesheets("main.js"));
        }

        @Test
        void keepsTheImportOrderBetweenSiblings() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), Arrays.asList("_a.js", "_b.js")),
                    entry("_a.js", "a.js", singletonList("a.css"), emptyList()),
                    entry("_b.js", "b.js", singletonList("b.css"), emptyList()));

            assertEquals(Arrays.asList("a.css", "b.css"), manifest.stylesheets("main.js"));
        }

        @Test
        void listsAStylesheetTwoChunksShareOnlyOnce() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), Arrays.asList("_a.js", "_b.js")),
                    entry("_a.js", "a.js", singletonList("shared.css"), emptyList()),
                    entry("_b.js", "b.js", singletonList("shared.css"), emptyList()));

            assertEquals(singletonList("shared.css"), manifest.stylesheets("main.js"));
        }

        @Test
        void terminatesOnACycle() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), singletonList("_a.js")),
                    entry("_a.js", "a.js", singletonList("a.css"), singletonList("_b.js")),
                    entry("_b.js", "b.js", singletonList("b.css"), singletonList("_a.js")));

            assertEquals(Arrays.asList("b.css", "a.css"), manifest.stylesheets("main.js"));
        }

        @Test
        void skipsAnImportTheManifestDoesNotDescribe() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", singletonList("index.css"), singletonList("_gone.js")));

            assertEquals(singletonList("index.css"), manifest.stylesheets("main.js"));
        }

        @Test
        void returnsNothingForAnEntryTheManifestDoesNotDescribe() {
            ViteManifest manifest = manifestOf(entry("main.js", "index.js", singletonList("index.css"), emptyList()));

            assertTrue(manifest.stylesheets("elsewhere.js").isEmpty());
        }
    }

    @Nested
    @DisplayName("importedFiles")
    class ImportedFiles {

        @Test
        void listsTheFilesOfEveryChunkReached() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), singletonList("_a.js")),
                    entry("_a.js", "a.js", emptyList(), singletonList("_b.js")),
                    entry("_b.js", "b.js", emptyList(), emptyList()));

            assertEquals(Arrays.asList("b.js", "a.js"), manifest.importedFiles("main.js"));
        }

        // The script tag already loads it; preloading it as well would ask for the same file twice.
        @Test
        void leavesOutTheEntrysOwnFile() {
            ViteManifest manifest = manifestOf(
                    entry("main.js", "index.js", emptyList(), singletonList("_a.js")),
                    entry("_a.js", "a.js", emptyList(), emptyList()));

            assertFalse(manifest.importedFiles("main.js").contains("index.js"));
        }
    }

    @Test
    void reportsTheEntrysBuiltFile() {
        ViteManifest manifest = manifestOf(entry("main.js", "index.js", emptyList(), emptyList()));

        assertEquals("index.js", manifest.file("main.js").orElse(null));
        assertFalse(manifest.file("elsewhere.js").isPresent());
    }

    static Map.Entry<String, Chunk> entry(String key, String file, List<String> css, List<String> imports) {
        return new java.util.AbstractMap.SimpleEntry<>(key, new Chunk(file, css, imports));
    }

    @SafeVarargs
    static ViteManifest manifestOf(Map.Entry<String, Chunk>... entries) {
        Map<String, Chunk> chunks = new LinkedHashMap<>();
        for (Map.Entry<String, Chunk> entry : entries) {
            chunks.put(entry.getKey(), entry.getValue());
        }
        return ViteManifest.of(Collections.unmodifiableMap(chunks));
    }
}
