import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Button } from '../common/Button';
import { CategoryIcon } from '../common/CategoryIcon';
import { CATEGORIES } from '../../data/catalog';
import { color, space, type } from '../../theme/tokens';

type Props = {
  selectedId: string | null;
  onPick: (categoryId: string) => void;
  onBack: () => void;
};

export function CategoryStep({ selectedId, onPick, onBack }: Props) {
  return (
    <View>
      <ScrollView style={styles.list} showsVerticalScrollIndicator={false} bounces={false}>
        {CATEGORIES.map((category) => (
          <Pressable
            key={category.id}
            testID={'quicklog-category-' + category.id}
            accessibilityRole="button"
            accessibilityState={{ selected: selectedId === category.id }}
            onPress={() => onPick(category.id)}
            style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
          >
            <CategoryIcon categoryId={category.id} size={36} />
            <Text style={styles.label}>{category.label}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <View style={styles.footer}>
        <Button label="Back" variant="neutral" onPress={onBack} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  // Capped rather than free-growing: the card must not push its own buttons
  // off-screen once the category list gets longer than the screen.
  list: { maxHeight: 320 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: space.md,
  },
  rowPressed: { opacity: 0.55 },
  label: { ...type.body, color: color.ink, marginLeft: space.md },
  footer: { flexDirection: 'row', marginTop: space.md },
});
