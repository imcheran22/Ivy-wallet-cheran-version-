import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { color, radius, space, type } from '../../theme/tokens';
import { DAY_LABELS } from '../../utils/dates';

type Props = {
  /** Sun..Sat totals in minor units. */
  buckets: number[];
  todayIndex?: number;
};

const CHART_HEIGHT = 96;
const MIN_BAR = 3;

export function WeekBarChart({ buckets, todayIndex = new Date().getDay() }: Props) {
  // Scaled to the week's own peak, not to a fixed ceiling: the shape of a $40
  // week and a $4,000 week should be equally readable.
  const peak = Math.max(...buckets, 1);

  return (
    <View style={styles.wrap} accessibilityRole="image" accessibilityLabel="Spending by day this week">
      <View style={styles.bars}>
        {buckets.map((amount, index) => {
          const height = amount === 0 ? MIN_BAR : Math.max(MIN_BAR, (amount / peak) * CHART_HEIGHT);
          return (
            <View key={DAY_LABELS[index]} style={styles.column}>
              <View style={styles.track}>
                <View
                  testID={'chart-bar-' + index}
                  style={[
                    styles.bar,
                    { height },
                    amount === 0 && styles.barEmpty,
                  ]}
                />
              </View>
              <Text style={[styles.dayLabel, index === todayIndex && styles.dayLabelToday]}>
                {DAY_LABELS[index]}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { marginTop: space.xl },
  bars: { flexDirection: 'row', alignItems: 'flex-end' },
  column: { flex: 1, alignItems: 'center' },
  track: { height: CHART_HEIGHT, justifyContent: 'flex-end' },
  bar: {
    width: 20,
    borderRadius: radius.sm,
    backgroundColor: color.bar,
  },
  barEmpty: { backgroundColor: color.barIdle },
  dayLabel: { ...type.caption, color: color.inkMuted, marginTop: space.sm },
  dayLabelToday: { color: color.ink, fontWeight: '700' },
});
