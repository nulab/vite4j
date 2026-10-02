package com.nulabinc.vite4j;

import java.util.Collections;
import java.util.List;

/**
 * The tags a page needs in order to load a Vite entry.
 *
 * <p>Implemented by {@link ViteAssets} for a built bundle and {@link ViteDevServer} for a running dev
 * server, so a template can hold one of these and not branch on which it is. {@link HotFile#or} picks
 * between them per call.
 */
@FunctionalInterface
public interface ViteTags {

    /** Every tag for the entry, in the order they should appear in the document. */
    List<String> tags(String entry);

    /** The same tags, joined by newlines, for templates that want one string. */
    default String html(String entry) {
        return String.join("\n", tags(entry));
    }

    /**
     * Tags for a page with no frontend build: none, whatever the entry.
     *
     * <p>Reading a manifest happens before {@link ViteManifest#of}, so an application wired to vite4j
     * needs one before it can start. With nothing built and no dev server running that read throws, and
     * every test that renders a page fails with it, for a reason that has nothing to do with what the
     * test was checking. The fallbacks a manifest can offer do not reach this: they stand in for a
     * manifest that could not be read, not for the absence of a build.
     *
     * <p>Not only for tests. A server that answers with JSON and renders no pages has no use for a
     * manifest either, and should not have to be handed one.
     */
    static ViteTags none() {
        return entry -> Collections.emptyList();
    }
}
