import type { Account, Category, PaymentMethod } from '../types';

/**
 * The category list is deliberately short. Step 3 of the Quick Log has to be
 * answerable without reading — six rows fit on screen, so the choice is a
 * glance and a tap rather than a scroll and a search.
 */
export const CATEGORIES: Category[] = [
  { id: 'food', label: 'Food & Drinks', icon: 'restaurant-outline' },
  { id: 'shopping', label: 'Shopping', icon: 'bag-handle-outline' },
  { id: 'travel', label: 'Travel', icon: 'airplane-outline' },
  { id: 'services', label: 'Services', icon: 'construct-outline' },
  { id: 'entertainment', label: 'Entertainment', icon: 'game-controller-outline' },
  { id: 'health', label: 'Health', icon: 'fitness-outline' },
];

export const ACCOUNTS: Account[] = [
  { id: 'personal', label: 'Personal', isDefault: true },
  { id: 'joint', label: 'Joint' },
  { id: 'business', label: 'Business' },
];

export const PAYMENT_METHODS: PaymentMethod[] = [
  { id: 'credit-card', label: 'Credit Card', isDefault: true },
  { id: 'debit-card', label: 'Debit Card' },
  { id: 'cash', label: 'Cash' },
  { id: 'bank-transfer', label: 'Bank Transfer' },
];

export const DEFAULT_CURRENCY = 'USD';

export const defaultAccount = (): Account =>
  ACCOUNTS.find((a) => a.isDefault) ?? (ACCOUNTS[0] as Account);

export const defaultPaymentMethod = (): PaymentMethod =>
  PAYMENT_METHODS.find((p) => p.isDefault) ?? (PAYMENT_METHODS[0] as PaymentMethod);

export const categoryById = (id: string): Category | undefined =>
  CATEGORIES.find((c) => c.id === id);

export const labelOf = (
  list: ReadonlyArray<{ id: string; label: string }>,
  id: string,
): string => list.find((item) => item.id === id)?.label ?? id;
