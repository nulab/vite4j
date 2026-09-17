# vite4j

[English](README.md)

[Vite](https://ja.vite.dev) のエントリに必要なタグを、JVM のバックエンドから生成します。

Vite は自前の `index.html` ならタグを書き換えますが、バックエンドが出す HTML は Vite からは見えません。代わりに「どのエントリに何が必要か」をマニフェストに記録します。vite4j はそれを読んでタグを組み立てるので、**テンプレートにファイル名を書かなくて済みます**。

手で書いても、バンドラがチャンクを分割するまでは動きます。分割された瞬間、切り出された CSS はビルドされ、配信され、どこからもリンクされないまま一度も適用されなくなります。

## インストール

```gradle
implementation 'com.nulab-inc:vite4j:0.1.0'
```

Java 11 以降。Jackson は optional で、使うのは `JacksonManifestParser` だけです。

## 使い方

```js
// vite.config.js
export default defineConfig({
  build: { manifest: true, rollupOptions: { input: "src/main.tsx" } },
})
```

起動時に 1 回読みます。

```java
ViteManifest manifest;
try (InputStream in = loader.getResourceAsStream("public/.vite/manifest.json")) {
    manifest = ViteManifest.of(new JacksonManifestParser().parse(in));
}

ViteAssets assets = ViteAssets.builder(manifest)
        .assetUrl(path -> "/assets/" + path)
        .build();
```

あとはページごとに、この 1 行が手書きしていたタグすべての代わりになります。

```java
assets.html("src/main.tsx");
```

の戻り値が

```html
<link rel="stylesheet" href="/assets/assets/shared-ChJ_j-JJ.css">
<link rel="stylesheet" href="/assets/assets/main-5UjPuW-k.css">
<script type="module" src="/assets/assets/main-BRBmoGS9.js"></script>
```

`assetUrl` にはマニフェストに書かれたままのパスが渡ります。CDN のホスト、デプロイのバージョン、ダイジェスト付きディレクトリはここに入れてください。

`html()` が返すのは HTML なので、エスケープしない指定が必要です。多くのテンプレートエンジンは既定でエスケープするため、そのままだとタグが文字列としてページに出ます。

```html
<div th:utext="${viteTags}"></div>          <!-- Thymeleaf -->
@Html(vite.html("src/main.tsx"))            @* Twirl *@
<%= viteTags %>                             <%-- JSP。<c:out> ではない --%>
```

自分で組み立てたい場合は、`tags()` が同じタグをリストで返します。

## オプション

| | |
| --- | --- |
| `assetUrl(fn)` | マニフェストのパスを URL にする。既定は `/` を前置 |
| `modulePreload(true)` | import したチャンクごとに `<link rel="modulepreload">` を出す。既定は off |

## 開発時

dev server が動いている間はマニフェストがありません。エントリをソースのまま配信し、スタイルは自分で差し込むためです。`ViteDevServer` が本番用と同じ `ViteTags` インタフェースを実装しているので、テンプレート側は分岐せずに済みます。

```java
ViteTags vite = devMode
        ? ViteDevServer.at("http://localhost:5173/").withReactRefresh()
        : ViteAssets.builder(manifest).assetUrl(assetUrl).build();
```

`withReactRefresh()` は `@vitejs/plugin-react` が要求するプリアンブルを足します。

### dev server を自動で検知する

`devMode` を設定フラグで決めると、切り替えるたびに設定を書き換えて再起動することになります。dev server 自身に知らせてもらいましょう。数行の Vite 設定で、起動中だけファイルを置きます。

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

あとは勝手に切り替わります。

```java
ViteTags vite = HotFile.at(Paths.get("public/hot")).withReactRefresh().or(assets);
```

ファイルは呼び出しごとに読むので、dev server を起動・停止するだけで済みます。アプリケーション側は何も知る必要がなく、再起動も要りません。`public/hot` は `.gitignore` に入れてください。

`resolvedUrls.local[0]` は Vite が起動時に表示する URL です。ブラウザと dev server がホスト名の認識を共有していれば、これを書けば済みます。Docker やリバースプロキシを挟むと一致しないことがあり、その場合は Vite 設定の `server.origin` に指定した URL を書いてください。

## 別の JSON ライブラリを使う

`ManifestParser` はメソッド 1 つです。

```java
ManifestParser parser = json -> { /* → Map<String, Chunk> */ };
```

## マニフェストの読み方

[Backend Integration](https://ja.vite.dev/guide/backend-integration) の手順に従います。見落としやすいのは、**各チャンクが自分の CSS しか列挙しない**ことです。import 先のチャンクもたどる必要があります。`manifest[entry].css` を読んで終わりにすると、バンドラが切り出した分が漏れます。

順序は import 側が先、自分の CSS が最後です。モジュールの実行順であり、Vite が出力する順序でもあります。共有 CSS は 1 回だけリンクされ、循環しても停止し、マニフェストに無いチャンクは例外を投げずにスキップします。

## ライセンス

MIT — [LICENSE.txt](LICENSE.txt) を参照してください。
