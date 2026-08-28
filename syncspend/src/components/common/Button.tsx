import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';

import { color, radius, space, type } from '../../theme/tokens';

type Props = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'neutral';
  disabled?: boolean;
  testID?: string;
};

export function Button({ label, onPress, variant = 'primary', disabled = false, testID }: Props) {
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

const styles = StyleSheet.create({
  base: {
    flex: 1,
    height: 50,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.lg,
  },
  primary: { backgroundColor: color.accent },
  neutral: { backgroundColor: color.neutralButton },
  disabled: { opacity: 0.4 },
  pressed: { opacity: 0.85 },
  label: type.body,
  primaryLabel: { color: color.accentInk },
  neutralLabel: { color: color.ink },
});
