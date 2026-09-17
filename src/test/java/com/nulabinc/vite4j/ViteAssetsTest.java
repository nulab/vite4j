package com.nulabinc.vite4j;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.nulabinc.vite4j.ViteManifestTest.entry;
import static com.nulabinc.vite4j.ViteManifestTest.manifestOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteAssetsTest {

    private final ViteManifest manifest = manifestOf(
            entry("main.js", "assets/index.js", singletonList("assets/index.css"), singletonList("_shared.js")),
            entry("_shared.js", "assets/shared.js", singletonList("assets/shared.css"), emptyList()));

    @Test
    void rendersStylesheetsThenTheScript() {
        ViteAssets assets = ViteAssets.builder(manifest).build();

        assertEquals(Arrays.asList(
                "<link rel=\"stylesheet\" href=\"/assets/shared.css\">",
                "<link rel=\"stylesheet\" href=\"/assets/index.css\">",
                "<script type=\"module\" src=\"/assets/index.js\"></script>"), assets.tags("main.js"));
    }

    @Test
    void sendsEveryPathThroughTheResolver() {
        ViteAssets assets = ViteAssets.builder(manifest)
                .assetUrl(path -> "https://cdn.example.com/v42/" + path)
                .build();

        assertEquals(singletonList("https://cdn.example.com/v42/assets/index.js"),
                pathsIn(assets.scriptTag("main.js").map(java.util.Collections::singletonList).orElse(emptyList())));
    }

    // Optional in Vite's guide, so a page has to ask before its markup grows.
    @Test
    void preloadsImportedChunksOnlyWhenAskedTo() {
        assertTrue(ViteAssets.builder(manifest).build().modulePreloadTags("main.js").isEmpty());

        assertEquals(singletonList("<link rel=\"modulepreload\" href=\"/assets/shared.js\">"),
                ViteAssets.builder(manifest).modulePreload(true).build().modulePreloadTags("main.js"));
    }

    // Preloads belong before the script that will import them, or the browser learns about them too late.
    @Test
    void putsPreloadsBeforeTheScript() {
        List<String> tags = ViteAssets.builder(manifest).modulePreload(true).build().tags("main.js");

        assertTrue(tags.indexOf("<link rel=\"modulepreload\" href=\"/assets/shared.js\">")
                < tags.indexOf("<script type=\"module\" src=\"/assets/index.js\"></script>"));
    }

    @Test
    void escapesWhatGoesIntoTheAttribute() {
        ViteAssets assets = ViteAssets.builder(manifest).assetUrl(path -> "/a?v=1&b=\"2\"/" + path).build();

        assertTrue(assets.stylesheetTags("main.js").get(0).contains("/a?v=1&amp;b=&quot;2&quot;/"));
    }

    @Test
    void rendersNothingForAnEntryTheManifestDoesNotDescribe() {
        ViteAssets assets = ViteAssets.builder(manifest).build();

        assertTrue(assets.tags("elsewhere.js").isEmpty());
    }

    @Test
    void joinsTagsForTemplatesThatWantOneString() {
        String html = ViteAssets.builder(manifest).build().html("main.js");

        assertEquals(3, html.split("\n").length);
    }

    private static List<String> pathsIn(List<String> tags) {
        return tags.stream().map(tag -> tag.replaceAll(".*src=\"([^\"]+)\".*", "$1")).collect(java.util.stream.Collectors.toList());
    }
}
