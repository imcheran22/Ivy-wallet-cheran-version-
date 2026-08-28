import React, { createContext, useContext, useMemo } from 'react';
import { useColorScheme } from 'react-native';

import { palettes } from './tokens';
import type { Palette } from './tokens';

type ThemeValue = { palette: Palette; scheme: 'light' | 'dark' };

const ThemeContext = createContext<ThemeValue>({ palette: palettes.light, scheme: 'light' });

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const value = useMemo<ThemeValue>(() => ({ palette: palettes[scheme], scheme }), [scheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeValue {
  return useContext(ThemeContext);
}

/**
 * Styles still go through StyleSheet.create — they are just built once per
 * palette instead of once per module. `factory` must be defined at module
 * level so its identity is stable and the memo actually holds.
 */
export function useStyles<T>(factory: (c: Palette) => T): T {
  const { palette } = useTheme();
  return useMemo(() => factory(palette), [factory, palette]);
}
