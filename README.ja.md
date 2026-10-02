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

## リリース

GitHub の Release を公開すると Maven Central に publish されます。バージョンはタグから読むので
（`v0.1.0` でも `0.1.0` でも `0.1.0` と解釈されます）、事前にリポジトリを書き換える必要はありません。

```
gh release create v0.1.0 --generate-notes
```

あとは `.github/workflows/publish.yml` がタグの内容をビルドして成果物をステージし、JReleaser が
署名して [Central Portal](https://central.sonatype.com) にアップロードします。Central に上げた
バージョンは差し替えも取り下げもできないため、このワークフローは Release の公開時にのみ動きます。
失敗した場合、ログは実行結果に `jreleaser-log` として残ります。

`gradle.properties` の `version` はローカルビルドの名前でしかありません。公開されるバージョンでは
ないので、上げても何も起きません。

対になる公開鍵は `jreleaser.yml` にそのまま書いてあります。公開鍵は公開されるものですし、
`JRELEASER_GPG_PUBLIC_KEY` は秘密鍵と違って環境変数から読み戻されないためです。

必要なリポジトリシークレットは次の 4 つです。Central で検証済みの `com.nulab-inc` ネームスペースを
使います。

| | |
| --- | --- |
| `GPG_SECRET_KEY` | armored 形式の秘密鍵。JReleaser が環境変数から読むので keyring は不要 |
| `SIGNING_PASSWORD` | 上記の鍵のパスフレーズ |
| `CENTRAL_PORTAL_USER` | Central Portal の Account で発行する user token |
| `CENTRAL_PORTAL_PASSWORD` | その token のパスワード側 |

設定するときはファイルから流し込むか `printf` を使ってください。Web フォームへの貼り付けと `echo`
は避けます。

```
gpg --armor --export-secret-keys <鍵ID> > key.asc
gh secret set GPG_SECRET_KEY < key.asc
printf '%s' '<パスフレーズ>' | gh secret set SIGNING_PASSWORD
```

どの値も 1 文字増減すると壊れますが、エラーは揃って別のものを指します。チャット越しに貼って改行が
潰れた秘密鍵は、**公開鍵**が見つからないと報告されます。公開鍵がどれだけ正しくてもです。`echo` が
足した改行を含むパスフレーズは `checksum mismatch in checksum of 20 bytes` として、`jreleaser-log`
にしか残らないスタックトレースの奥に出ます。3 つのうち素直なのは Central の資格情報だけで、これは
401 と言ってくれます。

ワークフローは手動でも起動できます。リリースの全工程を dry run で歩き、何もアップロードしません。
署名鍵を変えた後に試しておくと安心です。

```
gh workflow run publish.yml --ref main -f version=0.0.0-rehearsal
```

ただし Central には接続しないので、鍵とパスフレーズは検証できても、Portal の資格情報が正しいか
どうかは分かりません。

## ライセンス

MIT — [LICENSE.txt](LICENSE.txt) を参照してください。
