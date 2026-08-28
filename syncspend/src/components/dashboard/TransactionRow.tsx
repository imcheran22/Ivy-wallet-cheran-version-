import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import * as Haptics from 'expo-haptics';

import { CategoryIcon } from '../common/CategoryIcon';
import { useStyles } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { space, type } from '../../theme/tokens';
import type { Expense } from '../../types';
import { relativeDayLabel } from '../../utils/dates';
import { formatMinorUnits } from '../../utils/money';

type Props = { expense: Expense; onLongPress: (expense: Expense) => void };

export function TransactionRow({ expense, onLongPress }: Props) {
  const styles = useStyles(makeStyles);
  return (
    <Pressable
      testID={'transaction-' + expense.id}
      accessibilityRole="button"
      accessibilityHint="Long press to delete"
      onLongPress={() => {
        void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
        onLongPress(expense);
      }}
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
    >
      <CategoryIcon categoryId={expense.categoryId} />
      <View style={styles.middle}>
        <Text style={styles.title} numberOfLines={1}>
          {expense.title}
        </Text>
        <Text style={styles.date}>{relativeDayLabel(expense.occurredAt)}</Text>
      </View>
      <Text style={styles.amount}>{formatMinorUnits(expense.amountMinor, expense.currency)}</Text>
    </Pressable>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    row: { flexDirection: 'row', alignItems: 'center', paddingVertical: space.md },
    rowPressed: { opacity: 0.55 },
    middle: { flex: 1, marginHorizontal: space.md },
    title: { ...type.body, color: c.ink },
    date: { ...type.caption, color: c.inkMuted, marginTop: 2 },
    amount: { ...type.body, color: c.ink },
  });
