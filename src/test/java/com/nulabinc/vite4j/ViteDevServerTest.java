package com.nulabinc.vite4j;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteDevServerTest {

    @Test
    void loadsTheClientAndThenTheEntrysSource() {
        List<String> tags = ViteDevServer.at("http://localhost:5173/").tags("src/main.tsx");

        assertEquals(2, tags.size());
        assertTrue(tags.get(0).contains("http://localhost:5173/@vite/client"));
        assertTrue(tags.get(1).contains("http://localhost:5173/src/main.tsx"));
    }

    @Test
    void acceptsAnOriginWithOrWithoutATrailingSlash() {
        assertEquals(ViteDevServer.at("http://localhost:5173").tags("src/main.tsx"),
                ViteDevServer.at("http://localhost:5173/").tags("src/main.tsx"));
    }

    @Test
    void keepsABasePathInTheOrigin() {
        List<String> tags = ViteDevServer.at("http://localhost:5173/vite-dev/").tags("src/main.tsx");

        assertTrue(tags.get(1).contains("http://localhost:5173/vite-dev/src/main.tsx"));
    }

    @Test
    void doesNotDoubleTheSlashWhenTheEntryHasOne() {
        List<String> tags = ViteDevServer.at("http://localhost:5173/").tags("/src/main.tsx");

        assertTrue(tags.get(1).contains("http://localhost:5173/src/main.tsx"));
    }

    // @vitejs/plugin-react fails at runtime unless this runs before any component module.
    @Test
    void emitsTheRefreshPreambleFirstWhenReactIsInPlay() {
        List<String> tags = ViteDevServer.at("http://localhost:5173/").withReactRefresh().tags("src/main.tsx");

        assertEquals(3, tags.size());
        assertTrue(tags.get(0).contains("__vite_plugin_react_preamble_installed__"));
        assertTrue(tags.get(0).contains("http://localhost:5173/@react-refresh"));
    }
}
