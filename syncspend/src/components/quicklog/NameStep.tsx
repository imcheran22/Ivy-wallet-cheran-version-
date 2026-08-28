import React, { useEffect, useRef } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { Button } from '../common/Button';
import { color, radius, space, type } from '../../theme/tokens';

type Props = {
  value: string;
  onChange: (value: string) => void;
  onCancel: () => void;
  onDone: () => void;
  canAdvance: boolean;
};

export function NameStep({ value, onChange, onCancel, onDone, canAdvance }: Props) {
  const inputRef = useRef<TextInput>(null);

  // The point of the flow is that logging costs one gesture. Waiting for the
  // user to tap the field before the keyboard appears spends that gesture.
  useEffect(() => {
    const timer = setTimeout(() => inputRef.current?.focus(), 80);
    return () => clearTimeout(timer);
  }, []);

  return (
    <View>
      <TextInput
        ref={inputRef}
        testID="quicklog-name-input"
        value={value}
        onChangeText={onChange}
        onSubmitEditing={() => canAdvance && onDone()}
        placeholder="Coffee"
        placeholderTextColor={color.inkFaint}
        returnKeyType="done"
        autoCapitalize="sentences"
        autoCorrect={false}
        style={styles.input}
      />
      <View style={styles.row}>
        <Button label="Cancel" variant="neutral" onPress={onCancel} />
        <View style={styles.gap} />
        <Button label="Done" onPress={onDone} disabled={!canAdvance} testID="quicklog-name-done" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  input: {
    ...type.body,
    color: color.ink,
    backgroundColor: color.surfaceSunken,
    borderRadius: radius.md,
    paddingHorizontal: space.lg,
    height: 52,
  },
  row: { flexDirection: 'row', marginTop: space.lg },
  gap: { width: space.md },
});
