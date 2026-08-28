import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { useStyles } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space, type } from '../../theme/tokens';
import { DAY_LABELS } from '../../utils/dates';
import { formatMinorUnits } from '../../utils/money';

type Props = {
  /** Sun..Sat totals in minor units. */
  buckets: number[];
  currency: string;
  todayIndex?: number;
};

const CHART_HEIGHT = 92;
const MIN_BAR = 3;

export function WeekBarChart({ buckets, currency, todayIndex = new Date().getDay() }: Props) {
  const styles = useStyles(makeStyles);
  // Scaled to the week's own peak, not a fixed ceiling: the shape of a $40 week
  // and a $4,000 week should be equally readable.
  const peak = Math.max(...buckets, 1);
  const busiest = buckets.indexOf(peak);

  return (
    <View style={styles.wrap}>
      <View style={styles.bars}>
        {buckets.map((amount, index) => {
          const height = amount === 0 ? MIN_BAR : Math.max(MIN_BAR, (amount / peak) * CHART_HEIGHT);
          return (
            <View
              key={DAY_LABELS[index]}
              style={styles.column}
              accessibilityLabel={
                DAY_LABELS[index] + ': ' + formatMinorUnits(amount, currency)
              }
            >
              {/* Only the peak is labelled. Seven numbers would be a table
                  pretending to be a chart; one gives the axis a scale. */}
              <Text style={[styles.peakLabel, index !== busiest || amount === 0 ? styles.hidden : null]}>
                {formatMinorUnits(amount, currency)}
              </Text>
              <View style={styles.track}>
                <View
                  testID={'chart-bar-' + index}
                  style={[styles.bar, { height }, amount === 0 && styles.barEmpty]}
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

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    wrap: { marginTop: space.xl },
    bars: { flexDirection: 'row', alignItems: 'flex-end' },
    column: { flex: 1, alignItems: 'center' },
    peakLabel: { ...type.caption, color: c.inkMuted, marginBottom: space.xs },
    hidden: { opacity: 0 },
    track: { height: CHART_HEIGHT, justifyContent: 'flex-end' },
    bar: { width: 20, borderRadius: radius.sm, backgroundColor: c.bar },
    barEmpty: { backgroundColor: c.barIdle },
    dayLabel: { ...type.caption, color: c.inkMuted, marginTop: space.sm },
    dayLabelToday: { color: c.ink, fontWeight: '700' },
  });
