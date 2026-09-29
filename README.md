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
- 編集履歴
- Run時の基本的な括弧・文字列チェック
- Preview画面とフルスクリーン表示
- GitHub ActionsによるDebug APKビルド

## Runについて

現在のRunは、KStudioアプリ内での基本的な静的チェックです。
実際のAndroid APKビルドはGitHub Actionsで行います。

## ビルド

GitHub Actionsの「Build APK」を手動実行するか、mainへのpush / pull requestでビルドできます。
Debug APKはActionsのArtifactとして取得できます。
