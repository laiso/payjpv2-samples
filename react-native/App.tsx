import React, {useCallback, useState} from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import * as WebBrowser from 'expo-web-browser';
import {parseRedirect} from './src/checkout';

const REDIRECT_PREFIX = 'payjpcheckoutexample://checkout';
const SUCCESS_URL = `${REDIRECT_PREFIX}/success`;
const CANCEL_URL = `${REDIRECT_PREFIX}/cancel`;

type Product = {id: string; name: string; amount: number};
type CheckoutSession = {id: string; url: string; status: string};

const defaultBackendUrl = () =>
  Platform.OS === 'android' ? 'http://10.0.2.2:3000' : 'http://localhost:3000';

export default function App() {
  const [backendUrl, setBackendUrl] = useState(defaultBackendUrl());
  const [products, setProducts] = useState<Product[] | null>(null);
  const [selected, setSelected] = useState<Product | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultMessage, setResultMessage] = useState<string | null>(null);

  const api = useCallback(
    (path: string, init?: RequestInit) =>
      fetch(`${backendUrl.trim().replace(/\/$/, '')}${path}`, init),
    [backendUrl],
  );

  const fetchProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    setResultMessage(null);
    try {
      const res = await api('/products');
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
      const body = (await res.json()) as {products: Product[]};
      setProducts(body.products);
      setSelected(body.products[0] ?? null);
    } catch (e: unknown) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, [api]);

  const startCheckout = useCallback(async () => {
    if (!selected) return;
    setLoading(true);
    setError(null);
    setResultMessage(null);
    try {
      const res = await api('/create-checkout-session', {
        method: 'POST',
        headers: {'content-type': 'application/json'},
        body: JSON.stringify({
          price_id: selected.id,
          quantity: 1,
          success_url: SUCCESS_URL,
          cancel_url: CANCEL_URL,
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
      const session = (await res.json()) as CheckoutSession;
      const outcome = await WebBrowser.openAuthSessionAsync(
        session.url,
        REDIRECT_PREFIX,
      );
      if (outcome.type === 'success' && outcome.url) {
        const kind = parseRedirect(outcome.url);
        if (kind === 'success') {
          setResultMessage(
            '決済受付が完了しました。Webhook での確定を確認してください。',
          );
        } else if (kind === 'cancel') {
          setResultMessage('キャンセルされました。');
        } else {
          setResultMessage(`未知のリダイレクト: ${outcome.url}`);
        }
      } else if (outcome.type === 'cancel' || outcome.type === 'dismiss') {
        setResultMessage('Checkout を閉じました。');
      }
    } catch (e: unknown) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, [api, selected]);

  const reset = () => {
    setProducts(null);
    setSelected(null);
    setResultMessage(null);
    setError(null);
  };

  return (
    <SafeAreaView style={styles.flex}>
      <StatusBar barStyle="dark-content" />
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>PAY.JP Checkout V2 (Expo)</Text>

        <Text style={styles.label}>バックエンド URL</Text>
        <TextInput
          style={styles.input}
          value={backendUrl}
          onChangeText={setBackendUrl}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          placeholder="http://10.0.2.2:3000"
        />

        <Pressable
          style={[styles.primary, loading && styles.disabled]}
          onPress={fetchProducts}
          disabled={loading}>
          <Text style={styles.primaryText}>商品を取得</Text>
        </Pressable>

        {loading && <ActivityIndicator style={styles.loader} size="large" />}

        {error && (
          <View style={styles.errorCard}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        {products && (
          <View>
            <Text style={[styles.label, styles.sectionGap]}>商品一覧</Text>
            {products.length === 0 && <Text>商品がありません</Text>}
            {products.map(p => {
              const isSelected = selected?.id === p.id;
              return (
                <Pressable
                  key={p.id}
                  style={styles.row}
                  onPress={() => setSelected(p)}>
                  <View
                    style={[
                      styles.radioOuter,
                      isSelected && styles.radioOuterSelected,
                    ]}>
                    {isSelected && <View style={styles.radioInner} />}
                  </View>
                  <View style={styles.rowBody}>
                    <Text style={styles.rowTitle}>{p.name}</Text>
                    <Text style={styles.rowSub}>
                      ¥{p.amount} / {p.id}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
            <Pressable
              style={[
                styles.primary,
                (!selected || loading) && styles.disabled,
              ]}
              onPress={startCheckout}
              disabled={!selected || loading}>
              <Text style={styles.primaryText}>Checkout を開く</Text>
            </Pressable>
          </View>
        )}

        {resultMessage && (
          <View style={styles.card}>
            <Text>{resultMessage}</Text>
            <Pressable style={styles.linkButton} onPress={reset}>
              <Text style={styles.linkButtonText}>最初からやり直す</Text>
            </Pressable>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: {flex: 1, backgroundColor: '#fff'},
  container: {
    paddingHorizontal: 16,
    paddingVertical: 16,
    gap: 12,
  },
  title: {fontSize: 22, fontWeight: '700', marginBottom: 8},
  label: {fontSize: 13, color: '#555'},
  sectionGap: {marginTop: 16},
  input: {
    borderWidth: 1,
    borderColor: '#888',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  primary: {
    backgroundColor: '#3a5a85',
    paddingVertical: 14,
    borderRadius: 24,
    alignItems: 'center',
  },
  primaryText: {color: '#fff', fontWeight: '600', fontSize: 16},
  disabled: {opacity: 0.5},
  loader: {marginVertical: 8},
  errorCard: {backgroundColor: '#fde7e7', padding: 12, borderRadius: 8},
  errorText: {color: '#842029'},
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    gap: 12,
  },
  rowBody: {flex: 1},
  rowTitle: {fontSize: 16, marginBottom: 2},
  rowSub: {color: '#666', fontSize: 13},
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: '#888',
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuterSelected: {borderColor: '#3a5a85'},
  radioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#3a5a85',
  },
  card: {backgroundColor: '#f4f4f4', padding: 12, borderRadius: 8},
  linkButton: {marginTop: 8},
  linkButtonText: {color: '#3a5a85', fontWeight: '600'},
});
