import test from 'node:test';
import assert from 'node:assert/strict';
import { formatMoney, parseAmountCents } from '../src/lib/money.ts';

test('les montants restent exacts au-delà de Number.MAX_SAFE_INTEGER', () => {
  assert.equal(parseAmountCents('90071992547409.93'), '9007199254740993');
  assert.equal(parseAmountCents('1,01'), '101');
  assert.equal(formatMoney('9007199254740993'), '90 071 992 547 409,93 XAF');
  assert.equal(formatMoney('-101'), '-1,01 XAF');
  assert.equal(formatMoney('100'), '1 XAF');
  assert.throws(() => formatMoney(9007199254740992), RangeError);
});

test('les entrées fractionnaires, négatives, exponentielles ou hors BIGINT sont rejetées', () => {
  for (const input of ['', '0', '-1', '1.001', '1e3', 'Infinity', '92233720368547758.08', '9'.repeat(1000)]) {
    assert.equal(parseAmountCents(input), null, input);
  }
});
