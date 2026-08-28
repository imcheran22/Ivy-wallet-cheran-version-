import assert from 'node:assert/strict';
import { test } from 'node:test';

import { canAdvance, draftToExpense, emptyDraft, quickLogReducer } from '../state/quickLogMachine';
import { summariseWeek } from '../state/weekSummary';
import { formatMinorUnits, pressBackspace, pressDecimalPoint, pressDigit, toMinorUnits } from '../utils/money';
import { startOfWeek } from '../utils/dates';

test('numpad refuses a second decimal point and a third decimal place', () => {
  assert.equal(pressDecimalPoint(pressDecimalPoint('5')), '5.');
  assert.equal(pressDigit(pressDigit(pressDigit('5.', '0'), '0'), '9'), '5.00');
});

test('numpad leading zero is replaced, not appended to', () => {
  assert.equal(pressDigit('0', '5'), '5');
  assert.equal(pressDecimalPoint(''), '0.');
});

test('backspace walks the buffer back to empty', () => {
  assert.equal(pressBackspace(pressBackspace('5.0')), '5');
  assert.equal(pressBackspace(''), '');
});

test('amounts are parsed to exact minor units', () => {
  assert.equal(toMinorUnits('5'), 500);
  assert.equal(toMinorUnits('5.'), 500);
  assert.equal(toMinorUnits('5.4'), 540);
  assert.equal(toMinorUnits('0.07'), 7);
  assert.equal(toMinorUnits(''), 0);
});

test('minor units render with two decimals and thousands separators', () => {
  assert.equal(formatMinorUnits(500, 'USD'), '$5.00');
  assert.equal(formatMinorUnits(12038, 'USD'), '$120.38');
  assert.equal(formatMinorUnits(123456789, 'USD'), '$1,234,567.89');
});

test('a step cannot be committed half-answered', () => {
  const draft = emptyDraft();
  assert.equal(canAdvance('name', draft), false);
  assert.equal(canAdvance('name', { ...draft, title: '  ' }), false);
  assert.equal(canAdvance('name', { ...draft, title: 'Coffee' }), true);
  assert.equal(canAdvance('amount', { ...draft, amountInput: '0.00' }), false);
  assert.equal(canAdvance('amount', { ...draft, amountInput: '5' }), true);
});

test('the four steps run in order and picking a category skips to confirm', () => {
  let state = quickLogReducer({ open: false }, { type: 'open' });
  assert.equal(state.open && state.step, 'name');

  state = quickLogReducer(state, { type: 'setTitle', title: 'Coffee' });
  state = quickLogReducer(state, { type: 'next' });
  assert.equal(state.open && state.step, 'amount');

  state = quickLogReducer(state, { type: 'setAmountInput', amountInput: '5' });
  state = quickLogReducer(state, { type: 'next' });
  assert.equal(state.open && state.step, 'category');

  state = quickLogReducer(state, { type: 'pickCategory', categoryId: 'food' });
  assert.equal(state.open && state.step, 'confirm');
  assert.equal(state.open && state.draft.categoryId, 'food');
});

test('next is a no-op while the current step is unanswered', () => {
  const opened = quickLogReducer({ open: false }, { type: 'open' });
  assert.deepEqual(quickLogReducer(opened, { type: 'next' }), opened);
});

test('back from the first step closes the flow and discards the draft', () => {
  let state = quickLogReducer({ open: false }, { type: 'open' });
  state = quickLogReducer(state, { type: 'setTitle', title: 'Coffee' });
  assert.deepEqual(quickLogReducer(state, { type: 'back' }), { open: false });
});

test('back preserves what was already typed', () => {
  let state = quickLogReducer({ open: false }, { type: 'open' });
  state = quickLogReducer(state, { type: 'setTitle', title: 'Coffee' });
  state = quickLogReducer(state, { type: 'next' });
  state = quickLogReducer(state, { type: 'setAmountInput', amountInput: '5' });
  state = quickLogReducer(state, { type: 'back' });
  assert.equal(state.open && state.step, 'name');
  assert.equal(state.open && state.draft.title, 'Coffee');
  assert.equal(state.open && state.draft.amountInput, '5');
});

test('a draft becomes an expense with defaults filled in', () => {
  const draft = { ...emptyDraft(), title: '  Coffee  ', amountInput: '5', categoryId: 'food' };
  const expense = draftToExpense(draft, 'id-1');
  assert.equal(expense.title, 'Coffee');
  assert.equal(expense.amountMinor, 500);
  assert.equal(expense.accountId, 'personal');
  assert.equal(expense.paymentMethodId, 'credit-card');
});

test('draftToExpense refuses a draft with no category', () => {
  assert.throws(() => draftToExpense({ ...emptyDraft(), title: 'x', amountInput: '5' }, 'id'));
});

test('the week summary buckets Sun..Sat and ignores other weeks', () => {
  const now = new Date(2026, 7, 26, 12, 0, 0); // a Wednesday
  const sunday = startOfWeek(now);
  const at = (dayOffset: number, hour = 9) => {
    const d = new Date(sunday);
    d.setDate(d.getDate() + dayOffset);
    d.setHours(hour);
    return d.toISOString();
  };
  const expense = (occurredAt: string, amountMinor: number) => ({
    id: occurredAt + amountMinor,
    title: 't',
    amountMinor,
    currency: 'USD',
    categoryId: 'food',
    accountId: 'personal',
    paymentMethodId: 'cash',
    occurredAt,
    createdAt: occurredAt,
  });

  const { totalMinor, buckets } = summariseWeek(
    [
      expense(at(0), 1000),
      expense(at(3), 500),
      expense(at(3, 18), 250),
      expense(at(-2), 9999), // last week
      expense(at(9), 9999), // next week
    ],
    now,
  );

  assert.equal(totalMinor, 1750);
  assert.deepEqual(buckets, [1000, 0, 0, 750, 0, 0, 0]);
});
