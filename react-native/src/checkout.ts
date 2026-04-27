export type CheckoutResult = 'success' | 'cancel';

export function parseRedirect(url: string): CheckoutResult | null {
  const prefix = 'payjpcheckoutexample://checkout';
  if (!url.startsWith(prefix)) {
    return null;
  }

  const path = url.slice(prefix.length).split('?')[0];
  if (path === '/success') {
    return 'success';
  }
  if (path === '/cancel') {
    return 'cancel';
  }

  return null;
}
