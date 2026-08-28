import type { Expense } from '../types';
import { startOfWeek } from '../utils/dates';

const MS_PER_DAY = 86_400_000;
const DAYS_IN_WEEK = 7;

export type WeekSummary = {
  totalMinor: number;
  /** Sun..Sat totals in minor units. */
  buckets: number[];
};

/**
 * The header total and the chart are derived in one pass so they can never
 * disagree — two separate reductions over the same list is exactly how a
 * dashboard ends up showing bars that don't add up to the number above them.
 */
export function summariseWeek(expenses: Expense[], now: Date = new Date()): WeekSummary {
  const start = startOfWeek(now).getTime();
  const buckets = new Array<number>(DAYS_IN_WEEK).fill(0);
  let totalMinor = 0;

  for (const expense of expenses) {
    const dayIndex = Math.floor((new Date(expense.occurredAt).getTime() - start) / MS_PER_DAY);
    if (dayIndex < 0 || dayIndex >= DAYS_IN_WEEK) continue;
    buckets[dayIndex] = (buckets[dayIndex] ?? 0) + expense.amountMinor;
    totalMinor += expense.amountMinor;
  }

  return { totalMinor, buckets };
}
