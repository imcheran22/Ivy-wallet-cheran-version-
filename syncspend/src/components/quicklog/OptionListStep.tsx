import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { Button } from '../common/Button';
import { CategoryIcon } from '../common/CategoryIcon';
import { useStyles, useTheme } from '../../theme/ThemeProvider';
import type { Palette } from '../../theme/tokens';
import { space, type } from '../../theme/tokens';

export type Option = { id: string; label: string };

type Props = {
  options: ReadonlyArray<Option>;
  selectedId: string | null;
  onPick: (id: string) => void;
  onBack: () => void;
  backLabel?: string;
  /** Category rows carry their icon; account and payment rows do not. */
  withCategoryIcons?: boolean;
  testIDPrefix: string;
};

/**
 * One list, three questions. Category, account and payment differ only in what
 * they are a list *of* — giving each its own component would be three copies of
 * the same scroll view drifting apart on padding.
 */
export function OptionListStep({
  options,
  selectedId,
  onPick,
  onBack,
  backLabel = 'Back',
  withCategoryIcons = false,
  testIDPrefix,
}: Props) {
  const styles = useStyles(makeStyles);
  const { palette } = useTheme();

  return (
    <View>
      <ScrollView style={styles.list} showsVerticalScrollIndicator={false} bounces={false}>
        {options.map((option) => {
          const selected = selectedId === option.id;
          return (
            <Pressable
              key={option.id}
              testID={testIDPrefix + option.id}
              accessibilityRole="button"
              accessibilityState={{ selected }}
              onPress={() => onPick(option.id)}
              style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
            >
              {withCategoryIcons && <CategoryIcon categoryId={option.id} size={36} />}
              <Text style={[styles.label, withCategoryIcons && styles.labelIndented]}>{option.label}</Text>
              {selected && <Ionicons name="checkmark" size={20} color={palette.accent} />}
            </Pressable>
          );
        })}
      </ScrollView>

      <View style={styles.footer}>
        <Button label={backLabel} variant="neutral" onPress={onBack} />
      </View>
    </View>
  );
}

const makeStyles = (c: Palette) =>
  StyleSheet.create({
    // Capped rather than free-growing: the card must not push its own buttons
    // off-screen once the list gets longer than the screen.
    list: { maxHeight: 300 },
    row: { flexDirection: 'row', alignItems: 'center', paddingVertical: space.md },
    rowPressed: { opacity: 0.55 },
    label: { ...type.body, color: c.ink, flex: 1 },
    labelIndented: { marginLeft: space.md },
    footer: { flexDirection: 'row', marginTop: space.md },
  });
