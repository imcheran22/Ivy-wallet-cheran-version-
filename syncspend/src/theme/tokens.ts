/**
 * The whole app is black on white. There is no accent colour except the one
 * blue used for "the button that commits" — which is precisely what makes it
 * readable at a glance: if something is blue, pressing it moves you forward.
 */
export const color = {
  ink: '#0A0A0A',
  inkMuted: '#8A8A8E',
  inkFaint: '#C4C4C8',
  surface: '#FFFFFF',
  surfaceSunken: '#F5F5F7',
  hairline: '#EAEAEC',
  accent: '#1F6FEB',
  accentInk: '#FFFFFF',
  neutralButton: '#EFEFF1',
  scrim: 'rgba(10,10,10,0.28)',
  bar: '#0A0A0A',
  barIdle: '#EAEAEC',
} as const;

export const space = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radius = {
  sm: 10,
  md: 14,
  lg: 20,
  pill: 999,
} as const;

export const type = {
  /** The dashboard's one piece of hero typography. */
  hero: { fontSize: 44, fontWeight: '700', letterSpacing: -1.4 },
  amount: { fontSize: 56, fontWeight: '600', letterSpacing: -1.8 },
  title: { fontSize: 19, fontWeight: '600', letterSpacing: -0.3 },
  body: { fontSize: 16, fontWeight: '500' },
  label: { fontSize: 14, fontWeight: '500' },
  caption: { fontSize: 12, fontWeight: '500' },
} as const;

export const duration = {
  step: 220,
  backdrop: 180,
} as const;
