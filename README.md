# Language-Agnostic Debugging Lab

**Language-Agnostic Debugging Lab** は、特定の言語構文の暗記ではなく、実行時の症状と再現テストから原因カテゴリを絞り込む練習のための、自己完結型Java 21コマンドラインアプリケーションです。Java、JavaScript、Python、SQLなどに共通しやすい不具合パターンを、短いコード例・観察結果・段階的ヒントとして提供します。

> このアプリケーションの狙いは、正解の修正コードを見ることではなく、**入力境界、型、資源管理、共有状態、時刻基準**という切り分けの観点を身に付けることです。

| 項目 | 内容 |
|---|---|
| 実装言語 | Java 21 |
| 実行形式 | CLIおよび実行可能JAR |
| 外部ライブラリ | 不要 |
| 問題数 | 6シナリオ |
| 進捗の保存先 | 既定は `$HOME/.debugging-lab-progress.tsv` |
| テスト方式 | 外部フレームワーク不要のJavaテストランナー |

## 提供する学習シナリオ

各シナリオには、症状、期待動作、調査対象コード、再現テストの観察結果、3段階のヒント、診断の採点基準が含まれています。正解済みのシナリオを再回答しても、重複してスコアを獲得することはできません。

| ID | テーマ | 代表的な原因カテゴリ | 難易度 |
|---|---|---|---|
| `N01` | 空の顧客メモで処理が停止する | null値と入力検証 | 基礎 |
| `B02` | 最終要素だけが集計できない | 境界条件とoff-by-one | 基礎 |
| `M03` | 達成率が常に0%になる | 整数除算と型変換 | 実践 |
| `R04` | 日次処理の途中でファイルがロックされる | 資源解放とtry-with-resources | 実践 |
| `C05` | 在庫がまれにマイナスになる | 競合状態と排他制御 | 応用 |
| `T06` | 月末だけ締め日がずれる | タイムゾーンと時刻基準 | 応用 |

## クイックスタート

プロジェクトのルートディレクトリで、次のコマンドを実行してください。初回の実行時は、必要なクラスと実行可能JARが自動で生成されます。

```bash
cd /home/ubuntu/language-agnostic-debugging-lab
./scripts/run.sh
```

引数なしで実行すると、対話モードが開始します。メニューからシナリオを選び、問題文の確認、再現テスト、ヒントの参照、原因の診断、進捗確認を順に行えます。

| 操作 | 対話モードでのキー | コマンドライン例 |
|---|---:|---|
| シナリオ一覧 | `L` | `./scripts/run.sh --list` |
| 問題文・コードの表示 | `S` | `./scripts/run.sh --show N01` |
| 再現テストの表示 | `R` | `./scripts/run.sh --run N01` |
| ヒントの表示 | `H` | `./scripts/run.sh --hint N01` |
| 原因の診断・採点 | `A` | `./scripts/run.sh --answer N01 "null guard"` |
| 進捗の確認 | `P` | `./scripts/run.sh --progress` |
| 使い方の表示 | — | `./scripts/run.sh --help` |

たとえば `N01` では、通常値・空文字・未入力値の再現テストを比較できます。失敗条件を観察してから `null guard`、`入力検証`、`nullpointer` など、原因を表す語句を含めて回答してください。採点後には原因箇所と修正方針を解説します。

## ビルドとテスト

以下のスクリプトは、すべてプロジェクトのルートから実行できます。いずれもJava 21の標準ツールのみを用い、ネットワーク接続や依存関係の解決を必要としません。

```bash
# 実行可能JARを生成
./scripts/build.sh

# 回帰テストをコンパイルして実行
./scripts/test.sh

# 生成済みJARを直接実行する場合
java -jar out/debugging-lab.jar --list
```

| スクリプト | 役割 | 主な出力 |
|---|---|---|
| `scripts/build.sh` | 本体をコンパイルし、実行可能JARを生成する | `out/debugging-lab.jar` |
| `scripts/run.sh` | JARがなければビルドしてからアプリケーションを起動する | 対話画面またはCLI出力 |
| `scripts/test.sh` | 本体とテストをコンパイルし、テストランナーを実行する | 成功・失敗の結果 |

## 設計上のポイント

アプリケーションは、問題データを不変の `BugScenario` レコードとして扱います。`DiagnosticSimulator` は各シナリオの観察結果を一貫したレポートへ整形し、`AnswerEvaluator` は入力された診断に含まれる原因カテゴリを判定します。これにより、表示・診断・採点の責務を分離しています。

進捗は `ProgressStore` がTSV形式でローカルに保存します。既定の保存先は `$HOME/.debugging-lab-progress.tsv` です。検証や複数プロファイルを使い分ける場合は、`DEBUGGING_LAB_PROGRESS_FILE` 環境変数に任意のファイルパスを指定できます。書込みは一時ファイルを経由してから置換するため、途中で処理が中断された場合にも、既存の進捗ファイルを直接上書きしません。保存データが一部破損している場合は、破損行だけを無視して学習を継続できます。

```bash
DEBUGGING_LAB_PROGRESS_FILE=/tmp/lab-progress.tsv ./scripts/run.sh --progress
```

```text
ConsoleApplication
  ├── ScenarioCatalog      : シナリオ定義
  ├── DiagnosticSimulator  : 再現テスト結果の組立て
  ├── AnswerEvaluator      : 診断キーワードの判定
  └── ProgressStore        : 試行回数・完了状態・スコアの永続化
```

## ディレクトリ構成

```text
language-agnostic-debugging-lab/
├── README.md
├── scripts/
│   ├── build.sh
│   ├── run.sh
│   └── test.sh
└── src/
    ├── main/java/lab/LanguageAgnosticDebuggingLab.java
    └── test/java/lab/LabEngineTest.java
```

## 追加・拡張の考え方

新しい問題を追加する場合は、`ScenarioCatalog.create()` に `BugScenario` を1件追加してください。シナリオID、難易度、症状、期待動作、コード例、原因箇所、解説、ヒント、観察結果、受理する診断キーワードを定義すれば、一覧表示・対話モード・CLI採点・進捗保存に自動的に反映されます。既存のテストに、新しいシナリオの観察結果と診断判定を加えることで品質を保てます。
