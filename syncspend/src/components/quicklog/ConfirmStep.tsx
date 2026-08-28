import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button } from '../common/Button';
import { ACCOUNTS, PAYMENT_METHODS, labelOf, categoryById } from '../../data/catalog';
import { color, space, type } from '../../theme/tokens';
import type { Draft } from '../../state/quickLogMachine';
import { fullDateLabel } from '../../utils/dates';
import { formatMinorUnits, toMinorUnits } from '../../utils/money';

type Props = {
  draft: Draft;
  onBack: () => void;
  onContinue: () => void;
};

export function ConfirmStep({ draft, onBack, onContinue }: Props) {
  const rows: Array<[string, string]> = [
    ['Title', draft.title.trim()],
    ['Account', labelOf(ACCOUNTS, draft.accountId)],
    ['Category', draft.categoryId ? categoryById(draft.categoryId)?.label ?? draft.categoryId : '—'],
    ['Payment', labelOf(PAYMENT_METHODS, draft.paymentMethodId)],
    ['Date', fullDateLabel(draft.occurredAt)],
  ];

  return (
    <View>
      <Text testID="quicklog-confirm-amount" style={styles.amount} numberOfLines={1} adjustsFontSizeToFit>
        {formatMinorUnits(toMinorUnits(draft.amountInput), draft.currency)}
      </Text>

      <View style={styles.table}>
        {rows.map(([label, value], index) => (
          <View key={label} style={[styles.row, index > 0 && styles.rowDivided]}>
            <Text style={styles.rowLabel}>{label}</Text>
            <Text style={styles.rowValue} numberOfLines={1}>
              {value}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.footer}>
        <Button label="Back" variant="neutral" onPress={onBack} />
        <View style={styles.gap} />
        <Button label="Continue" onPress={onContinue} testID="quicklog-confirm-continue" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  amount: { ...type.amount, color: color.ink, textAlign: 'center' },
  table: { marginTop: space.xl },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: space.md,
  },
  rowDivided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.hairline },
  rowLabel: { ...type.label, color: color.inkMuted },
  rowValue: { ...type.label, color: color.ink, flexShrink: 1, marginLeft: space.lg, textAlign: 'right' },
  footer: { flexDirection: 'row', marginTop: space.lg },
  gap: { width: space.md },
});
