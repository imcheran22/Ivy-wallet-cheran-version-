import { DEFAULT_CURRENCY, defaultAccount, defaultPaymentMethod } from '../data/catalog';
import type { Expense } from '../types';
import { isCommittableAmount, toMinorUnits } from '../utils/money';

/** The linear path through the flow. Progress dots count these and only these. */
export const FLOW = ['name', 'amount', 'category', 'confirm'] as const;

/**
 * `account` and `payment` sit off the main path: they are only reachable by
 * tapping their row on the confirmation card, and they return straight to it.
 * Putting them in the linear flow would tax every entry with two questions
 * that are answered by the defaults nine times out of ten.
 */
export const DETOURS = ['account', 'payment'] as const;

export type Step = (typeof FLOW)[number] | (typeof DETOURS)[number];

export type Draft = {
  title: string;
  /** Raw numpad keystrokes, e.g. "5." mid-typing. Parsed only on commit. */
  amountInput: string;
  categoryId: string | null;
  accountId: string;
  paymentMethodId: string;
  currency: string;
  occurredAt: string;
};

export type QuickLogState =
  | { open: false }
  | { open: true; step: Step; draft: Draft; direction: 1 | -1 };

export type QuickLogAction =
  | { type: 'open' }
  | { type: 'cancel' }
  | { type: 'setTitle'; title: string }
  | { type: 'setAmountInput'; amountInput: string }
  | { type: 'pickCategory'; categoryId: string }
  | { type: 'pickAccount'; accountId: string }
  | { type: 'pickPayment'; paymentMethodId: string }
  | { type: 'goto'; step: Step }
  | { type: 'next' }
  | { type: 'back' };

export const closedState: QuickLogState = { open: false };

export function emptyDraft(now: Date = new Date()): Draft {
  return {
    title: '',
    amountInput: '',
    categoryId: null,
    accountId: defaultAccount().id,
    paymentMethodId: defaultPaymentMethod().id,
    currency: DEFAULT_CURRENCY,
    occurredAt: now.toISOString(),
  };
}

export function isDetour(step: Step): boolean {
  return (DETOURS as readonly string[]).includes(step);
}

/** 0-based position on the linear path; -1 for a detour. */
export function flowIndex(step: Step): number {
  return (FLOW as readonly string[]).indexOf(step);
}

/**
 * Every step is a transition on one draft rather than four screens each owning
 * a slice of the answer. That is what makes Back free: nothing has been written
 * anywhere until `confirm`, so stepping backwards is only an index change.
 */
export function quickLogReducer(state: QuickLogState, action: QuickLogAction): QuickLogState {
  if (action.type === 'open') {
    return { open: true, step: 'name', draft: emptyDraft(), direction: 1 };
  }
  if (!state.open) return state;
  if (action.type === 'cancel') return closedState;

  switch (action.type) {
    case 'setTitle':
      return { ...state, draft: { ...state.draft, title: action.title } };

    case 'setAmountInput':
      return { ...state, draft: { ...state.draft, amountInput: action.amountInput } };

    case 'pickCategory':
      // Choosing a category *is* the answer to step 3, so it advances on its
      // own: an extra "Next" tap would only ask the user to confirm what they
      // just said. Revisiting from the card returns there instead of moving on.
      return {
        ...state,
        step: 'confirm',
        draft: { ...state.draft, categoryId: action.categoryId },
        direction: 1,
      };

    case 'pickAccount':
      return { ...state, step: 'confirm', draft: { ...state.draft, accountId: action.accountId }, direction: -1 };

    case 'pickPayment':
      return {
        ...state,
        step: 'confirm',
        draft: { ...state.draft, paymentMethodId: action.paymentMethodId },
        direction: -1,
      };

    case 'goto': {
      // Only ever used to step *back* into an already-answered question from
      // the confirmation card, so it must not be able to skip one forward.
      const target = flowIndex(action.step);
      const current = flowIndex(state.step);
      if (!isDetour(action.step) && target > current) return state;
      return { ...state, step: action.step, direction: -1 };
    }

    case 'next': {
      if (isDetour(state.step)) return { ...state, step: 'confirm', direction: 1 };
      if (!canAdvance(state.step, state.draft)) return state;
      const next = FLOW[flowIndex(state.step) + 1];
      return next ? { ...state, step: next, direction: 1 } : state;
    }

    case 'back': {
      if (isDetour(state.step)) return { ...state, step: 'confirm', direction: -1 };
      const previous = FLOW[flowIndex(state.step) - 1];
      // Back out of the first step means "I did not want to log anything".
      return previous ? { ...state, step: previous, direction: -1 } : closedState;
    }

    default:
      return state;
  }
}

/** Guards the Done/Continue button so a step can never be committed half-answered. */
export function canAdvance(step: Step, draft: Draft): boolean {
  switch (step) {
    case 'name':
      return draft.title.trim().length > 0;
    case 'amount':
      return isCommittableAmount(draft.amountInput);
    case 'category':
      return draft.categoryId !== null;
    case 'confirm':
      return (
        draft.title.trim().length > 0 &&
        isCommittableAmount(draft.amountInput) &&
        draft.categoryId !== null
      );
    default:
      return true;
  }
}

/** The one place a draft becomes a real, persistable expense. */
export function draftToExpense(draft: Draft, id: string, now: Date = new Date()): Expense {
  if (draft.categoryId === null) {
    throw new Error('draftToExpense called before a category was chosen');
  }
  return {
    id,
    title: draft.title.trim(),
    amountMinor: toMinorUnits(draft.amountInput),
    currency: draft.currency,
    categoryId: draft.categoryId,
    accountId: draft.accountId,
    paymentMethodId: draft.paymentMethodId,
    occurredAt: draft.occurredAt,
    createdAt: now.toISOString(),
  };
}
