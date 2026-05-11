# body-data-lab-android — Claude Code Instructions

## プロジェクト概要

body-data-lab バックエンドの Android クライアント。WebView で `/ui/*` を表示し、ネイティブ機能（Health Connect）でデータを取得して API に送信する。

## 技術スタック

- Kotlin / Android
- WebView（バックエンドの HTML UI を表示）
- Health Connect API（歩数データ取得）
- OkHttp（API通信）
- Jetpack（AppCompat, Activity）

## ディレクトリ構成

```
app/src/main/
  java/com/example/myhealthhub/
    MainActivity.kt       # トップ画面・Health Connect同期・ナビゲーション
    DashboardActivity.kt  # /ui/steps を WebView 表示
    ReflectionActivity.kt # /ui/reflections を WebView 表示
    WishListActivity.kt   # /ui/wishes を WebView 表示
  res/layout/
    activity_main.xml     # ボタン一覧
    activity_reflection.xml # WebView レイアウト（各 Activity で共用）
```

## 重要な依存関係

**body-data-lab と密結合。** バックエンドの `/ui/*` や API エンドポイントに依存している。
- バックエンドに画面を追加したら、`MainActivity` にボタンを追加し対応する `Activity` を作成する
- `AndroidManifest.xml` への Activity 登録も忘れずに

## ビルド・インストール

```bash
# USBデバッグ有効にして接続してから
./gradlew installDebug
```

## 設定

- `BuildConfig.API_BASE_URL`: バックエンドのベースURL（`local.properties` または `build.gradle` で設定）
- `BuildConfig.API_KEY`: APIキー（未設定でも動作する）
