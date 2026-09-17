package com.nulabinc.vite4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One entry in Vite's manifest: a built JavaScript chunk, the stylesheets it brings with it, and the
 * other chunks it imports.
 *
 * <p>A chunk lists only its <em>own</em> stylesheets. The ones belonging to the chunks it imports
 * live in those chunks' entries, which is why {@link ViteManifest} has to walk the graph rather than
 * read a single record.
 */
public final class Chunk {

    private final String file;
    private final List<String> css;
    private final List<String> imports;

    public Chunk(String file, List<String> css, List<String> imports) {
        this.file = file;
        this.css = immutableCopy(css);
        this.imports = immutableCopy(imports);
    }

    /** The built file for this chunk, as written in the manifest's {@code file} key. */
    public String file() {
        return file;
    }

    /** Stylesheets this chunk brings with it, in the manifest's order. Never null. */
    public List<String> css() {
        return css;
    }

    /** Keys of the chunks this chunk imports, to be looked up in the same manifest. Never null. */
    public List<String> imports() {
        return imports;
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new java.util.ArrayList<>(values));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Chunk)) {
            return false;
        }
        Chunk that = (Chunk) other;
        return Objects.equals(file, that.file) && css.equals(that.css) && imports.equals(that.imports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, css, imports);
    }

    @Override
    public String toString() {
        return "Chunk{file=" + file + ", css=" + css + ", imports=" + imports + "}";
    }
}
