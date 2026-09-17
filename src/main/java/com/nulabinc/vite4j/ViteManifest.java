package com.nulabinc.vite4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The manifest Vite writes next to the assets it builds, and the questions a page needs answered
 * about it.
 *
 * <p>A page rendered by a backend is markup Vite never sees, so it cannot rewrite the tags that load
 * its output the way it rewrites its own index.html. It records what each entry needs instead, and
 * the backend reads that record. See
 * <a href="https://vite.dev/guide/backend-integration.html">Backend Integration</a>.
 *
 * <p>This class answers the questions; {@link ViteAssets} turns the answers into tags.
 */
public final class ViteManifest {

    private final Map<String, Chunk> chunks;

    private ViteManifest(Map<String, Chunk> chunks) {
        this.chunks = Collections.unmodifiableMap(new HashMap<>(chunks));
    }

    /** Wraps an already parsed manifest. See {@link ManifestParser} for getting there from JSON. */
    public static ViteManifest of(Map<String, Chunk> chunks) {
        if (chunks == null) {
            throw new IllegalArgumentException("chunks must not be null");
        }
        return new ViteManifest(chunks);
    }

    /** The chunk an entry maps to, empty when the manifest does not describe it. */
    public Optional<Chunk> chunk(String entry) {
        return Optional.ofNullable(chunks.get(entry));
    }

    /** The built file for an entry, which is what a {@code <script type="module">} points at. */
    public Optional<String> file(String entry) {
        return chunk(entry).map(Chunk::file);
    }

    /**
     * Every stylesheet the entry needs, in the order they should be loaded.
     *
     * <p>Imports come first and the chunk's own stylesheets last. That is the order the modules run
     * in, and the order Vite itself emits, so where two stylesheets declare the same selector at the
     * same specificity the same one wins here as in a Vite-rendered page.
     *
     * <p>A stylesheet two chunks share is listed once, at its first position. Cycles terminate, and
     * an import the manifest does not describe is skipped rather than throwing: a manifest is build
     * output, and a page half-styled beats a page that fails to render.
     */
    public List<String> stylesheets(String entry) {
        List<String> found = new ArrayList<>();
        walk(entry, new LinkedHashSet<>(), found, null);
        return Collections.unmodifiableList(found);
    }

    /**
     * The built files of every chunk the entry imports, for {@code <link rel="modulepreload">}.
     *
     * <p>The entry's own file is not included: it is loaded by the script tag, not preloaded.
     */
    public List<String> importedFiles(String entry) {
        List<String> found = new ArrayList<>();
        walk(entry, new LinkedHashSet<>(), null, found);
        found.remove(file(entry).orElse(null));
        return Collections.unmodifiableList(found);
    }

    /**
     * Depth first, imports before the chunk itself. Collects into whichever accumulators are given,
     * so both questions are answered by the same traversal and can never disagree about the graph.
     */
    private void walk(String key, Set<String> visited, List<String> css, List<String> files) {
        if (!visited.add(key)) {
            return;
        }
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            return;
        }
        for (String importee : chunk.imports()) {
            walk(importee, visited, css, files);
        }
        if (css != null) {
            for (String stylesheet : chunk.css()) {
                if (!css.contains(stylesheet)) {
                    css.add(stylesheet);
                }
            }
        }
        if (files != null && chunk.file() != null && !files.contains(chunk.file())) {
            files.add(chunk.file());
        }
    }
}
