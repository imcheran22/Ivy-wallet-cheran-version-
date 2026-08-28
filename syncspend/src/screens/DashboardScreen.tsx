import React from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FloatingActionButton } from '../components/common/FloatingActionButton';
import { TransactionRow } from '../components/dashboard/TransactionRow';
import { WeekBarChart } from '../components/dashboard/WeekBarChart';
import { QuickLogFlow } from '../components/quicklog/QuickLogFlow';
import { DEFAULT_CURRENCY } from '../data/catalog';
import { useExpenses } from '../state/ExpenseStore';
import { color, space, type } from '../theme/tokens';
import { formatMinorUnits } from '../utils/money';

const LATEST_LIMIT = 20;

export function DashboardScreen() {
  const insets = useSafeAreaInsets();
  const { expenses, hydrated, weekTotalMinor, weekBuckets, openQuickLog } = useExpenses();
  const latest = expenses.slice(0, LATEST_LIMIT);

  return (
    <View style={styles.screen}>
      <FlatList
        data={latest}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <TransactionRow expense={item} />}
        contentContainerStyle={[
          styles.content,
          { paddingTop: insets.top + space.xl, paddingBottom: insets.bottom + 96 },
        ]}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <View>
            <Text style={styles.eyebrow}>Spent this week</Text>
            <Text style={styles.total} numberOfLines={1} adjustsFontSizeToFit testID="week-total">
              {formatMinorUnits(weekTotalMinor, DEFAULT_CURRENCY)}
            </Text>

            <WeekBarChart buckets={weekBuckets} />

            <Text style={styles.sectionTitle}>Latest</Text>
          </View>
        }
        ListEmptyComponent={
          hydrated ? (
            <Text style={styles.empty}>Nothing logged yet. Tap + to add your first expense.</Text>
          ) : null
        }
      />

      <FloatingActionButton onPress={openQuickLog} />
      <QuickLogFlow />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: color.surface },
  content: { paddingHorizontal: space.xl },
  eyebrow: { ...type.label, color: color.inkMuted },
  total: { ...type.hero, color: color.ink, marginTop: space.sm },
  sectionTitle: { ...type.title, color: color.ink, marginTop: space.xxl, marginBottom: space.xs },
  empty: { ...type.label, color: color.inkMuted, marginTop: space.lg },
});
