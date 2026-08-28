import React from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';

import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space } from '../../theme/tokens';

type Props = { onPress: () => void; bottomInset?: number };

export function FloatingActionButton({ onPress, bottomInset = 0 }: Props) {
  const styles = useStyles(makeStyles);
  const { palette } = useTheme();
  return (
    <Pressable
      testID="quicklog-fab"
      accessibilityRole="button"
      accessibilityLabel="Log an expense"
      hitSlop={8}
      onPress={() => {
        void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        onPress();
      }}
      style={({ pressed }) => [styles.fab, { bottom: space.xl + bottomInset }, pressed && styles.pressed]}
    >
      <Ionicons name="add" size={30} color={palette.surface} />
    </Pressable>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    fab: {
      position: 'absolute',
      right: space.lg,
      width: 58,
      height: 58,
      borderRadius: radius.pill,
      backgroundColor: c.ink,
      alignItems: 'center',
      justifyContent: 'center',
      shadowColor: '#000',
      shadowOpacity: 0.24,
      shadowRadius: 14,
      shadowOffset: { width: 0, height: 6 },
      elevation: 8,
    },
    pressed: { opacity: 0.85, transform: [{ scale: 0.95 }] },
  });
