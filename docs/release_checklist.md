# Google Play リリース時の必要事項

## Health Connect 関連（必須）

- [ ] `ACTION_SHOW_PERMISSIONS_RATIONALE` が呼ばれたときの権限説明画面を実装する
  - 「なぜ歩数データが必要か」をユーザーに説明する画面
  - 現状は `MainActivity` が起動されるだけで専用の説明画面がない
- [ ] プライバシーポリシーページの用意（URL が必要）
- [ ] Health Connect のデータ利用目的を Google Play Console で申告する

## アプリ全般

- [ ] リリースビルドの署名設定（Keystore の作成）
- [ ] `applicationId` を `com.example.myhealthhub` から正式なものに変更
- [ ] アプリ名・アイコンの確認
- [ ] minSdk の見直し（現状 35 = Android 15 以上のみ対象）

## 参考

- Health Connect の審査ガイドライン: https://developer.android.com/health-and-fitness/guides/health-connect/publish/request-access
