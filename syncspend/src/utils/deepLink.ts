/** The tile, the launcher shortcut, and any future entry point all fire this. */
export const QUICK_LOG_HOST = 'quick-log';

/**
 * Matches on the last path segment rather than the host, because the same link
 * arrives shaped differently depending on how the app was started:
 * `syncspend://quick-log` from the tile, `exp://<host>/--/quick-log` under
 * Expo Go in development.
 */
export function isQuickLogUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  const withoutScheme = url.replace(/^[a-z][a-z0-9+.-]*:/i, '');
  const pathOnly = withoutScheme.split(/[?#]/)[0] ?? '';
  const segments = pathOnly.split('/').filter(Boolean);
  return segments[segments.length - 1] === QUICK_LOG_HOST;
}
