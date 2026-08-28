import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space, type } from '../../theme/tokens';

export function EmptyState() {
  const styles = useStyles(makeStyles);
  const { palette } = useTheme();
  return (
    <View style={styles.wrap}>
      <View style={styles.badge}>
        <Ionicons name="receipt-outline" size={22} color={palette.inkMuted} />
      </View>
      <Text style={styles.title}>No expenses yet</Text>
      <Text style={styles.body}>Tap + and answer four quick questions. It takes about five seconds.</Text>
    </View>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    wrap: { alignItems: 'center', paddingTop: space.xl, paddingHorizontal: space.lg },
    badge: {
      width: 52,
      height: 52,
      borderRadius: radius.pill,
      backgroundColor: c.surfaceSunken,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: space.md,
    },
    title: { ...type.body, color: c.ink },
    body: { ...type.label, color: c.inkMuted, textAlign: 'center', marginTop: space.xs, lineHeight: 20 },
  });
