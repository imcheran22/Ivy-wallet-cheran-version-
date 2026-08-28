import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { categoryById } from '../../data/catalog';
import { useTheme } from '../../theme/ThemeProvider';
import { radius } from '../../theme/tokens';

type Props = { categoryId: string; size?: number };

export function CategoryIcon({ categoryId, size = 40 }: Props) {
  const { palette } = useTheme();
  const icon = categoryById(categoryId)?.icon ?? 'pricetag-outline';
  return (
    <View
      style={[
        styles.circle,
        { width: size, height: size, borderRadius: radius.pill, backgroundColor: palette.surfaceSunken },
      ]}
    >
      <Ionicons name={icon as never} size={size * 0.5} color={palette.ink} />
    </View>
  );
}

const styles = StyleSheet.create({
  circle: { alignItems: 'center', justifyContent: 'center' },
});
