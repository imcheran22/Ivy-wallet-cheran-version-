import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import * as Haptics from 'expo-haptics';
import { Ionicons } from '@expo/vector-icons';

import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space } from '../../theme/tokens';

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'backspace'] as const;

type Props = { onKey: (value: string) => void };

/**
 * A purpose-built pad instead of the numeric system keyboard: it can be laid
 * out to reach with a thumb, it cannot offer a comma or a minus sign the
 * parser would have to reject, and it never resizes the card mid-flow.
 */
export function Numpad({ onKey }: Props) {
  const styles = useStyles(makeStyles);
  const { palette } = useTheme();

  return (
    <View style={styles.grid}>
      {KEYS.map((key) => (
        <Pressable
          key={key}
          testID={'numpad-' + key}
          accessibilityRole="button"
          accessibilityLabel={key === 'backspace' ? 'Delete' : key}
          onPress={() => {
            void Haptics.selectionAsync();
            onKey(key);
          }}
          style={({ pressed }) => [styles.key, pressed && styles.keyPressed]}
        >
          {key === 'backspace' ? (
            <Ionicons name="backspace-outline" size={24} color={palette.ink} />
          ) : (
            <Text style={styles.keyLabel}>{key}</Text>
          )}
        </Pressable>
      ))}
    </View>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
    key: {
      width: '31.5%',
      height: 56,
      marginBottom: space.sm,
      borderRadius: radius.md,
      backgroundColor: c.surfaceSunken,
      alignItems: 'center',
      justifyContent: 'center',
    },
    keyPressed: { backgroundColor: c.hairline },
    keyLabel: { fontSize: 24, fontWeight: '500', color: c.ink },
  });
