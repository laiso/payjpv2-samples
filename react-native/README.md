# PAY.JP Checkout V2 Expo サンプル

Expo (React Native) から PAY.JP Checkout V2 を試すサンプルアプリです。`server/` のサンプル API と連携し、`expo-web-browser` の認証セッションで Checkout を開き、カスタムスキームのリダイレクトで結果を受け取ります。

PAY.JP Checkout V2 の新 SDK はネイティブモジュールを追加する必要がなく、Web ビュー + URL スキームだけで動くため、Expo の managed workflow で実装できます。

## 前提条件

- Node.js 18 以上
- `server/` が `http://localhost:3000` で起動していること
- iOS: Xcode + iOS シミュレータ（または Expo Go）
- Android: Android Studio + エミュレータ + JDK + `npx expo run:android` で dev build を作成

## 起動手順

### iOS（Expo Go で完結）

```bash
cd react-native
npm install
npm run ios   # → expo run:ios
```

iOS は `ASWebAuthenticationSession` がアプリ側のスキーム登録なしでもセッション内でリダイレクトを捕捉するため、Expo Go でも動きます。`npx expo start --ios` でも可。

### Android（dev build が必要）

```bash
npm run android   # → expo run:android
```

**Android では Expo Go 経由では成功リダイレクトが届きません。** 理由:

- `payjpcheckoutexample://` は Custom Tabs から OS のディープリンクとしてアプリに配送される
- Expo Go (`host.exp.exponent`) は自身の `exp://` スキームしか intent filter に持たないため、`payjpcheckoutexample://` を受信できない
- `npx expo run:android` で dev build を作ると、`app.json` の `scheme` が `AndroidManifest.xml` に書き込まれ、リダイレクトが正しくアプリに戻る

dev build の初回は Gradle / SDK のセットアップに数分〜10 分程度かかります。2 回目以降は差分ビルドで高速。

## 画面フロー

1. バックエンド URL 入力（既定: Android=`http://10.0.2.2:3000` / iOS=`http://localhost:3000`）
2. 「商品を取得」で `/products` を呼ぶ
3. 商品を選択 →「Checkout を開く」で `/create-checkout-session` → `expo-web-browser` が認証セッションで Checkout を表示
4. 決済完了 / キャンセルで `payjpcheckoutexample://checkout/success|cancel` にリダイレクトされると、`openAuthSessionAsync` が自動で閉じて結果 URL を返す
5. 結果メッセージを表示。「最初からやり直す」で初期状態へ

## success_url は「受付シグナル」

Android / iOS / Flutter / bare RN サンプルと同じ方針で、`success_url` にリダイレクトされた時点では **決済完了ではなく受付済み** として扱います。確定判定はサーバーの `checkout.session.completed` Webhook 側で行ってください。

## ディープリンク設定

`app.json` の `"scheme": "payjpcheckoutexample"` 1 行で完結:

- `AndroidManifest.xml` の `<intent-filter>` 編集不要
- `Info.plist` の `CFBundleURLTypes` 編集不要
- `AppDelegate` の `application:openURL:` 実装不要

`npx expo run:android` / `npx expo run:ios` 実行時、Expo prebuild がネイティブ側を生成します。

## expo-web-browser.openAuthSessionAsync

bare RN サンプル（旧版）では、外部ブラウザで Checkout を開いて `Linking.addEventListener('url', ...)` + `Linking.getInitialURL()` でリダイレクトを拾い、コールドスタート対策で直近 URI の重複処理もしていました。

Expo では `openAuthSessionAsync(url, redirectUrl)` がこれを内包します。

- iOS: `ASWebAuthenticationSession`
- Android: Chrome Custom Tabs

ブラウザセッション内でリダイレクトが発生した時点で自動的に閉じ、戻り値として `{type: 'success', url}` を返すため、URL リスナの登録やコールドスタート復帰の設計が不要です。

## Android のローカル HTTP 接続

`http://10.0.2.2:3000` への接続用に `app.json` で

```json
"android": {
  "usesCleartextTraffic": true
}
```

を有効化済みです。release ビルドでは HTTPS を用意してください。

## テスト

```bash
npm test
```

`parseRedirect` の単体テストを `jest-expo` プリセットで実行します。
