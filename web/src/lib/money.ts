const MAX_AMOUNT = 9223372036854775807n;

export function parseAmountCents(input: string): string | null {
  if (input.length > 22 || !/^\d+(?:[.,]\d{1,2})?$/.test(input)) return null;
  const [whole, fraction = ''] = input.replace(',', '.').split('.');
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  return cents > 0n && cents <= MAX_AMOUNT ? cents.toString() : null;
}

export function formatMoney(cents: bigint | number | string, currency = 'XAF'): string {
  if (typeof cents === 'number' && !Number.isSafeInteger(cents)) throw new RangeError('Montant numérique imprécis');
  const value = BigInt(cents);
  const absolute = value < 0n ? -value : value;
  const fraction = (absolute % 100n).toString().padStart(2, '0').replace(/0+$/, '');
  return `${value < 0n ? '-' : ''}${(absolute / 100n).toLocaleString('fr-FR')}${fraction ? `,${fraction}` : ''} ${currency}`;
}
