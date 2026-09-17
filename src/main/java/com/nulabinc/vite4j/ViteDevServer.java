package com.nulabinc.vite4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders the tags for a running Vite dev server, where there is no manifest and no built file: the
 * entry is served from its source path and the client runtime handles the rest.
 *
 * <p>It implements the same {@link ViteTags} interface as {@link ViteAssets} so that a template asks
 * for tags without knowing which mode it is in, and the choice lives wherever the application decides
 * it.
 */
public final class ViteDevServer implements ViteTags {

    private final String origin;
    private final boolean reactRefresh;

    private ViteDevServer(String origin, boolean reactRefresh) {
        this.origin = origin.endsWith("/") ? origin : origin + "/";
        this.reactRefresh = reactRefresh;
    }

    /**
     * @param origin where the dev server is reachable, including any base path, for example
     *               {@code http://localhost:5173/} or {@code http://localhost:5173/vite-dev/}
     */
    public static ViteDevServer at(String origin) {
        if (origin == null || origin.isEmpty()) {
            throw new IllegalArgumentException("origin must not be empty");
        }
        return new ViteDevServer(origin, false);
    }

    /**
     * Also emits the preamble React Fast Refresh needs before any component module runs. Required by
     * {@code @vitejs/plugin-react}, which otherwise fails at runtime with a message about the preamble.
     */
    public ViteDevServer withReactRefresh() {
        return new ViteDevServer(origin, true);
    }

    @Override
    public List<String> tags(String entry) {
        List<String> tags = new ArrayList<>();
        if (reactRefresh) {
            tags.add(reactRefreshPreamble());
        }
        tags.add("<script type=\"module\" src=\"" + Html.attribute(origin + "@vite/client") + "\"></script>");
        tags.add("<script type=\"module\" src=\"" + Html.attribute(origin + stripLeadingSlash(entry)) + "\"></script>");
        return Collections.unmodifiableList(tags);
    }

    private String reactRefreshPreamble() {
        return "<script type=\"module\">\n"
                + "  import RefreshRuntime from \"" + Html.attribute(origin + "@react-refresh") + "\"\n"
                + "  RefreshRuntime.injectIntoGlobalHook(window)\n"
                + "  window.$RefreshReg$ = () => {}\n"
                + "  window.$RefreshSig$ = () => (type) => type\n"
                + "  window.__vite_plugin_react_preamble_installed__ = true\n"
                + "</script>";
    }

    private static String stripLeadingSlash(String entry) {
        return entry.startsWith("/") ? entry.substring(1) : entry;
    }
}
