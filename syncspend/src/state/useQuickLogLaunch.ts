import { useEffect, useRef } from 'react';
import * as Linking from 'expo-linking';

/** The tile, the launcher shortcut, and any future entry point all fire this. */
export const QUICK_LOG_HOST = 'quick-log';

function isQuickLogUrl(url: string | null): boolean {
  if (!url) return false;
  const { hostname, path } = Linking.parse(url);
  // `syncspend://quick-log` parses with the segment as the host on Android and
  // as the path on some platforms, so accept it either way.
  return hostname === QUICK_LOG_HOST || path?.replace(/^\//, '') === QUICK_LOG_HOST;
}

/**
 * Opens Quick Log when the app is launched by the Quick Settings tile or the
 * launcher shortcut.
 *
 * Both the cold start and the warm one matter: MainActivity is singleTask, so a
 * second tap on the tile delivers a new intent to the running activity rather
 * than starting it again, and only the listener sees that.
 */
export function useQuickLogLaunch(onLaunch: () => void): void {
  const handler = useRef(onLaunch);
  handler.current = onLaunch;

  useEffect(() => {
    let cancelled = false;

    void Linking.getInitialURL().then((url) => {
      if (!cancelled && isQuickLogUrl(url)) handler.current();
    });

    const subscription = Linking.addEventListener('url', ({ url }) => {
      if (isQuickLogUrl(url)) handler.current();
    });

    return () => {
      cancelled = true;
      subscription.remove();
    };
  }, []);
}
