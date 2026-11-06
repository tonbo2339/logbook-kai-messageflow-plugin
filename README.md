# logbook-kai-messageflow-plugin

[航海日誌改 (logbook-kai)](https://github.com/sakura0689/logbook-kai) 用の非公式プラグインです。

もともと[logbook-kai-messageflow](https://github.com/sakura0689/logbook-kai-messageflow)は、
Chrome拡張と航海日誌改を中継するために必要な、別プロセス・別ウィンドウのツールでした。
このプラグインはその機能を航海日誌改本体に統合したもので、**別ウィンドウや別プロセスを
起動することなく、航海日誌改だけで完結**します。

## 何ができるか

- messageflowのWebSocket(SockJS)サーバーを、航海日誌改のプロセス内で起動
- Chrome拡張から受け取った通信内容を、航海日誌改の処理へ**直接**渡す（プロキシを経由した折り返しが不要）
- メインウィンドウのタイトルバーに接続状況を表示（例: `航海日誌 25.0.8 [MessageFlow: 接続中]`）
- 「拡張」メニューから状態確認ダイアログを開いて、接続状況やQueueの処理件数を確認
- ダイアログから通信ログ（`logs/messageflow_traffic.log`）をその場で確認
- ポート番号・受け渡し方式・デバッグログは状態確認ダイアログの「設定」ボタンから変更

## インストール

1. [Releases](../../releases) から `logbook-kai-messageflow-plugin.jar` をダウンロード
2. 航海日誌改の `plugins` フォルダに置く
3. 航海日誌改を起動する

初回起動時に `config/messageflow.json` が自動生成されます。ポート番号を変更するには、
「拡張」メニュー→MessageFlow→設定ダイアログから変更できます（変更を反映するには航海日誌改の
再起動が必要です）。

## 使い方

### 1. Chrome拡張を入れる（初回だけ）

このプラグインはあくまで航海日誌改側の中継役なので、Chrome拡張自体は含まれていません。
導入方法は[logbook-kai-messageflow](https://github.com/sakura0689/logbook-kai-messageflow)を
参照してください。

### 2. 航海日誌改を起動する

`plugins` フォルダにjarを置いた状態で、航海日誌改本体**のみ**を起動してください。

### 3. 接続できているか確認する

次のいずれかで確認できます。

- メインウィンドウの**タイトルバー**（`[MessageFlow: 接続中]` / `[MessageFlow: 未接続]`）
- 「拡張」メニューの **MessageFlow** 項目（●が緑になれば接続中）。クリックすると詳細ダイアログが開く

### 4. 設定を変えたいとき

デフォルトではmessageflowが8890番で待ち受け、受け取った通信内容を航海日誌改へ直接渡します。
「直接渡す」をOFFにすると、元のlogbook-kai-messageflowと同じく航海日誌改のプロキシ経由で渡します
（航海日誌改ポート番号は、指定しなければ航海日誌改本体の設定に自動で合わせます）。
拡張メニューのMessageFlow詳細ダイアログ内「設定」から変更できます。ポート番号の変更を反映するには
航海日誌改を再起動してください（受け渡し方式とデバッグログはすぐに反映されます）。

## 質問・不具合報告

不具合や質問があれば、[Issues](../../issues) にお気軽にどうぞ。

## クレジット

- 本プラグイン — tonbo
- [logbook-kai](https://github.com/sakura0689/logbook-kai) — sanaehirotaka, sakura0689
- [logbook-kai-messageflow](https://github.com/sakura0689/logbook-kai-messageflow) — sakura0689

上記2つのプロジェクトはどちらもMITライセンスで公開されており、このプラグインはそれぞれの
ソースコードを移植・改変して作っています。

実装はAnthropicのAIアシスタント [Claude](https://claude.ai) 君が頑張って作ってくれました。

## ライセンス

MIT License。詳しくは [LICENSE](./LICENSE) を、同梱しているサードパーティライブラリについては
[NOTICE.md](./NOTICE.md) を見てください。
