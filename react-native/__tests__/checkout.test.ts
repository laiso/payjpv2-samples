import {parseRedirect} from '../src/checkout';

describe('parseRedirect', () => {
  it('returns success for the success redirect', () => {
    expect(parseRedirect('payjpcheckoutexample://checkout/success')).toBe(
      'success',
    );
  });

  it('returns cancel for the cancel redirect', () => {
    expect(parseRedirect('payjpcheckoutexample://checkout/cancel')).toBe(
      'cancel',
    );
  });

  it('ignores unrelated urls', () => {
    expect(parseRedirect('https://example.com/checkout/success')).toBeNull();
  });

  it('does not misclassify cancel when the query contains success', () => {
    expect(
      parseRedirect(
        'payjpcheckoutexample://checkout/cancel?session=cs_success_123',
      ),
    ).toBe('cancel');
  });
});
