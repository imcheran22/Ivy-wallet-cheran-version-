import { useEffect, useRef } from 'react';
import * as Linking from 'expo-linking';

import { isQuickLogUrl } from '../utils/deepLink';

/**
 * Opens Quick Log when the app is launched by the Quick Settings tile or the
 * launcher shortcut.
 *
 * Both the cold start and the warm one matter: MainActivity is singleTask, so a
 * second tap on the tile delivers a new intent to the activity already running
 * rather than starting it again, and only the listener sees that.
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
