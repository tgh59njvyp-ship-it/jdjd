# PinZip - Pinterest Board Batch Downloader & ZIP Saver (Android)

PinterestのボードURLから全高画質画像を一括取得し、選択してZIP保存・共有ができるAndroidアプリです。

## ディレクトリ構成
```
app/src/main/java/com/example/
├── MainActivity.kt                      # メインアクティビティ・ナビゲーション・コンポーザブル
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt               # Room データベース設定
│   │   └── SavedZipDao.kt               # ZIP作成履歴 DAO
│   ├── network/
│   │   └── PinterestFetcher.kt          # Pinterest API v5 / Web 抽出ロジック・ドメイン保護
│   └── repository/
│       └── PinterestRepository.kt       # 画像並列ダウンロード・ZipOutputStream 圧縮・履歴保持
├── model/
│   └── Models.kt                        # PinterestPin, BoardInfo, ZipProgressState, SavedZipEntity
├── ui/
│   ├── components/
│   │   ├── HeroHeader.kt                # ヒーローバナーヘッダー
│   │   ├── UrlInputSection.kt           # URL入力・バリデーション・クリップボード貼り付け
│   │   ├── PinGrid.kt                   # グリッド表示・選択・全選択/解除・フィルタリング
│   │   ├── PinDetailDialog.kt           # 原寸大プレビューダイアログ
│   │   ├── ZipProgressDialog.kt         # ZIP生成進捗バー・共有ダイアログ
│   │   ├── HistoryScreen.kt             # Room DB履歴一覧・共有・削除
│   │   └── SettingsScreen.kt            # Pinterest API Token 設定・ガイド
│   ├── theme/
│   │   ├── Color.kt                     # Material 3 & Pinterest Red カラー
│   │   ├── Theme.kt                     # アプリテーマ設定
│   │   └── Type.kt                      # タイポグラフィ
│   └── viewmodel/
│       └── MainViewModel.kt             # アプリ全般の状態管理
```

## 主要機能

### 1. URL入力 & バリデーション
- PinterestのボードURL（例: `https://www.pinterest.jp/username/board-name/` や 短縮URL `pin.it/...`）の形式検証
- 日本語での丁寧なエラーメッセージ表示と過去の入力履歴チップ

### 2. ボード情報の全ピン取得
- Pinterest API v5（要 `PINTEREST_ACCESS_TOKEN`）または 公開ボード Web 抽出フォールバックに対応
- 各ピンから原寸大（originals / 高解像度）画像URLを取得
- `i.pinimg.com` ドメインに限定し、SSRF（サーバーサイドリクエストフォージェリ）攻撃を保護
- 取得上限（500件）およびスキップ件数の自動集計

### 3. 画像グリッド表示・プレビュー
- 2列 / 3列 / 4列切り替え対応のレスポンシブサムネイルグリッド
- 全選択・全解除・キーワードでの絞り込み検索
- タップで原寸大画像の拡大プレビュー表示

### 4. 高速並列ZIP化 & 保存・共有
- OkHttp + `java.util.zip.ZipOutputStream` によるマルチスレッド（同時5並列制限）画像ダウンローダー
- 連番ファイル名（`001.jpg`, `002.png` など）と重複排除
- リアルタイム進捗バー表示（完了件数 / 全件数 / 失敗数）
- 一部失敗しても処理を継続
- Android `FileProvider` を使用したZIPファイルの共有・他アプリ送信

### 5. ZIP作成履歴管理
- Room Database による作成済みZIPのローカル保存（ファイル名、サイズ、件数、作成日時）
- 過去に作成したZIPのいつでも再共有・削除が可能

## セットアップ & 起動手順

### 1. Pinterest API Access Token の取得 (任意)
1. [Pinterest Developer Portal](https://developers.pinterest.com/) にアクセスし、開発者アカウントでログインします。
2. 「My Apps」より新しいアプリを作成します。
3. パーミッション設定で `boards:read` および `pins:read` を有効化します。
4. 発行された Access Token をコピーします。

### 2. 環境変数の設定
プロジェクト直下の `.env` ファイル（または `.env.example`）に Access Token を記述します：
```env
PINTEREST_ACCESS_TOKEN=your_pinterest_access_token_here
```
※ API Token 未設定時でも、自動Web解析モードでボード内の画像を取得可能です。

### 3. アプリのビルド & 実行
Android Studio でプロジェクトを開くか、Gradleコマンドでビルドします：
```bash
gradle assembleDebug
```
