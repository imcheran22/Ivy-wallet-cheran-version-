# SyncSpend

An ultra-fast expense tracker. React Native (Expo, TypeScript), local-first, no backend.

The whole premise is that logging an expense should cost about as much attention
as not logging it. A long form loses that race, so entry is four one-question
modals over a blurred backdrop, each answerable with a thumb.

## Run it

```bash
cd syncspend
npm install
npm start        # then press i / a, or scan the QR with Expo Go
npm test         # pure-logic tests (13)
npm run typecheck
```

## Architecture

```
App.tsx
└── ExpenseProvider ............ single source of truth (expenses + flow state)
    └── DashboardScreen
        ├── WeekBarChart ....... Sun..Sat bars
        ├── TransactionRow[] ... the "Latest" list
        ├── FloatingActionButton
        └── QuickLogFlow ....... backdrop + step routing
            └── ModalShell ..... one card, contents cross-fade per step
                ├── NameStep
                ├── AmountStep (+ Numpad)
                ├── CategoryStep
                └── ConfirmStep
```

| Layer | Files | Responsibility |
|---|---|---|
| Domain | `src/types`, `src/data/catalog.ts` | Expense shape, category/account/payment catalogs |
| Logic | `src/state/quickLogMachine.ts`, `src/state/weekSummary.ts`, `src/utils/` | Pure, no React, fully tested |
| State | `src/state/ExpenseStore.tsx` | Context + AsyncStorage persistence |
| Storage | `src/storage/expenseRepository.ts` | Read/write the log, tolerate a corrupt blob |
| UI | `src/components`, `src/screens` | Views over the state above |

### Three decisions worth knowing

**The flow is a reducer over one draft, not four screens.** `quickLogMachine.ts`
holds `{ step, draft }` and every step is a transition on it. Nothing is written
anywhere until `confirm`, which makes Back free (an index change) and Cancel
total (drop the draft). It also means the flow can be entered from a FAB, a
shortcut or a lock-screen widget without any of them knowing how many steps
there are.

**Money is integer minor units.** `amountMinor: 500`, never `5.0`. The numpad
edits a *string* (`"5."` is a valid mid-typing state a float would round away)
and it becomes an integer exactly once, at save. Formatting back to `$5.00`
happens at the render boundary.

**The header total and the chart come from one pass.** `summariseWeek` returns
both, because two separate reductions over the same list is how a dashboard ends
up showing bars that don't add up to the number above them.

## The Quick Log flow

| Step | Prompt | Input | Advances on |
|---|---|---|---|
| 1 | What is the expense about? | Text field, autofocused | **Done** (or return key) |
| 2 | What is the amount? | Large display + custom numpad | **Done** |
| 3 | Which category? | Scrollable list of 6 | Tapping a row — no extra confirm tap |
| 4 | Confirm expense details | Amount + summary table | **Continue** → saved |

Cancel, Back, and tapping the blurred backdrop all discard without writing.

The numpad is custom rather than the system numeric keyboard so it can be
reached with a thumb, cannot offer a comma or minus sign the parser would have
to reject, and never resizes the card mid-flow.

## Not built

- **Editing and deleting.** Rows are append-only today.
- **Multiple currencies.** `Expense.currency` exists and is respected by the
  formatter, but the week total sums minor units blind — correct only while one
  currency is in play.
- **Remote sync.** Everything lives in AsyncStorage on the device. The store is
  the only thing that touches persistence, so a sync layer would slot in behind
  `expenseRepository` without the UI noticing.
