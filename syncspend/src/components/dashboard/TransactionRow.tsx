import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { CategoryIcon } from '../common/CategoryIcon';
import { color, space, type } from '../../theme/tokens';
import type { Expense } from '../../types';
import { relativeDayLabel } from '../../utils/dates';
import { formatMinorUnits } from '../../utils/money';

export function TransactionRow({ expense }: { expense: Expense }) {
  return (
    <View style={styles.row}>
      <CategoryIcon categoryId={expense.categoryId} />
      <View style={styles.middle}>
        <Text style={styles.title} numberOfLines={1}>
          {expense.title}
        </Text>
        <Text style={styles.date}>{relativeDayLabel(expense.occurredAt)}</Text>
      </View>
      <Text style={styles.amount}>{formatMinorUnits(expense.amountMinor, expense.currency)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: space.md },
  middle: { flex: 1, marginHorizontal: space.md },
  title: { ...type.body, color: color.ink },
  date: { ...type.caption, color: color.inkMuted, marginTop: 2 },
  amount: { ...type.body, color: color.ink },
});
