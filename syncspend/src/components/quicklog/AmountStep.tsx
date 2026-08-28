import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Button } from '../common/Button';
import { color, space, type } from '../../theme/tokens';
import { formatAmountInput, pressBackspace, pressDecimalPoint, pressDigit } from '../../utils/money';

import { Numpad } from './Numpad';

type Props = {
  amountInput: string;
  currency: string;
  onChange: (next: string) => void;
  onBack: () => void;
  onDone: () => void;
  canAdvance: boolean;
};

export function AmountStep({ amountInput, currency, onChange, onBack, onDone, canAdvance }: Props) {
  const handleKey = (key: string) => {
    if (key === 'backspace') return onChange(pressBackspace(amountInput));
    if (key === '.') return onChange(pressDecimalPoint(amountInput));
    return onChange(pressDigit(amountInput, key));
  };

  return (
    <View>
      <Text
        testID="quicklog-amount-display"
        style={[styles.display, amountInput === '' && styles.displayEmpty]}
        numberOfLines={1}
        adjustsFontSizeToFit
      >
        {formatAmountInput(amountInput, currency)}
      </Text>

      <Numpad onKey={handleKey} />

      <View style={styles.row}>
        <Button label="Back" variant="neutral" onPress={onBack} />
        <View style={styles.gap} />
        <Button label="Done" onPress={onDone} disabled={!canAdvance} testID="quicklog-amount-done" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  display: {
    ...type.amount,
    color: color.ink,
    textAlign: 'center',
    marginBottom: space.xl,
  },
  displayEmpty: { color: color.inkFaint },
  row: { flexDirection: 'row', marginTop: space.sm },
  gap: { width: space.md },
});
