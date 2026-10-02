package com.nulabinc.vite4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteTagsTest {

    @Test
    void noneRendersNoTags() {
        assertTrue(ViteTags.none().tags("src/main.tsx").isEmpty());
    }

    // What a template holding one of these actually calls. An empty string is what renders nothing.
    @Test
    void noneRendersAnEmptyString() {
        assertEquals("", ViteTags.none().html("src/main.tsx"));
    }

    // The point is that no entry reaches a manifest, so none of them can be the one that throws.
    @Test
    void noneRendersNothingForAnEntryNoBuildEverHad() {
        assertTrue(ViteTags.none().tags("src/never-built.tsx").isEmpty());
    }
}
