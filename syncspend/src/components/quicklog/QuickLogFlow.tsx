import React, { useEffect, useRef } from 'react';
import { Animated, Easing, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, View } from 'react-native';
import { BlurView } from 'expo-blur';

import { useExpenses } from '../../state/ExpenseStore';
import { canAdvance } from '../../state/quickLogMachine';
import { color, duration, space } from '../../theme/tokens';

import { AmountStep } from './AmountStep';
import { CategoryStep } from './CategoryStep';
import { ConfirmStep } from './ConfirmStep';
import { ModalShell } from './ModalShell';
import { NameStep } from './NameStep';

const PROMPTS = {
  name: 'What is the expense about?',
  amount: 'What is the amount?',
  category: 'Which category?',
  confirm: 'Confirm expense details',
} as const;

/**
 * Owns the backdrop and the step routing; every step below it is a dumb view
 * over the draft. Keeping the decisions in one place is what lets the flow be
 * re-entered from a FAB, a shortcut or a widget without any of them knowing
 * how many steps there are.
 */
export function QuickLogFlow() {
  const { quickLog, dispatchQuickLog, cancelQuickLog, confirmQuickLog } = useExpenses();
  const fade = useRef(new Animated.Value(0)).current;
  const open = quickLog.open;

  useEffect(() => {
    Animated.timing(fade, {
      toValue: open ? 1 : 0,
      duration: duration.backdrop,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    }).start();
  }, [open, fade]);

  if (!quickLog.open) return null;

  const { step, draft, direction } = quickLog;
  const advanceable = canAdvance(step, draft);

  return (
    <Modal transparent animationType="none" visible onRequestClose={cancelQuickLog} statusBarTranslucent>
      <Animated.View style={[StyleSheet.absoluteFill, { opacity: fade }]}>
        <BlurView intensity={28} tint="light" style={StyleSheet.absoluteFill}>
          {/* Tapping the blurred background is the same as Cancel — an escape
              that does not require finding the button. */}
          <Pressable
            testID="quicklog-scrim"
            accessibilityLabel="Dismiss"
            style={[StyleSheet.absoluteFill, styles.scrim]}
            onPress={cancelQuickLog}
          />
        </BlurView>
      </Animated.View>

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.centered}
        pointerEvents="box-none"
      >
        <View style={styles.cardWrap} pointerEvents="box-none">
          <ModalShell prompt={PROMPTS[step]} direction={direction} transitionKey={step}>
            {step === 'name' && (
              <NameStep
                value={draft.title}
                onChange={(title) => dispatchQuickLog({ type: 'setTitle', title })}
                onCancel={cancelQuickLog}
                onDone={() => dispatchQuickLog({ type: 'next' })}
                canAdvance={advanceable}
              />
            )}

            {step === 'amount' && (
              <AmountStep
                amountInput={draft.amountInput}
                currency={draft.currency}
                onChange={(amountInput) => dispatchQuickLog({ type: 'setAmountInput', amountInput })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
                onDone={() => dispatchQuickLog({ type: 'next' })}
                canAdvance={advanceable}
              />
            )}

            {step === 'category' && (
              <CategoryStep
                selectedId={draft.categoryId}
                onPick={(categoryId) => dispatchQuickLog({ type: 'pickCategory', categoryId })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
              />
            )}

            {step === 'confirm' && (
              <ConfirmStep
                draft={draft}
                onBack={() => dispatchQuickLog({ type: 'back' })}
                onContinue={confirmQuickLog}
              />
            )}
          </ModalShell>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: { backgroundColor: color.scrim },
  centered: { flex: 1, justifyContent: 'flex-end' },
  cardWrap: { padding: space.lg, paddingBottom: space.xxl },
});
