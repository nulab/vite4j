package com.nulabinc.vite4j;

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
}
