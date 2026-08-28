import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import * as Haptics from 'expo-haptics';
import { Ionicons } from '@expo/vector-icons';

import { color, radius, space } from '../../theme/tokens';

type Key = { label: string; value: string } | { label: 'backspace'; value: 'backspace' };

const KEYS: Key[] = [
  { label: '1', value: '1' }, { label: '2', value: '2' }, { label: '3', value: '3' },
  { label: '4', value: '4' }, { label: '5', value: '5' }, { label: '6', value: '6' },
  { label: '7', value: '7' }, { label: '8', value: '8' }, { label: '9', value: '9' },
  { label: '.', value: '.' }, { label: '0', value: '0' }, { label: 'backspace', value: 'backspace' },
];

type Props = { onKey: (value: string) => void };

/**
 * A purpose-built pad instead of the numeric system keyboard: it can be laid
 * out to reach with a thumb, it cannot offer a comma or a minus sign the
 * parser would have to reject, and it never resizes the card mid-flow.
 */
export function Numpad({ onKey }: Props) {
  return (
    <View style={styles.grid}>
      {KEYS.map((key) => (
        <Pressable
          key={key.value}
          testID={'numpad-' + key.value}
          accessibilityRole="button"
          accessibilityLabel={key.value === 'backspace' ? 'Delete' : key.label}
          onPress={() => {
            void Haptics.selectionAsync();
            onKey(key.value);
          }}
          style={({ pressed }) => [styles.key, pressed && styles.keyPressed]}
        >
          {key.value === 'backspace' ? (
            <Ionicons name="backspace-outline" size={24} color={color.ink} />
          ) : (
            <Text style={styles.keyLabel}>{key.label}</Text>
          )}
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  key: {
    width: '31%',
    height: 58,
    marginBottom: space.sm,
    borderRadius: radius.md,
    backgroundColor: color.surfaceSunken,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyPressed: { backgroundColor: color.hairline },
  keyLabel: { fontSize: 24, fontWeight: '500', color: color.ink },
});
