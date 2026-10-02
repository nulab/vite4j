# vite4j

[日本語](README.ja.md)

https://central.sonatype.com/artifact/com.nulab-inc/vite4j

Render the tags a [Vite](https://vite.dev) entry needs, from a JVM backend.

Vite rewrites the tags in its own `index.html`. A page your backend renders is markup Vite never sees,
so it writes down what each entry needs and leaves the tags to you. vite4j reads that and writes them,
so no filename is spelled out in a template.

Spelling them out works until the bundler splits a chunk. Then the CSS of the split-out chunk is
built, served, and never applied, because nothing links it.

## Install

```gradle
implementation 'com.nulab-inc:vite4j:0.1.0'
```

Java 11+. Jackson is optional — used by `JacksonManifestParser` and nothing else.

## Use

```js
// vite.config.js
export default defineConfig({
  build: { manifest: true, rollupOptions: { input: "src/main.tsx" } },
})
```

Read the manifest once at startup:

```java
ViteManifest manifest;
try (InputStream in = loader.getResourceAsStream("public/.vite/manifest.json")) {
    manifest = ViteManifest.of(new JacksonManifestParser().parse(in));
}

ViteAssets assets = ViteAssets.builder(manifest)
        .assetUrl(path -> "/assets/" + path)
        .build();
```

Then, per page, one call replaces every tag you would have written by hand:

```java
assets.html("src/main.tsx");
```

returns

```html
<link rel="stylesheet" href="/assets/assets/shared-ChJ_j-JJ.css">
<link rel="stylesheet" href="/assets/assets/main-5UjPuW-k.css">
<script type="module" src="/assets/assets/main-BRBmoGS9.js"></script>
```

`assetUrl` receives the path as the manifest spells it. Put a CDN host, a deployment version or a
digest directory there.

`html()` returns markup, and most template engines escape what you hand them. Tell yours not to, or
the tags arrive on the page as text:

```html
<div th:utext="${viteTags}"></div>          <!-- Thymeleaf -->
@Html(vite.html("src/main.tsx"))            @* Twirl *@
<%= viteTags %>                             <%-- JSP, not <c:out> --%>
```

`tags()` returns the same tags as a list, for rendering them yourself.

## Options

| | |
| --- | --- |
| `assetUrl(fn)` | Turns a manifest path into a URL. Defaults to `/` + path |
| `modulePreload(true)` | Adds `<link rel="modulepreload">` per imported chunk. Off by default |
| `scriptAttribute(name, value)` | Adds an attribute to the `<script>` tag |
| `styleAttribute(name, value)` | Adds an attribute to every `<link rel="stylesheet">` tag |

A `crossorigin`, a `nonce`, or whatever a CDN or a monitoring tool asks for:

```java
ViteAssets.builder(manifest)
        .scriptAttribute("crossorigin", "anonymous")
        .scriptAttribute("async")
        .styleAttribute("data-turbo-track", "reload")
        .build();
```

```html
<link rel="stylesheet" href="/assets/main.css" data-turbo-track="reload">
<script type="module" src="/assets/main.js" crossorigin="anonymous" async></script>
```

Called with a name alone, the attribute stands on its own, which is how `async` and `defer` carry
their meaning. Attributes appear in the order they were added, and values are escaped the way the
URLs already are.

The attributes vite4j writes itself are refused: `src` and `type` on the script, `href` and `rel` on
the stylesheets. Overriding one of those makes the tag silently wrong, so it throws where it is
asked for rather than rendering something that does not work.

## Development

While the dev server runs there is no manifest — it serves the entry from source and injects styles
itself. `ViteDevServer` renders that mode behind the same `ViteTags` interface, so a template holds
one of the two and does not branch:

```java
ViteTags vite = devMode
        ? ViteDevServer.at("http://localhost:5173/").withReactRefresh()
        : ViteAssets.builder(manifest).assetUrl(assetUrl).build();
```

`withReactRefresh()` adds the preamble `@vitejs/plugin-react` requires.

### Detecting the dev server

Deciding `devMode` from a config flag means setting it, and restarting to change it. Have the dev
server say so instead: a few lines of Vite config write a file while it runs.

```js
import { unlinkSync, writeFileSync } from "node:fs"

const hotFile = "public/hot"

const hot = () => ({
  name: "hot-file",
  apply: "serve",
  configureServer(server) {
    server.httpServer?.once("listening", () => {
      writeFileSync(hotFile, server.resolvedUrls.local[0])
    })
    const remove = () => {
      try { unlinkSync(hotFile) } catch {}
    }
    process.on("exit", remove)
    for (const signal of ["SIGINT", "SIGTERM", "SIGHUP"]) {
      process.on(signal, () => { remove(); process.exit() })
    }
  },
})
```

Then the choice makes itself:

```java
ViteTags vite = HotFile.at(Paths.get("public/hot")).withReactRefresh().or(assets);
```

The file is read per call, so starting and stopping the dev server is enough — the application does
not need to know, and does not need restarting. Add `public/hot` to `.gitignore`.

`resolvedUrls.local[0]` is the URL Vite prints on startup, which is the one to write when the browser
and the dev server agree on what to call the host. Behind Docker or a reverse proxy they may not, and
`server.origin` in the Vite config is then the URL to write instead.

## Without a build

An application wired to vite4j reads a manifest before it starts. With nothing built and no dev
server running, that read throws, and every test that renders a page fails with it — for a reason
that has nothing to do with what the test was checking.

```java
ViteTags vite = ViteTags.none();
```

renders nothing, for any entry. `html()` returns an empty string.

Not only for tests. A server that answers with JSON and renders no pages has no use for a manifest
either, and this is how it says so.

## Another JSON library

`ManifestParser` is one method:

```java
ManifestParser parser = json -> { /* → Map<String, Chunk> */ };
```

## How it reads the manifest

It follows the [Backend Integration](https://vite.dev/guide/backend-integration.html) guide. The step
that is easy to miss: **a chunk lists only its own stylesheets**, so the chunks it imports have to be
walked too. Reading `manifest[entry].css` and stopping there misses whatever the bundler split out.

Imports come first, the chunk's own stylesheets last — the order the modules run in, and the order
Vite emits. A shared stylesheet is linked once, cycles terminate, and a chunk the manifest does not
describe is skipped rather than throwing.

## Releasing

Cutting a GitHub release publishes to Maven Central. The tag carries the version — `v0.1.0` and
`0.1.0` are both read as `0.1.0` — so nothing in the repository has to be edited first.

```
gh release create v0.1.0 --generate-notes
```

`.github/workflows/publish.yml` then builds the tag, stages the artifacts, and hands them to
JReleaser, which signs them and uploads them to the [Central Portal](https://central.sonatype.com).
A version on Central can neither be replaced nor withdrawn, so the workflow runs on a published
release and on nothing else. If it fails, the log is kept as a `jreleaser-log` artifact on the run.

The `version` in `gradle.properties` only ever names a local build. It is not the version that
ships, and bumping it does nothing.

The matching public key is checked in, in `jreleaser.yml` — a public key is public, and
`JRELEASER_GPG_PUBLIC_KEY` is not read back out of the environment the way the secret key is.

These repository secrets have to be set, under the `com.nulab-inc` namespace already verified on
Central:

| | |
| --- | --- |
| `GPG_SECRET_KEY` | Armored private key. JReleaser reads it from the environment — no keyring |
| `SIGNING_PASSWORD` | Passphrase for that key |
| `CENTRAL_PORTAL_USER` | Central Portal user token, generated under Account |
| `CENTRAL_PORTAL_PASSWORD` | The token's password half |

## License

MIT — see [LICENSE.txt](LICENSE.txt).
