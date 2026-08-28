const MINOR_UNITS = 100;
const MAX_DIGITS_BEFORE_POINT = 7;
const MAX_DECIMALS = 2;

/**
 * The numpad edits a *string*, not a number, and only becomes a number at the
 * point of saving. Typing "1.0" has to survive on screen as "1.0" — a number
 * would render it back as "1" and eat the keystroke the user just made.
 */
export function pressDigit(input: string, digit: string): string {
  const [whole = '', decimals] = input.split('.');
  if (decimals !== undefined) {
    if (decimals.length >= MAX_DECIMALS) return input;
    return input + digit;
  }
  if (whole === '0') return digit;
  if (whole.length >= MAX_DIGITS_BEFORE_POINT) return input;
  return input + digit;
}

export function pressDecimalPoint(input: string): string {
  if (input.includes('.')) return input;
  return (input === '' ? '0' : input) + '.';
}

export function pressBackspace(input: string): string {
  return input.slice(0, -1);
}

export function toMinorUnits(input: string): number {
  if (input === '' || input === '.') return 0;
  const [whole = '0', decimals = ''] = input.split('.');
  const padded = (decimals + '00').slice(0, MAX_DECIMALS);
  return Number(whole || '0') * MINOR_UNITS + Number(padded || '0');
}

export function isCommittableAmount(input: string): boolean {
  return toMinorUnits(input) > 0;
}

/** What the numpad shows while typing: echoes the raw keystrokes. */
export function formatAmountInput(input: string, currency: string): string {
  return symbolFor(currency) + (input === '' ? '0' : input);
}

/** What every settled amount shows: always two decimals, thousands separated. */
export function formatMinorUnits(amountMinor: number, currency: string): string {
  const sign = amountMinor < 0 ? '-' : '';
  const abs = Math.abs(amountMinor);
  const whole = Math.floor(abs / MINOR_UNITS).toLocaleString('en-US');
  const decimals = String(abs % MINOR_UNITS).padStart(2, '0');
  return sign + symbolFor(currency) + whole + '.' + decimals;
}

export function symbolFor(currency: string): string {
  const symbols: Record<string, string> = { USD: '$', EUR: '€', GBP: '£', INR: '₹', JPY: '¥' };
  return symbols[currency] ?? currency + ' ';
}
