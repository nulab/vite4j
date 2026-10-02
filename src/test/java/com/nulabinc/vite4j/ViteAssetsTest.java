package com.nulabinc.vite4j;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.nulabinc.vite4j.ViteManifestTest.entry;
import static com.nulabinc.vite4j.ViteManifestTest.manifestOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void putsAnAttributeOnTheScript() {
        ViteAssets assets = ViteAssets.builder(manifest).scriptAttribute("crossorigin", "anonymous").build();

        assertEquals("<script type=\"module\" src=\"/assets/index.js\" crossorigin=\"anonymous\"></script>",
                assets.scriptTag("main.js").orElse(null));
    }

    @Test
    void putsAnAttributeOnEveryStylesheet() {
        ViteAssets assets = ViteAssets.builder(manifest).styleAttribute("data-turbo-track", "reload").build();

        assertEquals(Arrays.asList(
                "<link rel=\"stylesheet\" href=\"/assets/shared.css\" data-turbo-track=\"reload\">",
                "<link rel=\"stylesheet\" href=\"/assets/index.css\" data-turbo-track=\"reload\">"),
                assets.stylesheetTags("main.js"));
    }

    // async and defer carry their meaning by being present, so a value would be wrong rather than redundant.
    @Test
    void writesAnAttributeWithNoValueBare() {
        ViteAssets assets = ViteAssets.builder(manifest).scriptAttribute("async").build();

        assertEquals("<script type=\"module\" src=\"/assets/index.js\" async></script>",
                assets.scriptTag("main.js").orElse(null));
    }

    @Test
    void keepsAttributesInTheOrderTheyWereAdded() {
        ViteAssets assets = ViteAssets.builder(manifest)
                .scriptAttribute("crossorigin", "anonymous")
                .scriptAttribute("async")
                .scriptAttribute("data-id", "main")
                .build();

        assertEquals("<script type=\"module\" src=\"/assets/index.js\" crossorigin=\"anonymous\" async data-id=\"main\"></script>",
                assets.scriptTag("main.js").orElse(null));
    }

    @Test
    void keepsTheLastValueForAnAttributeAddedTwice() {
        ViteAssets assets = ViteAssets.builder(manifest)
                .scriptAttribute("crossorigin", "anonymous")
                .scriptAttribute("crossorigin", "use-credentials")
                .build();

        assertEquals("<script type=\"module\" src=\"/assets/index.js\" crossorigin=\"use-credentials\"></script>",
                assets.scriptTag("main.js").orElse(null));
    }

    // The same escaping the URLs already get. A value carrying a quote would otherwise end the attribute.
    @Test
    void escapesAnAttributeValue() {
        ViteAssets assets = ViteAssets.builder(manifest).scriptAttribute("data-note", "a \"quote\" & <tag>").build();

        assertEquals("<script type=\"module\" src=\"/assets/index.js\" data-note=\"a &quot;quote&quot; &amp; &lt;tag&gt;\"></script>",
                assets.scriptTag("main.js").orElse(null));
    }

    // Overriding these makes the tag silently wrong rather than loudly broken, so it is refused up front.
    @Test
    void refusesTheAttributesItWritesItself() {
        ViteAssets.Builder builder = ViteAssets.builder(manifest);

        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("src", "/elsewhere.js"));
        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("type", "text/javascript"));
        assertThrows(IllegalArgumentException.class, () -> builder.styleAttribute("href", "/elsewhere.css"));
        assertThrows(IllegalArgumentException.class, () -> builder.styleAttribute("rel", "preload"));
    }

    @Test
    void refusesAReservedAttributeWhateverItsCase() {
        ViteAssets.Builder builder = ViteAssets.builder(manifest);

        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("SRC", "/elsewhere.js"));
    }

    // A value can be escaped on the way out; a name cannot, so one that would break the markup is refused.
    @Test
    void refusesAttributeNamesThatAreNotNames() {
        ViteAssets.Builder builder = ViteAssets.builder(manifest);

        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("", "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("one two", "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("onload=alert(1) x", "y"));
        assertThrows(IllegalArgumentException.class, () -> builder.scriptAttribute("a\"b", "x"));
    }

    // The two sets are separate: an attribute meant for the script has no business on a stylesheet.
    @Test
    void keepsScriptAndStyleAttributesApart() {
        ViteAssets assets = ViteAssets.builder(manifest).scriptAttribute("async").build();

        assertEquals(singletonList("<link rel=\"stylesheet\" href=\"/assets/shared.css\">"),
                assets.stylesheetTags("_shared.js"));
    }
}
