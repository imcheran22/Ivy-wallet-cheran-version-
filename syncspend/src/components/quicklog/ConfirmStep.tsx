import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Button } from '../common/Button';
import { ACCOUNTS, PAYMENT_METHODS, categoryById, labelOf } from '../../data/catalog';
import type { Draft, Step } from '../../state/quickLogMachine';
import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { space, type } from '../../theme/tokens';
import { fullDateLabel } from '../../utils/dates';
import { formatMinorUnits, toMinorUnits } from '../../utils/money';

type Props = {
  draft: Draft;
  onEdit: (step: Step) => void;
  onBack: () => void;
  onContinue: () => void;
};

export function ConfirmStep({ draft, onEdit, onBack, onContinue }: Props) {
  const styles = useStyles(makeStyles);
  const { palette } = useTheme();

  // Every row except the date is a question that was asked earlier, so every
  // row except the date can be re-answered by tapping it. Sending the user
  // back through Back four times to fix a typo is the thing this flow exists
  // to avoid.
  const rows: Array<{ label: string; value: string; edit?: Step }> = [
    { label: 'Title', value: draft.title.trim(), edit: 'name' },
    { label: 'Account', value: labelOf(ACCOUNTS, draft.accountId), edit: 'account' },
    {
      label: 'Category',
      value: draft.categoryId ? categoryById(draft.categoryId)?.label ?? draft.categoryId : '—',
      edit: 'category',
    },
    { label: 'Payment', value: labelOf(PAYMENT_METHODS, draft.paymentMethodId), edit: 'payment' },
    { label: 'Date', value: fullDateLabel(draft.occurredAt) },
  ];

  return (
    <View>
      <Pressable onPress={() => onEdit('amount')} accessibilityRole="button" accessibilityLabel="Edit amount">
        <Text testID="quicklog-confirm-amount" style={styles.amount} numberOfLines={1} adjustsFontSizeToFit>
          {formatMinorUnits(toMinorUnits(draft.amountInput), draft.currency)}
        </Text>
      </Pressable>

      <View style={styles.table}>
        {rows.map((row, index) => (
          <Pressable
            key={row.label}
            disabled={!row.edit}
            onPress={() => row.edit && onEdit(row.edit)}
            accessibilityRole={row.edit ? 'button' : 'text'}
            style={({ pressed }) => [styles.row, index > 0 && styles.rowDivided, pressed && styles.rowPressed]}
          >
            <Text style={styles.rowLabel}>{row.label}</Text>
            <Text style={styles.rowValue} numberOfLines={1}>
              {row.value}
            </Text>
            {row.edit ? (
              <Ionicons name="chevron-forward" size={15} color={palette.inkFaint} style={styles.chevron} />
            ) : (
              <View style={styles.chevronSpacer} />
            )}
          </Pressable>
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

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    amount: { ...type.amount, color: c.ink, textAlign: 'center' },
    table: { marginTop: space.lg },
    row: { flexDirection: 'row', alignItems: 'center', paddingVertical: space.md },
    rowDivided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: c.hairline },
    rowPressed: { opacity: 0.5 },
    rowLabel: { ...type.label, color: c.inkMuted },
    rowValue: { ...type.label, color: c.ink, flex: 1, marginLeft: space.lg, textAlign: 'right' },
    chevron: { marginLeft: space.xs },
    chevronSpacer: { width: 19 },
    footer: { flexDirection: 'row', marginTop: space.lg },
    gap: { width: space.md },
  });
