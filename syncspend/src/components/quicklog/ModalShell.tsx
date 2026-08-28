import React, { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, Text, View } from 'react-native';

import { color, duration, radius, space, type } from '../../theme/tokens';

type Props = {
  prompt: string;
  /** +1 advancing, -1 going back. Drives which side the card slides in from. */
  direction: 1 | -1;
  /** Changing this key replays the transition — one card per step. */
  transitionKey: string;
  children: React.ReactNode;
};

const SLIDE = 28;

/**
 * The card the whole flow lives in. Steps do not each mount their own modal:
 * one shell stays put and its contents cross-fade, which is what makes the
 * sequence read as a single question changing rather than four dialogs.
 */
export function ModalShell({ prompt, direction, transitionKey, children }: Props) {
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    progress.setValue(0);
    Animated.timing(progress, {
      toValue: 1,
      duration: duration.step,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [transitionKey, progress]);

  const translateX = progress.interpolate({
    inputRange: [0, 1],
    outputRange: [SLIDE * direction, 0],
  });

  return (
    <Animated.View style={[styles.card, { opacity: progress, transform: [{ translateX }] }]}>
      <Text style={styles.prompt}>{prompt}</Text>
      <View style={styles.body}>{children}</View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.lg,
    paddingHorizontal: space.xl,
    paddingTop: space.xl,
    paddingBottom: space.lg,
    shadowColor: '#000',
    shadowOpacity: 0.14,
    shadowRadius: 28,
    shadowOffset: { width: 0, height: 12 },
    elevation: 12,
  },
  prompt: {
    ...type.title,
    color: color.ink,
    textAlign: 'center',
  },
  body: { marginTop: space.lg },
});
