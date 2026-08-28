import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useState } from 'react';

import { loadExpenses, saveExpenses } from '../storage/expenseRepository';
import type { Expense } from '../types';
import { closedState, draftToExpense, quickLogReducer } from './quickLogMachine';
import type { QuickLogState } from './quickLogMachine';
import { summariseWeek } from './weekSummary';

type ExpenseContextValue = {
  expenses: Expense[];
  hydrated: boolean;
  quickLog: QuickLogState;
  openQuickLog: () => void;
  cancelQuickLog: () => void;
  dispatchQuickLog: React.Dispatch<Parameters<typeof quickLogReducer>[1]>;
  /** Commits the current draft and closes the flow. */
  confirmQuickLog: () => void;
  weekTotalMinor: number;
  /** Sun..Sat totals for the current week, in minor units. */
  weekBuckets: number[];
};

const ExpenseContext = createContext<ExpenseContextValue | null>(null);

export function ExpenseProvider({ children }: { children: React.ReactNode }) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [hydrated, setHydrated] = useState(false);
  const [quickLog, dispatchQuickLog] = useReducer(quickLogReducer, closedState);

  useEffect(() => {
    let cancelled = false;
    void loadExpenses().then((loaded) => {
      if (cancelled) return;
      setExpenses(loaded);
      setHydrated(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Persist only after the first read has landed, so an empty initial render
  // can never overwrite a real log with [].
  useEffect(() => {
    if (!hydrated) return;
    void saveExpenses(expenses);
  }, [expenses, hydrated]);

  const confirmQuickLog = useCallback(() => {
    if (!quickLog.open) return;
    const expense = draftToExpense(quickLog.draft, newId());
    setExpenses((current) => [expense, ...current]);
    dispatchQuickLog({ type: 'cancel' });
  }, [quickLog]);

  const week = useMemo(() => summariseWeek(expenses), [expenses]);

  const value = useMemo<ExpenseContextValue>(
    () => ({
      expenses,
      hydrated,
      quickLog,
      openQuickLog: () => dispatchQuickLog({ type: 'open' }),
      cancelQuickLog: () => dispatchQuickLog({ type: 'cancel' }),
      dispatchQuickLog,
      confirmQuickLog,
      weekTotalMinor: week.totalMinor,
      weekBuckets: week.buckets,
    }),
    [expenses, hydrated, quickLog, confirmQuickLog, week],
  );

  return <ExpenseContext.Provider value={value}>{children}</ExpenseContext.Provider>;
}

export function useExpenses(): ExpenseContextValue {
  const ctx = useContext(ExpenseContext);
  if (!ctx) throw new Error('useExpenses must be used inside <ExpenseProvider>');
  return ctx;
}

function newId(): string {
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}
