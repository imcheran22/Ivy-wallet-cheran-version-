# Cloud sync (Supabase) & SMS auto-import

Two related features, both opt-in and both configured from **Settings**.

## Cloud sync (Supabase)

Ivy Wallet is still a local-first, offline-capable Android app - there is nothing to deploy
on Vercel here, since Vercel hosts web apps/APIs, not Android APKs. What this adds instead is
an optional mirror of your data (accounts, categories, transactions) to a Supabase Postgres
project you control, reached directly over Supabase's REST API (no new backend to host).

Setup:

1. Create a free project at [supabase.com](https://supabase.com).
2. Open the SQL editor and run [`supabase_schema.sql`](./supabase_schema.sql) from this folder.
3. In your Supabase project settings, copy the **Project URL** and the **anon/public API key**.
4. In the app: Settings -> "Cloud sync (Supabase)" -> paste both -> Save credentials -> enable
   the switch.
5. "Sync now" pushes your current local data up. "Restore from cloud" pulls it back down - use
   this on a fresh install/new device to get your data back.

How it works:

- Every row is tagged with a random `owner_id` generated once per app install (Settings has no
  concept of a Supabase user account/login), so multiple installs can share one Supabase
  project without colliding.
- Sync is a full mirror upsert, not a CRDT - the last push wins per table. Fine for one person
  using the app on one device at a time; if you actively edit the same data from two devices
  at once, the later sync overwrites the earlier one.
- A background sync runs roughly hourly (Android WorkManager) while enabled, plus immediately
  after an SMS auto-import.
- See the comment header in `supabase_schema.sql` for the (deliberately minimal) security
  model - it is NOT equivalent to real user authentication.

## SMS auto-import

Settings -> "Auto-import transactions from SMS". Turning it on requests the `RECEIVE_SMS`/
`READ_SMS` permissions and starts listening for new incoming SMS (existing/older messages in
your inbox are not scanned).

For each incoming SMS, the app:

1. Looks for generic "debited"/"credited" phrasing with a nearby Rs./INR amount - this covers
   most Indian banks without bank-specific rules, but isn't exhaustive. Messages that don't
   match (OTPs, promos, failed/reversed transactions, payment requests) are ignored.
2. Picks an account: if an account's "Bank a/c or card last digits" (set when creating/editing
   an account) matches the digits mentioned in the SMS, it's used; otherwise the transaction
   falls back to your primary account, on the principle that a transaction landing on the
   "wrong" account beats the import silently doing nothing.
3. Best-effort guesses a category from the UPI handle/merchant text against a small keyword
   list (rideshare -> Transport, food delivery -> Food, etc.); if nothing matches, the
   transaction is left uncategorized rather than guessing wrong.
4. Creates the transaction and (if cloud sync is enabled) pushes it up.

This is heuristic parsing, not a bank integration - always spot-check imported transactions,
especially amounts/accounts on SMS formats you haven't seen before.
