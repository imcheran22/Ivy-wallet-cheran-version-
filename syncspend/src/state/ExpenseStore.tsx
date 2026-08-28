import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useState } from 'react';
import * as Haptics from 'expo-haptics';

import { loadExpenses, saveExpenses } from '../storage/expenseRepository';
import type { Expense } from '../types';
import { closedState, draftToExpense, quickLogReducer } from './quickLogMachine';
import type { QuickLogState } from './quickLogMachine';
import { useQuickLogLaunch } from './useQuickLogLaunch';
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
  deleteExpense: (id: string) => void;
  weekTotalMinor: number;
  /** Sun..Sat totals for the current week, in minor units. */
  weekBuckets: number[];
  /**
   * True when this launch came from the tile or the launcher shortcut. The
   * activity draws over the keyguard, so the dashboard — which is a list of
   * what the owner spends money on — must stay hidden behind the flow.
   */
  launchedForQuickLog: boolean;
};

const ExpenseContext = createContext<ExpenseContextValue | null>(null);

export function ExpenseProvider({ children }: { children: React.ReactNode }) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [hydrated, setHydrated] = useState(false);
  const [quickLog, dispatchQuickLog] = useReducer(quickLogReducer, closedState);
  const [launchedForQuickLog, setLaunchedForQuickLog] = useState(false);

  useQuickLogLaunch(
    useCallback(() => {
      setLaunchedForQuickLog(true);
      dispatchQuickLog({ type: 'open' });
    }, []),
  );

  useEffect(() => {
    let cancelled = false;
    void loadExpenses().then((loaded) => {
      if (cancelled) return;
      setExpenses(sortNewestFirst(loaded));
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
    setExpenses((current) => sortNewestFirst([expense, ...current]));
    dispatchQuickLog({ type: 'cancel' });
    // The card vanishing is the only other signal that the save landed, and it
    // looks identical to Cancel. The tap confirms which of the two happened.
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  }, [quickLog]);

  const deleteExpense = useCallback((id: string) => {
    setExpenses((current) => current.filter((expense) => expense.id !== id));
  }, []);

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
      deleteExpense,
      weekTotalMinor: week.totalMinor,
      weekBuckets: week.buckets,
      launchedForQuickLog,
    }),
    [expenses, hydrated, quickLog, confirmQuickLog, deleteExpense, week, launchedForQuickLog],
  );

  return <ExpenseContext.Provider value={value}>{children}</ExpenseContext.Provider>;
}

export function useExpenses(): ExpenseContextValue {
  const ctx = useContext(ExpenseContext);
  if (!ctx) throw new Error('useExpenses must be used inside <ExpenseProvider>');
  return ctx;
}

/** "Latest" means by when the money was spent, not by when the row was typed. */
function sortNewestFirst(expenses: Expense[]): Expense[] {
  return [...expenses].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
}

function newId(): string {
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}
