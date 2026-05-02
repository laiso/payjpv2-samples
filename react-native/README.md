# PAY.JP Checkout V2 Expo サンプル

Expo (React Native) から PAY.JP Checkout V2 を試すサンプルアプリです。`server/` のサンプル API と連携し、`expo-web-browser.openBrowserAsync` で Checkout を開き (iOS は `SFSafariViewController`、Android は Chrome Custom Tabs)、カスタムスキームのリダイレクトを `Linking` で受け取って結果を判定します。

PAY.JP Checkout V2 の新 SDK はネイティブモジュールを追加する必要がなく、ブラウザ + URL スキームだけで動くため、Expo Prebuild + Development Build で実装できます。

## 前提条件

- Node.js 18 以上
- `server/` が `http://localhost:3000` で起動していること
- iOS: Xcode + iOS シミュレータ (Expo Go は非対応 — `payjpcheckoutexample://` のディープリンクが Expo Go に登録できないため)
- Android: Android Studio + エミュレータ + JDK + `npx expo run:android` で dev build を作成

## 起動手順

### iOS / Android (どちらも dev build)

```bash
cd react-native
npm install
npm run ios       # → expo run:ios
npm run android   # → expo run:android
```

ディープリンク (`payjpcheckoutexample://...`) でアプリに復帰するため、iOS / Android とも `expo run:*` で dev build を作る必要があります (Expo Go では `payjpcheckoutexample://` を intent filter に持てないため復帰しない)。

dev build の初回は Gradle / SDK のセットアップに数分〜10 分程度かかります。2 回目以降は差分ビルドで高速。

## 画面フロー

1. バックエンド URL 入力（既定: Android=`http://10.0.2.2:3000` / iOS=`http://localhost:3000`）
2. 「商品を取得」で `/products` を呼ぶ
3. 商品を選択 →「Checkout を開く」で `/create-checkout-session` → `WebBrowser.openBrowserAsync` で Checkout を開く (iOS は SFSafariViewController、Android は Chrome Custom Tabs)
4. 決済完了 / キャンセルで `payjpcheckoutexample://checkout/success|cancel` がアプリにディープリンクされ、`Linking` リスナが捕捉して `WebBrowser.dismissBrowser()` を呼ぶ
5. 結果メッセージを表示。「最初からやり直す」で初期状態へ

## success_url は「受付シグナル」

Android / iOS / Flutter / bare RN サンプルと同じ方針で、`success_url` にリダイレクトされた時点では **決済完了ではなく受付済み** として扱います。確定判定はサーバーの `checkout.session.completed` Webhook 側で行ってください。

## ディープリンク設定

`app.json` の `"scheme": "payjpcheckoutexample"` 1 行で完結:

- `AndroidManifest.xml` の `<intent-filter>` 編集不要
- `Info.plist` の `CFBundleURLTypes` 編集不要
- `AppDelegate` の `application:openURL:` 実装不要

`npx expo run:android` / `npx expo run:ios` 実行時、Expo prebuild がネイティブ側を生成します。

## Checkout 起動 API: openBrowserAsync + Linking

このサンプルは `WebBrowser.openBrowserAsync(session.url)` で Checkout を開き、別途 `Linking.addEventListener('url', ...)` でカスタムスキームのリダイレクトを検知して `WebBrowser.dismissBrowser()` を呼んでブラウザを閉じます。

- iOS: `SFSafariViewController` (Safari と同じ WebKit コンテキスト → **Apple Pay JS が動作する**)
- Android: Chrome Custom Tabs

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
