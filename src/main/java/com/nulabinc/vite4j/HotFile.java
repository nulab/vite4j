package com.nulabinc.vite4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The file a Vite dev server writes while it is running, holding the URL it is listening on.
 *
 * <p>Without it an application has to be told which mode it is in - a config flag, a system property,
 * a profile - and told again every time the answer changes, which means a restart to switch. The file
 * answers the question by existing, so starting the dev server is enough.
 *
 * <p>Vite does not write one on its own. A few lines in the Vite config do it; the README has them.
 *
 * <pre>{@code
 * ViteTags vite = HotFile.at(Paths.get("public/hot")).withReactRefresh().or(assets);
 * }</pre>
 */
public final class HotFile {

    private final Path path;
    private final boolean reactRefresh;

    private HotFile(Path path, boolean reactRefresh) {
        this.path = path;
        this.reactRefresh = reactRefresh;
    }

    public static HotFile at(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return new HotFile(path, false);
    }

    /** Adds the preamble React Fast Refresh needs to the dev server's tags. See {@link ViteDevServer#withReactRefresh()}. */
    public HotFile withReactRefresh() {
        return new HotFile(path, true);
    }

    /** The dev server the file points at, empty when it is not there or holds nothing usable. */
    public Optional<ViteDevServer> devServer() {
        return origin().map(origin -> {
            ViteDevServer devServer = ViteDevServer.at(origin);
            return reactRefresh ? devServer.withReactRefresh() : devServer;
        });
    }

    /** The URL in the file, empty when it is not there or holds nothing usable. */
    public Optional<String> origin() {
        try {
            if (!Files.isReadable(path)) {
                return Optional.empty();
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return lines.stream().map(String::trim).filter(line -> !line.isEmpty()).findFirst();
        } catch (IOException e) {
            // The dev server writes this file and deletes it on the way out, so a read can lose a
            // race with either. Not running is the safe reading: a page served from the build works,
            // one pointed at a dev server that has gone does not.
            return Optional.empty();
        }
    }

    /** True while the file is there and names a dev server. */
    public boolean isRunning() {
        return origin().isPresent();
    }

    /**
     * Tags from the dev server while it is running, and from {@code built} the rest of the time.
     *
     * <p>The file is read per call rather than once at startup: a dev server is started and stopped
     * while the application keeps running, and having to restart it to notice is the problem this
     * was meant to solve.
     */
    public ViteTags or(ViteTags built) {
        if (built == null) {
            throw new IllegalArgumentException("built must not be null");
        }
        return entry -> devServer().map(devServer -> (ViteTags) devServer).orElse(built).tags(entry);
    }
}
