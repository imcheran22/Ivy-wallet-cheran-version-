import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';

import { useStyles } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space, type } from '../../theme/tokens';

type Props = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'neutral';
  disabled?: boolean;
  testID?: string;
};

export function Button({ label, onPress, variant = 'primary', disabled = false, testID }: Props) {
  const styles = useStyles(makeStyles);
  const primary = variant === 'primary';
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        primary ? styles.primary : styles.neutral,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
      ]}
    >
      <Text style={[styles.label, primary ? styles.primaryLabel : styles.neutralLabel]}>{label}</Text>
    </Pressable>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    base: {
      flex: 1,
      height: 50,
      borderRadius: radius.md,
      alignItems: 'center',
      justifyContent: 'center',
      paddingHorizontal: space.lg,
    },
    primary: { backgroundColor: c.accent },
    neutral: { backgroundColor: c.neutralButton },
    disabled: { opacity: 0.4 },
    pressed: { opacity: 0.85 },
    label: type.body,
    primaryLabel: { color: c.accentInk },
    neutralLabel: { color: c.ink },
  });
