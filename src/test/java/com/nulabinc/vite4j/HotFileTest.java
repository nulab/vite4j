package com.nulabinc.vite4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotFileTest {

    @TempDir
    Path dir;

    private final ViteTags built = entry -> singletonList("<script src=\"/built.js\"></script>");

    @Test
    void readsTheUrlTheDevServerWrote() throws IOException {
        HotFile hotFile = writing("http://localhost:5173/");

        assertTrue(hotFile.isRunning());
        assertEquals("http://localhost:5173/", hotFile.origin().orElse(null));
    }

    @Test
    void reportsNotRunningWhenThereIsNoFile() {
        HotFile hotFile = HotFile.at(dir.resolve("hot"));

        assertFalse(hotFile.isRunning());
        assertFalse(hotFile.devServer().isPresent());
    }

    // Vite writes the URL followed by a newline.
    @Test
    void ignoresSurroundingWhitespace() throws IOException {
        assertEquals("http://localhost:5173/", writing("  http://localhost:5173/  \n").origin().orElse(null));
    }

    // Some plugins leave the file behind empty rather than deleting it.
    @Test
    void treatsAnEmptyFileAsNotRunning() throws IOException {
        assertFalse(writing("").isRunning());
        assertFalse(writing("\n  \n").isRunning());
    }

    @Test
    void servesTheDevServersTagsWhileItIsRunning() throws IOException {
        List<String> tags = writing("http://localhost:5173/").or(built).tags("src/main.tsx");

        assertTrue(tags.stream().anyMatch(tag -> tag.contains("@vite/client")), tags.toString());
        assertTrue(tags.stream().anyMatch(tag -> tag.contains("src/main.tsx")), tags.toString());
    }

    @Test
    void fallsBackToTheBuildWhenItIsNot() {
        assertEquals(singletonList("<script src=\"/built.js\"></script>"),
                HotFile.at(dir.resolve("hot")).or(built).tags("src/main.tsx"));
    }

    // The whole point: a dev server starts and stops while the application keeps running, and it has
    // to notice without a restart.
    @Test
    void noticesTheDevServerComingAndGoing() throws IOException {
        Path path = dir.resolve("hot");
        ViteTags vite = HotFile.at(path).or(built);

        assertTrue(vite.html("src/main.tsx").contains("/built.js"));

        Files.write(path, "http://localhost:5173/".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(vite.html("src/main.tsx").contains("@vite/client"));

        Files.delete(path);
        assertTrue(vite.html("src/main.tsx").contains("/built.js"));
    }

    @Test
    void passesReactRefreshOnToTheDevServer() throws IOException {
        Path path = dir.resolve("hot");
        Files.write(path, "http://localhost:5173/".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(HotFile.at(path).withReactRefresh().or(built).html("src/main.tsx")
                .contains("__vite_plugin_react_preamble_installed__"));
    }

    private HotFile writing(String contents) throws IOException {
        Path path = dir.resolve("hot");
        Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HotFile.at(path);
    }
}
