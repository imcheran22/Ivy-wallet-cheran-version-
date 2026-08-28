import React, { useEffect, useRef } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { Button } from '../common/Button';
import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { radius, space, type } from '../../theme/tokens';

type Props = {
  value: string;
  onChange: (value: string) => void;
  onCancel: () => void;
  onDone: () => void;
  canAdvance: boolean;
};

export function NameStep({ value, onChange, onCancel, onDone, canAdvance }: Props) {
  const styles = useStyles(makeStyles);
  const { palette, scheme } = useTheme();
  const inputRef = useRef<TextInput>(null);

  // The point of the flow is that logging costs one gesture. Making the user
  // tap the field before the keyboard appears spends that gesture.
  useEffect(() => {
    const timer = setTimeout(() => inputRef.current?.focus(), 90);
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
        placeholderTextColor={palette.inkFaint}
        keyboardAppearance={scheme}
        returnKeyType="done"
        autoCapitalize="sentences"
        autoCorrect={false}
        maxLength={60}
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

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    input: {
      ...type.body,
      color: c.ink,
      backgroundColor: c.surfaceSunken,
      borderRadius: radius.md,
      paddingHorizontal: space.lg,
      height: 52,
    },
    row: { flexDirection: 'row', marginTop: space.lg },
    gap: { width: space.md },
  });
