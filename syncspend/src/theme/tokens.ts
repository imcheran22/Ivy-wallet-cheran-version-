/**
 * Black on white, with exactly one accent. That constraint is what makes the
 * flow readable at a glance: if something is accent-coloured, pressing it moves
 * you forward. Everything else is a shade of ink.
 */
export type Palette = {
  ink: string;
  inkMuted: string;
  inkFaint: string;
  surface: string;
  surfaceRaised: string;
  surfaceSunken: string;
  hairline: string;
  accent: string;
  accentInk: string;
  neutralButton: string;
  scrim: string;
  bar: string;
  barIdle: string;
  danger: string;
};

const light: Palette = {
  ink: '#0A0A0A',
  inkMuted: '#8A8A8E',
  inkFaint: '#C4C4C8',
  surface: '#FFFFFF',
  surfaceRaised: '#FFFFFF',
  surfaceSunken: '#F4F4F6',
  hairline: '#EAEAEC',
  accent: '#1F6FEB',
  accentInk: '#FFFFFF',
  neutralButton: '#EEEEF0',
  scrim: 'rgba(10,10,10,0.22)',
  bar: '#0A0A0A',
  barIdle: '#E4E4E7',
  danger: '#D93025',
};

/**
 * Not an inversion. Pure white on pure black glares at night, and a card that
 * is #000 on a #000 backdrop loses its edge — so ink softens and the raised
 * surfaces step *up* in lightness to keep the card reading as a card.
 */
const dark: Palette = {
  ink: '#F2F2F4',
  inkMuted: '#8E8E93',
  inkFaint: '#5A5A5F',
  surface: '#0B0B0C',
  surfaceRaised: '#1A1A1C',
  surfaceSunken: '#232326',
  hairline: '#2E2E32',
  accent: '#3B82F6',
  accentInk: '#FFFFFF',
  neutralButton: '#2A2A2E',
  scrim: 'rgba(0,0,0,0.45)',
  bar: '#F2F2F4',
  barIdle: '#2E2E32',
  danger: '#FF6B60',
};

export const palettes = { light, dark } as const;

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const radius = { sm: 10, md: 14, lg: 22, pill: 999 } as const;

export const type = {
  hero: { fontSize: 44, fontWeight: '700', letterSpacing: -1.4 },
  amount: { fontSize: 54, fontWeight: '600', letterSpacing: -1.8 },
  title: { fontSize: 19, fontWeight: '600', letterSpacing: -0.3 },
  body: { fontSize: 16, fontWeight: '500' },
  label: { fontSize: 14, fontWeight: '500' },
  caption: { fontSize: 12, fontWeight: '500' },
} as const;

export const duration = { step: 210, backdrop: 170 } as const;
