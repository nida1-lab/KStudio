# KStudio

スマホ向けAndroid開発環境。

## 現在の機能

- Kotlinコードエディタ
- 行番号表示
- シンタックスハイライト
- ワード検索
- 行ジャンプ
- 括弧・引用符の自動補完
- 閉じ括弧の二重入力スキップ
- ペア削除
- Tabで4スペース入力
- Enter時の簡易インデント
- Undo / Redo
- システムのライト / ダークモード対応
- Androidのファイル選択UIを使ったプロジェクトフォルダ選択
- ファイルの開く / 保存 / 新規作成
- フォルダの作成 / 移動
- ファイル・フォルダの名前変更 / 削除
- ファイル内容のコピー
- クリップボード内容でファイルを丸ごと置換
- 最近開いたプロジェクトの記憶
- HOMEダッシュボード
  - プロジェクト
  - 履歴
  - コミュニティ
  - クラウド使用量表示
- プロジェクト一覧
  - 最終編集時間
  - 編集者
  - クラウドバックアップ状態
- 1つ前へ戻るカスタム戻る操作
- 編集中の未保存変更に対するカスタム警告
- 標準AlertDialog / Toastを使わないKStudio独自UI
- Androidアニメーションを使わないUI
- コミュニティ
  - #タグ
  - #検索
  - ワード検索
  - スレッド
  - 登録アカウント名での投稿
  - @メンション入力候補
  - @ / #の強調表示
- 共同コーディング基盤
  - 所有者 / 準所有者 / 編集者
  - 所有者・準所有者だけのメンバー管理
  - 所有者のみプロジェクト削除
  - 1ファイル1編集者のロック基盤
  - ロックの定期更新と期限切れ処理
- Preview画面とフルスクリーン表示
- GitHub ActionsによるDebug APKビルド

## 共同コーディングについて

現在はクライアント側の権限UI・メンバー管理・ファイルロック基盤を実装しています。

AさんとBさんが別端末から同じプロジェクトをリアルタイムで共同編集するには、Cloud側の共有データベースと同期APIが必要です。
この接続先は今後のCloud機能実装で追加します。

## Run：実際のKotlin / Android実行

Runで編集中のファイルを保存し、プロジェクト全体をGitHub Actionsへ送ります。
Kotlinコンパイラ・Android Gradle PluginでDebug APKを生成し、アプリ内でダウンロード、インストール、起動できます。
処理は別のAndroidアプリとして端末上で実行されます。KStudio内のXMLプレビューはレイアウト確認専用です。

### 初回の設定

1. Run → GitHub接続設定を開く。
2. 最新版のこのリポジトリ、またはそのForkを `owner/repo` で指定する。
3. GitHubのFine-grained PATを**アプリ内**へ入力する。対象リポジトリの **Contents: Read and write / Actions: Read and write** が必要。
4. ソース送信への同意を選択して保存する。公開リポジトリではソースも公開されるので、追加の同意が必要。
5. 「ビルドして実行」→「APKをインストール」→「インストール済みアプリを起動」。Androidから求められた場合はKStudioの「不明なアプリのインストール」を許可する。

このチャットのGitHub接続と、端末上のKStudioの認証は別です。PATをチャットに送る必要はありません。
PATはAndroid Keystoreの鍵でAES-GCM暗号化し、バックアップ対象から除外しています。

### 対応するプロジェクト

- **KStudioの簡単なフォルダ**：`package` 宣言付きの `MainActivity.kt` / `.java` と、他のソース、`res/`、`assets/`。Gradleの設定を自動生成する。新規プロジェクトはクリックで数値が変わるカウンターから開始。
- **Kotlinコンソール**：トップレベルの `fun main()` / `fun main(args: Array<String>)` をAndroidアプリへコンパイルし、`println` と例外を画面に表示。標準入力には未対応。
- **既存のAndroid Gradleプロジェクト**：依存関係とビルド設定を維持して `assembleDebug`。JDK 17を使用し、Gradleのバージョンはwrapperのpropertiesから取得（未指定時8.9）。単一のDebug APK、またはuniversal APKに対応。

簡単なフォルダには外部ライブラリを自動追加しません。Compose / AppCompat等を使う場合は、依存関係を宣言したGradleプロジェクトを開いてください。
`examples/run-counter` と `examples/run-console` はそのまま開いてRunできる実行サンプルです。

### ビルドの状態とエラー

- アプリを閉じたり通信が切れたりしても、開始済みのActionsは継続。「前回のビルドに再接続」で結果を取得できる。
- 同時に開始できるビルドは1件。停止要求とActionsへのリンクを用意。
- Kotlin / javac / XMLのコンパイルエラーはログに表示し、対応ファイル・行へ移動できる。
- ソースZIPのSHA-256、リクエストID、APKのSHA-256を確認してからインストールする。
- 実行ごとに `kstudio-run/<UUID>` ブランチへソースZIPを送る。完了・停止時は一時ブランチの削除を試みる。成果物は3日間保存。公開されたソースの完全な消去を保証するものではない。
- `.git` / `.gradle` / `build` / `local.properties`、代表的な鍵ファイル等は送信から除外。最大1000ファイル・全体25MB・1ファイル10MB。

### Debug署名

`tools/run-debug.keystore` は実行用APKを更新インストールできるようにする**公開のテスト専用鍵**です。
パスワードとaliasはAndroid標準Debug値（`android` / `androiddebugkey`）。配布・本番署名には使用しないでください。
ビルド用リポジトリにActionsのシークレットを登録する必要はありません。

### ビルド・検証

GitHub Actionsの「Build APK」はKStudio本体のユニットテストとDebug APK生成を実行します。
さらに「Run project」の同じビルド経路でカウンターとコンソールのAPKを生成し、Android 35エミュレーターへインストール・起動します。
カウンターのクリックで `Count: 0 → Count: 1`、Kotlinの関数・map・printlnの実行結果を自動確認します。

```sh
python3 -m unittest discover -s tools -p 'test_*.py' -v
gradle testDebugUnitTest assembleDebug
```

KStudio本体のAPKはActionsの `KStudio-debug` Artifactから取得できます。
