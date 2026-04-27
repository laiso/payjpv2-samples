# PAY.JP Checkout V2 サンプル

[PAY.JP](https://pay.jp/) の **Checkout V2** を試すためのサンプル集です。クライアント（モバイル）とローカル開発用のバックエンドを同梱しています。セットアップと動作確認の手順はこの README にまとめています。

## 構成

| ディレクトリ | 内容 |
|--------------|------|
| [`android/`](android/) | Android アプリ（サンプル API と連携し、Checkout を開く） |
| [`server/`](server/) | Node.js 製のサンプル API（商品一覧・Checkout Session 作成・Webhook 受信） |
| [`ios/`](ios/) | iOS アプリ（SwiftUI・サンプル API と連携し Safari で Checkout を開く） |
| [`flutter/`](flutter/) | Flutter アプリ（Android / iOS 共通・外部ブラウザで Checkout を開く） |
| [`react-native/`](react-native/) | React Native アプリ（Android / iOS 共通・Linking で外部ブラウザ起動） |

## 前提条件

- Node.js 18 以上
- PAY.JP アカウント
- PAY.JP ダッシュボードで Price オブジェクトを作成済み
- PayPay を使うテスト環境

このサンプルサーバーは `payment_method_types` を `['card', 'paypay']` に固定しています。PayPay が有効でないアカウントでは Checkout Session 作成が失敗する可能性があります。

## サーバーセットアップ

### 1. 依存関係をインストール

```bash
cd server
npm install
```

### 2. 環境変数を設定

```bash
cp .env.example .env
```

最低限、次の値を `.env` に設定してください。

```dotenv
PAYJP_SECRET_KEY=sk_test_xxxxx
PAYJP_SAMPLE_PRICE_ID=price_xxxxx
PAYJP_WEBHOOK_SECRET=your_webhook_secret
```

任意で次の値も設定できます。

- `PAYJP_SAMPLE_PRODUCT_NAME`: `/products` で返すサンプル商品名。未設定時は `テスト商品`
- `PAYJP_SAMPLE_PRODUCT_AMOUNT`: `/products` で返すサンプル金額。未設定時は `100`
- `ALLOWED_ORIGINS`: CORS 許可オリジン。カンマ区切り。未設定時は全オリジン許可
- `PORT`: サーバーポート。既定値は `3000`

### 3. サーバーを起動

```bash
npm start
```

サーバーが `http://localhost:3000` で起動します。

### 4. Webhook をローカルに転送

```bash
payjp-cli listen --forward-to http://localhost:3000/webhook
```

`success_url` へのリダイレクトだけでは決済完了は確定しません。注文確定や発送などの後続処理は、必ず `checkout.session.completed` の Webhook を受けてから行ってください。

## サーバー API

### `GET /products`

サンプル商品一覧を返します。

レスポンス例:

```json
{
  "products": [
    { "id": "price_xxx", "name": "テスト商品A", "amount": 100 }
  ]
}
```

### `POST /create-checkout-session`

Checkout Session を作成します。

リクエスト例:

```json
{
  "price_id": "price_xxx",
  "quantity": 1,
  "success_url": "payjpcheckoutexample://checkout/success",
  "cancel_url": "payjpcheckoutexample://checkout/cancel"
}
```

レスポンス例:

```json
{
  "id": "cs_xxx",
  "url": "https://checkout.pay.jp/...",
  "status": "open"
}
```

`success_url` / `cancel_url` には次のスキームだけを許可しています。

- `https://`
- `http://localhost`
- `payjpcheckoutexample://`

### `POST /webhook`

PAY.JP からの Webhook を受信します。`x-payjp-webhook-token` が `.env` の `PAYJP_WEBHOOK_SECRET` と一致しない場合は拒否します。

処理対象イベント:

- `checkout.session.completed`
- `checkout.session.expired`

## クライアント起動

### Android

1. Android Studio で `android/` をプロジェクトとして開きます。
2. アプリのバックエンド URL に、エミュレータなら `http://10.0.2.2:3000`、実機なら開発マシンの LAN IP を入力します。
3. 商品を選び、Checkout を開始します。

### iOS

1. Xcode で `ios/PayJPCheckoutExample.xcodeproj` を開きます。
2. シミュレータでは既定の `http://localhost:3000` のまま動かせます。実機は Mac の LAN IP に変更してください。
3. 商品を選び、Safari で Checkout を開始します。

### Flutter

1. `cd flutter && flutter pub get`
2. iOS は初回のみ `flutter config --enable-swift-package-manager`（CocoaPods は不要・Podfile もコミットしていません）
3. `flutter run` でエミュレータ / シミュレータに展開。バックエンド URL 既定値は Android エミュレータが `http://10.0.2.2:3000`、iOS シミュレータが `http://localhost:3000`。
4. 詳細は [`flutter/README.md`](flutter/README.md)。

### React Native

1. `cd react-native && npm install`
2. iOS は `cd ios && bundle install && bundle exec pod install && cd ..`
3. `npm start` で Metro を起動、別シェルで `npm run android` または `npm run ios`
4. 詳細は [`react-native/README.md`](react-native/README.md)。

iOS アプリは `success_url` に戻った時点では「決済完了」ではなく、Webhook 確認待ちとして扱います。`success_url` は受付済みのシグナルであって、決済確定そのものではありません。

コマンドラインでビルドする例:

```bash
xcodebuild -project ios/PayJPCheckoutExample.xcodeproj -target PayJPCheckoutExample -sdk iphonesimulator -configuration Debug build
```

成果物は `ios/build/Debug-iphonesimulator/PayJPCheckoutExample.app` に出力されます。プロジェクトの iOS デプロイメントターゲットは `26.1` です。

## テスト

テストカード、PayPay のテスト手順、テスト用アカウントについては [PAY.JP ドキュメント](https://docs.pay.jp/v2/) を参照してください。

## 注意事項

- 秘密鍵（`sk_xxx`）はクライアントサイドに含めないでください
- `success_url` だけで決済完了扱いしないでください
- Webhook を使わない場合、このサンプルは注文確定の正判定を提供しません

## 参考リンク

- [server/README.md](server/README.md)
- [Checkout ガイド](https://docs.pay.jp/v2/guide/payments/checkout)
- [PAY.JP API リファレンス](https://docs.pay.jp/v2/api)
- [PAY.JP CLI](https://docs.pay.jp/v2/guide/developers/payjp-cli)
- [PAY.JP モバイルアプリ（Android）](https://pay.jp/docs/mobileapp-android)
