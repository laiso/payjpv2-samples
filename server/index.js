require('dotenv').config();
const crypto = require('crypto');
const express = require('express');
const cors = require('cors');

const app = express();

const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map(o => o.trim())
  : undefined;
if (!ALLOWED_ORIGINS) {
  console.warn('[security] ALLOWED_ORIGINS が未設定のため全オリジンを許可します。本番環境では必ず設定してください。');
}
app.use(cors(ALLOWED_ORIGINS ? { origin: ALLOWED_ORIGINS } : undefined));

app.use('/webhook', express.raw({ type: 'application/json' }));

app.use((req, res, next) => {
  if (req.path === '/webhook') {
    return next();
  }
  express.json()(req, res, next);
});

const PAYJP_SECRET_KEY = process.env.PAYJP_SECRET_KEY;
const PAYJP_API_BASE = 'https://api.pay.jp/v2';
const SAMPLE_PRODUCTS = buildSampleProducts();

function buildSampleProducts() {
  const id = process.env.PAYJP_SAMPLE_PRICE_ID;
  if (!id) {
    return [];
  }

  const name = process.env.PAYJP_SAMPLE_PRODUCT_NAME || 'テスト商品';
  const amountRaw = process.env.PAYJP_SAMPLE_PRODUCT_AMOUNT;
  const amount = amountRaw && amountRaw.trim() !== '' && Number.isFinite(Number(amountRaw))
    ? Number(amountRaw)
    : 100;

  return [
    {
      id: String(id),
      name: String(name),
      amount: amount
    }
  ];
}

/**
 * Validates redirect URLs for Checkout Session.
 * - https: any host (production / staging)
 * - http: loopback only (localhost, 127.0.0.1, ::1) — rejects hostnames like localhost.evil.com
 * - payjpcheckoutexample: custom scheme for the sample app deep link
 */
function isAllowedRedirectUrl(urlString) {
  let parsed;
  try {
    parsed = new URL(urlString);
  } catch {
    return false;
  }

  const protocol = parsed.protocol.toLowerCase();

  if (protocol === 'https:') {
    return true;
  }

  if (protocol === 'http:') {
    const host = parsed.hostname.toLowerCase();
    return (
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host === '::1'
    );
  }

  if (protocol === 'payjpcheckoutexample:') {
    return true;
  }

  return false;
}

// ヘルスチェック
app.get('/', (_, res) => {
  res.json({ status: 'ok', message: 'PAY.JP Checkout V2 Sample Server' });
});

// サンプル商品一覧
app.get('/products', (_, res) => {
  res.json({ products: SAMPLE_PRODUCTS });
});

// Checkout Session 作成エンドポイント
app.post('/create-checkout-session', async (req, res) => {
  try {
    const { price_id, quantity, success_url, cancel_url } = req.body ?? {};

    if (!PAYJP_SECRET_KEY) {
      return res.status(500).json({ error: 'PAYJP_SECRET_KEY が設定されていません' });
    }

    if (!price_id || !quantity || !success_url || !cancel_url) {
      return res.status(400).json({ error: '必須パラメータが不足しています' });
    }

    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 100) {
      return res.status(400).json({ error: 'quantity は 1〜100 の整数で指定してください' });
    }

    if (!isAllowedRedirectUrl(success_url) || !isAllowedRedirectUrl(cancel_url)) {
      return res.status(400).json({ error: '許可されていないURLスキームです' });
    }

    const response = await fetch(`${PAYJP_API_BASE}/checkout/sessions`, {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${Buffer.from(PAYJP_SECRET_KEY + ':').toString('base64')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        line_items: [
          {
            price_id: price_id,
            quantity: quantity
          }
        ],
        mode: 'payment',
        payment_method_types: ['card', 'paypay', 'apple_pay'],
        success_url: success_url,
        cancel_url: cancel_url
      })
    });

    const session = await response.json();

    if (!response.ok) {
      console.error('PAY.JP API Error:', session);
      return res.status(response.status).json({ error: 'PAY.JP APIエラーが発生しました' });
    }

    console.log('Checkout session created:', session.id);
    console.log('Checkout URL:', session.url);

    res.json({
      id: session.id,
      url: session.url,
      status: session.status
    });
  } catch (error) {
    console.error('Checkout session creation failed:', error);
    res.status(500).json({ error: 'サーバーエラーが発生しました' });
  }
});

// Webhook エンドポイント
app.post('/webhook', (req, res) => {
  const webhookToken = req.headers['x-payjp-webhook-token'];
  const expectedToken = process.env.PAYJP_WEBHOOK_SECRET;

  if (!expectedToken) {
    console.warn('PAYJP_WEBHOOK_SECRET is not set — rejecting webhook request');
    return res.status(500).json({ error: 'Webhook secret is not configured' });
  }

  const tokenBuf = Buffer.from(String(webhookToken ?? ''), 'utf8');
  const expectedBuf = Buffer.from(expectedToken, 'utf8');
  if (
    tokenBuf.length !== expectedBuf.length ||
    !crypto.timingSafeEqual(tokenBuf, expectedBuf)
  ) {
    console.error('Invalid webhook token');
    return res.status(401).json({ error: 'Invalid webhook token' });
  }

  let event;
  try {
    event = JSON.parse(req.body.toString('utf8'));
  } catch (err) {
    console.error('Webhook parsing failed:', err);
    return res.status(400).json({ error: 'Invalid JSON' });
  }

  console.log('Webhook received:', event.type);

  switch (event.type) {
    case 'checkout.session.completed':
      console.log('決済完了:', event.data);
      // ここで商品発送などの処理を実行
      // 例: await fulfillOrder(event.data);
      break;

    case 'checkout.session.expired':
      console.log('セッション期限切れ:', event.data);
      break;

    default:
      console.log('未処理のイベント:', event.type);
  }

  res.json({ received: true });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on http://localhost:${PORT}`);
  console.log('');
  console.log('エンドポイント:');
  console.log(`  POST http://localhost:${PORT}/create-checkout-session`);
  console.log(`  POST http://localhost:${PORT}/webhook`);
});
