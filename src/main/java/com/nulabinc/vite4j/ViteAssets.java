package com.nulabinc.vite4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Renders the tags for a built bundle, following the four steps in Vite's
 * <a href="https://vite.dev/guide/backend-integration.html">Backend Integration</a> guide:
 * stylesheets for the entry, stylesheets for everything it imports, the entry's script, and
 * optionally a preload for each imported chunk.
 *
 * <p>Where the files are served from is the application's business, so it supplies a resolver that
 * turns a path in the manifest into a URL. That is where a digest directory, a CDN host or a
 * deployment version goes.
 *
 * <pre>{@code
 * ViteAssets assets = ViteAssets.builder(manifest)
 *         .assetUrl(path -> "/assets/" + version + "/" + path)
 *         .build();
 *
 * assets.html("src/main.tsx");
 * }</pre>
 */
public final class ViteAssets implements ViteTags {

    private final ViteManifest manifest;
    private final UnaryOperator<String> assetUrl;
    private final boolean modulePreload;

    private ViteAssets(Builder builder) {
        this.manifest = builder.manifest;
        this.assetUrl = builder.assetUrl;
        this.modulePreload = builder.modulePreload;
    }

    public static Builder builder(ViteManifest manifest) {
        return new Builder(manifest);
    }

    @Override
    public List<String> tags(String entry) {
        List<String> tags = new ArrayList<>(stylesheetTags(entry));
        tags.addAll(modulePreloadTags(entry));
        scriptTag(entry).ifPresent(tags::add);
        return Collections.unmodifiableList(tags);
    }

    /** One {@code <link rel="stylesheet">} per stylesheet the entry needs, in cascade order. */
    public List<String> stylesheetTags(String entry) {
        List<String> tags = new ArrayList<>();
        for (String stylesheet : manifest.stylesheets(entry)) {
            tags.add("<link rel=\"stylesheet\" href=\"" + Html.attribute(url(stylesheet)) + "\">");
        }
        return Collections.unmodifiableList(tags);
    }

    /** The {@code <script type="module">} for the entry, empty when the manifest does not describe it. */
    public java.util.Optional<String> scriptTag(String entry) {
        return manifest.file(entry)
                .map(file -> "<script type=\"module\" src=\"" + Html.attribute(url(file)) + "\"></script>");
    }

    /**
     * A {@code <link rel="modulepreload">} per imported chunk, so the browser can start fetching them
     * without waiting to parse the entry first. Empty unless preloading was asked for.
     */
    public List<String> modulePreloadTags(String entry) {
        if (!modulePreload) {
            return Collections.emptyList();
        }
        List<String> tags = new ArrayList<>();
        for (String file : manifest.importedFiles(entry)) {
            tags.add("<link rel=\"modulepreload\" href=\"" + Html.attribute(url(file)) + "\">");
        }
        return Collections.unmodifiableList(tags);
    }

    private String url(String path) {
        String resolved = assetUrl.apply(path);
        if (resolved == null) {
            throw new IllegalStateException("The asset URL resolver returned null for " + path);
        }
        return resolved;
    }

    public static final class Builder {

        private final ViteManifest manifest;
        private UnaryOperator<String> assetUrl = path -> "/" + path;
        private boolean modulePreload;

        private Builder(ViteManifest manifest) {
            if (manifest == null) {
                throw new IllegalArgumentException("manifest must not be null");
            }
            this.manifest = manifest;
        }

        /** Turns a path as the manifest spells it into the URL a browser should request. */
        public Builder assetUrl(UnaryOperator<String> assetUrl) {
            if (assetUrl == null) {
                throw new IllegalArgumentException("assetUrl must not be null");
            }
            this.assetUrl = assetUrl;
            return this;
        }

        /** Emits {@code modulepreload} links for imported chunks. Off by default: Vite calls it optional. */
        public Builder modulePreload(boolean modulePreload) {
            this.modulePreload = modulePreload;
            return this;
        }

        public ViteAssets build() {
            return new ViteAssets(this);
        }
    }
}
