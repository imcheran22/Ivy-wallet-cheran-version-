import React, { useEffect, useRef } from 'react';
import {
  Animated,
  Easing,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  View,
} from 'react-native';
import { BlurView } from 'expo-blur';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ACCOUNTS, CATEGORIES, PAYMENT_METHODS } from '../../data/catalog';
import { useExpenses } from '../../state/ExpenseStore';
import { FLOW, canAdvance, flowIndex, isDetour } from '../../state/quickLogMachine';
import { useTheme } from '../../theme/ThemeProvider';
import { duration, space } from '../../theme/tokens';

import { AmountStep } from './AmountStep';
import { ConfirmStep } from './ConfirmStep';
import { ModalShell } from './ModalShell';
import { NameStep } from './NameStep';
import { OptionListStep } from './OptionListStep';
import { StepDots } from './StepDots';

const PROMPTS = {
  name: 'What is the expense about?',
  amount: 'What is the amount?',
  category: 'Which category?',
  confirm: 'Confirm expense details',
  account: 'Which account?',
  payment: 'How did you pay?',
} as const;

/**
 * Owns the backdrop and the step routing; every step below it is a dumb view
 * over the draft. Keeping the decisions here is what lets the flow be entered
 * from a FAB, a shortcut or a widget without any of them knowing how many
 * steps there are.
 */
export function QuickLogFlow() {
  const { quickLog, dispatchQuickLog, cancelQuickLog, confirmQuickLog } = useExpenses();
  const { palette, scheme } = useTheme();
  const insets = useSafeAreaInsets();
  const fade = useRef(new Animated.Value(0)).current;
  const open = quickLog.open;
  const step = quickLog.open ? quickLog.step : null;

  useEffect(() => {
    Animated.timing(fade, {
      toValue: open ? 1 : 0,
      duration: duration.backdrop,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    }).start();
  }, [open, fade]);

  // Step 1 focuses a text field; every later step is tap-only. Without this the
  // system keyboard stays up and covers the numpad it was replaced by.
  useEffect(() => {
    if (step !== null && step !== 'name') Keyboard.dismiss();
  }, [step]);

  if (!quickLog.open) return null;

  const { draft, direction } = quickLog;
  const current = quickLog.step;
  const advanceable = canAdvance(current, draft);

  return (
    <Modal transparent animationType="none" visible onRequestClose={cancelQuickLog} statusBarTranslucent>
      <Animated.View style={[StyleSheet.absoluteFill, { opacity: fade }]}>
        <BlurView intensity={scheme === 'dark' ? 40 : 26} tint={scheme} style={StyleSheet.absoluteFill}>
          {/* Tapping the blurred background is Cancel — an escape that does not
              require finding the button. */}
          <Pressable
            testID="quicklog-scrim"
            accessibilityLabel="Dismiss"
            style={[StyleSheet.absoluteFill, { backgroundColor: palette.scrim }]}
            onPress={cancelQuickLog}
          />
        </BlurView>
      </Animated.View>

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={styles.centered}
        pointerEvents="box-none"
      >
        <View
          style={[styles.cardWrap, { paddingBottom: space.lg + insets.bottom }]}
          pointerEvents="box-none"
        >
          <ModalShell
            prompt={PROMPTS[current]}
            direction={direction}
            transitionKey={current}
            header={
              !isDetour(current) ? <StepDots total={FLOW.length} index={flowIndex(current)} /> : undefined
            }
          >
            {current === 'name' && (
              <NameStep
                value={draft.title}
                onChange={(title) => dispatchQuickLog({ type: 'setTitle', title })}
                onCancel={cancelQuickLog}
                onDone={() => dispatchQuickLog({ type: 'next' })}
                canAdvance={advanceable}
              />
            )}

            {current === 'amount' && (
              <AmountStep
                amountInput={draft.amountInput}
                currency={draft.currency}
                onChange={(amountInput) => dispatchQuickLog({ type: 'setAmountInput', amountInput })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
                onDone={() => dispatchQuickLog({ type: 'next' })}
                canAdvance={advanceable}
              />
            )}

            {current === 'category' && (
              <OptionListStep
                testIDPrefix="quicklog-category-"
                options={CATEGORIES}
                selectedId={draft.categoryId}
                withCategoryIcons
                onPick={(categoryId) => dispatchQuickLog({ type: 'pickCategory', categoryId })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
              />
            )}

            {current === 'account' && (
              <OptionListStep
                testIDPrefix="quicklog-account-"
                options={ACCOUNTS}
                selectedId={draft.accountId}
                onPick={(accountId) => dispatchQuickLog({ type: 'pickAccount', accountId })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
              />
            )}

            {current === 'payment' && (
              <OptionListStep
                testIDPrefix="quicklog-payment-"
                options={PAYMENT_METHODS}
                selectedId={draft.paymentMethodId}
                onPick={(paymentMethodId) => dispatchQuickLog({ type: 'pickPayment', paymentMethodId })}
                onBack={() => dispatchQuickLog({ type: 'back' })}
              />
            )}

            {current === 'confirm' && (
              <ConfirmStep
                draft={draft}
                onEdit={(target) => dispatchQuickLog({ type: 'goto', step: target })}
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
  centered: { flex: 1, justifyContent: 'flex-end' },
  cardWrap: { padding: space.lg },
});
