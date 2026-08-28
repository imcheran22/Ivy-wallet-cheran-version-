import React, { useCallback, useEffect } from 'react';
import { Alert, BackHandler, FlatList, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FloatingActionButton } from '../components/common/FloatingActionButton';
import { EmptyState } from '../components/dashboard/EmptyState';
import { TransactionRow } from '../components/dashboard/TransactionRow';
import { WeekBarChart } from '../components/dashboard/WeekBarChart';
import { QuickLogFlow } from '../components/quicklog/QuickLogFlow';
import { DEFAULT_CURRENCY } from '../data/catalog';
import { useExpenses } from '../state/ExpenseStore';
import { useStyles } from '../theme/ThemeProvider';
import type { Palette } from '../theme/tokens';
import { space, type } from '../theme/tokens';
import type { Expense } from '../types';
import { formatMinorUnits } from '../utils/money';

const LATEST_LIMIT = 30;

export function DashboardScreen() {
  const styles = useStyles(makeStyles);
  const insets = useSafeAreaInsets();
  const {
    expenses,
    hydrated,
    weekTotalMinor,
    weekBuckets,
    openQuickLog,
    deleteExpense,
    quickLog,
    launchedForQuickLog,
  } = useExpenses();

  // Launched from the lock screen, the activity draws over the keyguard. Once
  // the flow is done there is nothing more this launch was for, and staying
  // open would leave the dashboard one dismiss away from an unlocked phone.
  const flowClosed = launchedForQuickLog && !quickLog.open;
  useEffect(() => {
    if (flowClosed) BackHandler.exitApp();
  }, [flowClosed]);

  const confirmDelete = useCallback(
    (expense: Expense) => {
      Alert.alert(
        'Delete expense?',
        expense.title + ' — ' + formatMinorUnits(expense.amountMinor, expense.currency),
        [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Delete', style: 'destructive', onPress: () => deleteExpense(expense.id) },
        ],
      );
    },
    [deleteExpense],
  );

  if (launchedForQuickLog) {
    // A blank surface, not the dashboard: the totals and the list of what the
    // owner buys are exactly what a locked phone should not be showing.
    return (
      <View style={styles.screen}>
        <QuickLogFlow />
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <FlatList
        data={expenses.slice(0, LATEST_LIMIT)}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <TransactionRow expense={item} onLongPress={confirmDelete} />}
        contentContainerStyle={[
          styles.content,
          { paddingTop: insets.top + space.xl, paddingBottom: insets.bottom + 110 },
        ]}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <View>
            <Text style={styles.eyebrow}>Spent this week</Text>
            <Text style={styles.total} numberOfLines={1} adjustsFontSizeToFit testID="week-total">
              {formatMinorUnits(weekTotalMinor, DEFAULT_CURRENCY)}
            </Text>

            <WeekBarChart buckets={weekBuckets} currency={DEFAULT_CURRENCY} />

            <Text style={styles.sectionTitle}>Latest</Text>
          </View>
        }
        ListEmptyComponent={hydrated ? <EmptyState /> : null}
      />

      <FloatingActionButton onPress={openQuickLog} bottomInset={insets.bottom} />
      <QuickLogFlow />
    </View>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: c.surface },
    content: { paddingHorizontal: space.xl },
    eyebrow: { ...type.label, color: c.inkMuted },
    total: { ...type.hero, color: c.ink, marginTop: space.sm },
    sectionTitle: { ...type.title, color: c.ink, marginTop: space.xxl, marginBottom: space.xs },
  });
