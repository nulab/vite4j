package com.nulabinc.vite4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

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
    private final String scriptAttributes;
    private final String styleAttributes;

    private ViteAssets(Builder builder) {
        this.manifest = builder.manifest;
        this.assetUrl = builder.assetUrl;
        this.modulePreload = builder.modulePreload;
        this.scriptAttributes = render(builder.scriptAttributes);
        this.styleAttributes = render(builder.styleAttributes);
    }

    /** An attribute with no value renders bare, the way {@code async} and {@code defer} are written. */
    private static String render(Map<String, String> attributes) {
        StringBuilder rendered = new StringBuilder();
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            rendered.append(' ').append(attribute.getKey());
            if (attribute.getValue() != null) {
                rendered.append("=\"").append(Html.attribute(attribute.getValue())).append('"');
            }
        }
        return rendered.toString();
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
            tags.add("<link rel=\"stylesheet\" href=\"" + Html.attribute(url(stylesheet)) + "\"" + styleAttributes + ">");
        }
        return Collections.unmodifiableList(tags);
    }

    /** The {@code <script type="module">} for the entry, empty when the manifest does not describe it. */
    public java.util.Optional<String> scriptTag(String entry) {
        return manifest.file(entry)
                .map(file -> "<script type=\"module\" src=\"" + Html.attribute(url(file)) + "\"" + scriptAttributes + "></script>");
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

        /** What vite4j writes on the script itself. Letting these be overridden makes the tag silently wrong. */
        private static final Set<String> SCRIPT_RESERVED = reserved("src", "type");

        /** The same for the stylesheet link. */
        private static final Set<String> STYLE_RESERVED = reserved("href", "rel");

        /** An HTML attribute name. A value can be escaped; a name cannot, so one that is not a name is refused. */
        private static final Pattern NAME = Pattern.compile("[A-Za-z_:][A-Za-z0-9_:.-]*");

        private final ViteManifest manifest;
        private UnaryOperator<String> assetUrl = path -> "/" + path;
        private boolean modulePreload;
        private final Map<String, String> scriptAttributes = new LinkedHashMap<>();
        private final Map<String, String> styleAttributes = new LinkedHashMap<>();

        private static Set<String> reserved(String... names) {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
        }

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

        /**
         * Adds an attribute to the {@code <script>} tag, for a {@code crossorigin}, a {@code nonce}, or
         * whatever a CDN or a monitoring tool asks for. Attributes render in the order they were added,
         * and adding the same name twice keeps the last value. Names are matched without regard to case,
         * as HTML reads them.
         *
         * @throws IllegalArgumentException if the name is not an attribute name, or is one vite4j writes
         *                                  itself ({@code src}, {@code type})
         */
        public Builder scriptAttribute(String name, String value) {
            scriptAttributes.put(checked(name, SCRIPT_RESERVED), value);
            return this;
        }

        /** The same, for an attribute that stands alone with no value, such as {@code async} or {@code defer}. */
        public Builder scriptAttribute(String name) {
            return scriptAttribute(name, null);
        }

        /**
         * Adds an attribute to each {@code <link rel="stylesheet">} tag.
         *
         * @throws IllegalArgumentException if the name is not an attribute name, or is one vite4j writes
         *                                  itself ({@code href}, {@code rel})
         */
        public Builder styleAttribute(String name, String value) {
            styleAttributes.put(checked(name, STYLE_RESERVED), value);
            return this;
        }

        /** The same, for an attribute that stands alone with no value. */
        public Builder styleAttribute(String name) {
            return styleAttribute(name, null);
        }

        /**
         * HTML does not tell {@code crossorigin} and {@code CROSSORIGIN} apart, so neither does the map
         * they are collected in: keeping both would render one attribute twice, and the browser would
         * take the first while the caller was promised the last.
         */
        private static String checked(String name, Set<String> reserved) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("attribute name must not be empty");
            }
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("\"" + name + "\" is not an attribute name");
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (reserved.contains(normalized)) {
                throw new IllegalArgumentException(name + " is written by vite4j and cannot be overridden");
            }
            return normalized;
        }

        public ViteAssets build() {
            return new ViteAssets(this);
        }
    }
}
