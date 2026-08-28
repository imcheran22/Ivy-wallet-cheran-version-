# SyncSpend

An ultra-fast expense tracker. React Native (Expo, TypeScript), local-first, no backend.

The whole premise is that logging an expense should cost about as much attention
as not logging it. A long form loses that race, so entry is four one-question
modals over a blurred backdrop, each answerable with a thumb.

## Install it on a phone

Every push builds a signed APK and attaches it to a **GitHub Release**. On the
phone: open the repo's Releases page, download the `.apk`, tap it, and allow
installs from your browser when Android asks.

It is signed with Expo's debug keystore, which means it installs *alongside*
anything from the Play Store rather than over it — fine for sideloading, not
something to publish.

## Run it from source

```bash
cd syncspend
npm install
npm start        # then press i / a, or scan the QR with Expo Go
npm test         # pure-logic tests (13)
npm run typecheck
```

## Logging from the lock screen

Android gives a third-party app **no lock-screen widget on a phone** — they were
removed in 5.0, and the Android 16 revival is tablets and docks only. Nothing in
this repo can change that. What is available on a Pixel, and what this app uses:

| Entry point | How you reach it | Unlock needed |
|---|---|---|
| **Quick Settings tile** | Swipe the shade down *on the lock screen*, tap **Log expense** | No |
| **Launcher shortcut** | Long-press the app icon → **Log expense** | Yes (home screen is behind the lock) |
| **FAB** | Open the app, tap **+** | Yes |

`MainActivity` carries `android:showWhenLocked`, so the flow draws over the
keyguard instead of sending you through an unlock first. All three fire the same
`syncspend://quick-log` deep link, read in JS by `useQuickLogLaunch`.

**One-time setup on the Pixel:** pull down the shade → pencil/edit → drag **Log
expense** into the active tiles. Then check Settings → Display → *Lock screen* →
"Quick Settings from lock screen" is on.

Launched that way the app shows **only** the entry flow on a blank surface, never
the dashboard, and exits when the flow ends — a phone drawing over its own
keyguard should not also be listing what its owner spends money on. First boot is
the exception: until the phone has been unlocked once, Android keeps app storage
encrypted, so a save has to wait for that first unlock.

## Architecture

```
App.tsx
└── ThemeProvider .............. light/dark palette from the system
    └── ExpenseProvider ........ single source of truth (expenses + flow state)
        └── DashboardScreen
            ├── WeekBarChart ... Sun..Sat bars, peak labelled
            ├── TransactionRow[]  the "Latest" list (long-press to delete)
            ├── FloatingActionButton
            └── QuickLogFlow ... backdrop + step routing (also opened by the
                                  QS tile / shortcut deep link)
                └── ModalShell . one card, contents cross-fade per step
                    ├── StepDots
                    ├── NameStep
                    ├── AmountStep (+ Numpad)
                    ├── OptionListStep  (category / account / payment)
                    └── ConfirmStep
```

| Layer | Files | Responsibility |
|---|---|---|
| Domain | `src/types`, `src/data/catalog.ts` | Expense shape, category/account/payment catalogs |
| Logic | `src/state/quickLogMachine.ts`, `src/state/weekSummary.ts`, `src/utils/` | Pure, no React, fully tested |
| State | `src/state/ExpenseStore.tsx` | Context + AsyncStorage persistence |
| Storage | `src/storage/expenseRepository.ts` | Read/write the log, tolerate a corrupt blob |
| Theme | `src/theme/` | Palettes + `useStyles`, rebuilt per palette |
| Native | `plugins/withLockScreenQuickLog.js` | QS tile, shortcut, `showWhenLocked` — generated, not committed |
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

Every row on the confirmation card jumps back to the question it answers, so
fixing a typo is one tap rather than four Backs. **Account** and **Payment** are
reachable only from that card — putting them in the linear flow would tax every
entry with two questions the defaults answer nine times out of ten.

The numpad is custom rather than the system numeric keyboard so it can be
reached with a thumb, cannot offer a comma or minus sign the parser would have
to reject, and never resizes the card mid-flow.

## Not built

- **Editing.** A saved row can be deleted (long-press) but not edited.
- **Multiple currencies.** `Expense.currency` exists and is respected by the
  formatter, but the week total sums minor units blind — correct only while one
  currency is in play.
- **Remote sync.** Everything lives in AsyncStorage on the device. The store is
  the only thing that touches persistence, so a sync layer would slot in behind
  `expenseRepository` without the UI noticing.
- **Custom categories and accounts.** Both catalogs are constants in
  `src/data/catalog.ts`.
