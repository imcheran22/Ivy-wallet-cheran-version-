export type Expense = {
  id: string;
  title: string;
  /**
   * Minor units (cents). Amounts are never held as floats: 0.1 + 0.2 is a bug
   * waiting to be filed as a support ticket about a one-cent discrepancy.
   */
  amountMinor: number;
  currency: string;
  categoryId: string;
  accountId: string;
  paymentMethodId: string;
  /** ISO-8601. When the money was spent, not when the row was typed. */
  occurredAt: string;
  createdAt: string;
};

export type Category = {
  id: string;
  label: string;
  /** Ionicons glyph name. */
  icon: string;
};

export type Account = { id: string; label: string; isDefault?: boolean };
export type PaymentMethod = { id: string; label: string; isDefault?: boolean };
