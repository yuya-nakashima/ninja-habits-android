# body-data-lab-android 設計書

## 概要

Pixel 8a + Pixel Watch 4 の歩数データを Health Connect 経由で取得し、バックエンドへ送信する Android アプリ。

---

## 環境

| 項目 | 内容 |
|------|------|
| 端末 | Google Pixel 8a |
| ウェアラブル | Pixel Watch 4（Health Connect へ自動同期） |
| OS | Android 15（minSdk = 35） |
| 言語 | Kotlin |
| ビルド | debug / release |

---

## API

| 項目 | 内容 |
|------|------|
| ベース URL（debug） | `https://35-76-200-8.nip.io` |
| ベース URL（release） | `https://35-76-200-8.nip.io` |
| エンドポイント | `POST /ingest` |

### ペイロード形式

```json
{
  "source": "health_connect",
  "metric": "steps",
  "start_at": "2026-04-07T15:00:00Z",
  "end_at": "2026-04-08T14:59:59Z",
  "value": 8432,
  "unit": "count"
}
```

- `start_at` / `end_at` は ISO 8601 UTC
- バックエンドの normalize は `source=health_connect` かつ `metric=steps` のみ処理する
- サーバは upsert（同じ日のデータを複数回送っても上書き）

---

## データ取得設計

### 送信範囲

| タイミング | 送信する日付 |
|-----------|-------------|
| アプリ起動時（毎回） | 昨日・今日の2日分 |

- 一昨日以前のバックフィルは別途検討
- 今日分は「当日 00:00 JST 〜 現在時刻」で送信（当日中に複数回起動しても上書きされる）
- 昨日分は「前日 00:00 JST 〜 00:00 JST」（1日分まるごと）

### 重複排除

Pixel Watch 4 とスマホの両方が歩数を記録するため、`readRecords()` の単純な合計では**二重カウント**になる。

→ `HealthConnectClient.aggregate()` + `StepsRecord.COUNT_TOTAL` を使用する。Health Connect が複数ソースの重複を自動排除する。

```kotlin
val result = healthConnectClient.aggregate(
    AggregateRequest(
        metrics = setOf(StepsRecord.COUNT_TOTAL),
        timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
    )
)
val total = result[StepsRecord.COUNT_TOTAL] ?: 0L
```

---

## アプリフロー

```
起動
  └─ 権限チェック（READ_STEPS）
       ├─ 権限あり → syncRecentSteps() 自動実行
       └─ 権限なし → 権限リクエスト画面
                        └─ 許可 → syncRecentSteps() 実行
                        └─ 拒否 → エラーメッセージ表示

syncRecentSteps()
  ├─ 昨日の aggregate → POST /ingest
  └─ 今日の aggregate → POST /ingest
```

ボタンは手動で再送するために残す。

---

## 権限

`AndroidManifest.xml` で宣言：

```xml
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.INTERNET" />
```

Health Connect のパッケージクエリ：

```xml
<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>
```

---

## インストール方法（実機）

debug ビルドを ADB でインストールする。

```bash
# プロジェクトルートで実行
./gradlew assembleDebug

# Pixel 8a を USB 接続して
adb install app/build/outputs/apk/debug/app-debug.apk
```

事前に Pixel 8a の「開発者向けオプション」→「USB デバッグ」を有効にすること。

---

## 今後の検討事項

- 一昨日以前のデータ送信方法（バックフィル UI または別ツール）
- バックグラウンド自動送信（WorkManager による定期実行）
- エラー時のリトライ処理
