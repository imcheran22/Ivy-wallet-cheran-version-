import AsyncStorage from '@react-native-async-storage/async-storage';

import type { Expense } from '../types';

const KEY = 'syncspend.expenses.v1';

/**
 * One key holding the whole list. An expense log that a person types by hand
 * stays in the low thousands of rows for years, which is well inside what a
 * single JSON blob reads and writes without being felt.
 */
export async function loadExpenses(): Promise<Expense[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isExpense) : [];
  } catch {
    // A corrupt blob must not brick the app on launch: an empty log is
    // recoverable, a crash loop on the splash screen is not.
    return [];
  }
}

export async function saveExpenses(expenses: Expense[]): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify(expenses));
}

function isExpense(value: unknown): value is Expense {
  if (typeof value !== 'object' || value === null) return false;
  const e = value as Partial<Expense>;
  return (
    typeof e.id === 'string' &&
    typeof e.title === 'string' &&
    typeof e.amountMinor === 'number' &&
    typeof e.occurredAt === 'string'
  );
}
