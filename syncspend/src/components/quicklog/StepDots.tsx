import React from 'react';
import { StyleSheet, View } from 'react-native';

import { useTheme } from '../../theme/ThemeProvider';
import { radius, space } from '../../theme/tokens';

type Props = { total: number; index: number };

/**
 * Four questions in a row with no sense of position feels open-ended — the
 * user cannot tell whether they are one tap from done or five. Two pixels of
 * dot buys that answer without adding a word to the card.
 */
export function StepDots({ total, index }: Props) {
  const { palette } = useTheme();
  return (
    <View style={styles.row} accessibilityLabel={'Step ' + (index + 1) + ' of ' + total}>
      {Array.from({ length: total }, (_, i) => (
        <View
          key={i}
          style={[
            styles.dot,
            { backgroundColor: i <= index ? palette.ink : palette.hairline },
            i === index && styles.dotCurrent,
          ]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'center', marginBottom: space.md },
  dot: { width: 5, height: 5, borderRadius: radius.pill, marginHorizontal: 3 },
  dotCurrent: { width: 16 },
});
