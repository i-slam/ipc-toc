# Project status

Last updated at commit `729524e`, branch `claude/phone-app-sticky-actions-s46yjw`.

## What this is

Two Android apps sharing one library.

| Module | Package | What it is |
| --- | --- | --- |
| `:bubble` | `com.aistudio.ipcsolution.bubble` | **Floating Button** — a bubble that sits over every app. This is the one being developed. |
| `:app` | `com.aistudio.ipcsolution.poc` | **IPC Solution PoC** — the original Tecno/HiOS call-detection diagnostic tool. Largely untouched lately. |
| `:core` | — | Everything both share: call log, WhatsApp, overlay hosting, inventory, the arc menu, the palette. |

The bubble app is becoming the Floating CRM from the concept: answer the phone, and in two
taps send the caller vehicles from the dealership inventory over WhatsApp.

## Working and verified

Verified means it passed on the API 30 emulator in CI, not just in unit tests.

- **The bubble** — draggable overlay above every app, bottom-right, survives backgrounding.
- **Gooey arc menu** — five actions fan out and visually merge. The merge is a chained
  `RenderEffect` (blur → alpha threshold) and needs API 31+; below that the fan opens without it.
- **Call log** — every recent call, tabs for All / Missed / In / Out with counts, a WhatsApp
  button per row. A number that cannot be resolved to a country code gets a greyed button that
  says why, instead of opening WhatsApp to an "invalid number" error.
- **Auto-pop** — when a call ends the bubble opens itself onto that caller. On end, not on ring:
  from API 31 a plain telephony listener is given the state but not the number.
- **Inventory** — reads the Supabase `vehicles` table, filtered to `status=available`, newest
  first. Multi-select, then send to the caller with photos and a spec-and-price caption.
- **Signing** — fixed keys in `ci/`, so every build installs over the last with `install -r`.

## Not yet verified

Honest list. These are built and unit-tested but have never run against the real thing.

| Thing | Why not | What would settle it |
| --- | --- | --- |
| The WhatsApp send | The emulator has no WhatsApp installed | Send a vehicle from the phone; note whether it lands in the chat or on WhatsApp's contact picker |
| Auto-pop on a real call | The emulator makes no calls | Take a call, hang up, see if the bubble opens within ~2s |
| Live inventory in an APK | No build has been given the database credentials yet | See *Blocked on you* below |
| The `jid` chat targeting | Same as the send | As above — the picker appearing is the documented fallback working, not a failure |

## Blocked on you

**1. Getting the live inventory into a build.** The database URL and key are deliberately not in
this repository, because it is public and its APKs are published. Two ways forward:

- *Local build, no risk:* put `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties`
  (gitignored) and run `.\ci\dev-install.ps1`. The key never leaves your machine.
- *Published builds:* add the same two as repository secrets named `SUPABASE_URL` and
  `SUPABASE_ANON_KEY`. The workflow already passes them through.

**2. The `vehicles` table lets anyone write.** RLS is enabled, but the policies are
`USING (true)` for `public`, which includes `anon` — the role that publishable key authenticates
as. So anyone holding the key can read, insert and update every row. Deletion is not possible;
there is no DELETE policy.

This matters for option 2 above: a key inside a published APK can be extracted by anyone. That
is the normal, intended way to ship a Supabase publishable key **only while `anon` is
`SELECT`-only**.

The fix is reversible and keeps the app working:

```sql
drop policy "public write"  on public.vehicles;
drop policy "public update" on public.vehicles;
create policy "authenticated write"  on public.vehicles for insert to authenticated with check (true);
create policy "authenticated update" on public.vehicles for update to authenticated using (true);
```

It has not been applied, because whatever currently writes stock into that table may be using
the same publishable key, and this would stop it. If that writer uses the `service_role` key it
bypasses RLS and is unaffected.

## Known state of the data

Checked against the live project, 8 available vehicles:

- 6 of 8 have photos; the other 2 send as text rather than being dropped from the selection.
- The `vehicle-photos` storage bucket is public, so photo downloads need no authentication.
- No rows currently have `special_offer` set, so the offer badge and struck-through price are
  unexercised against real data.

## Deliberately not built

- **Call recording, playback, waveform.** The concept's call-log popup is built around it.
  Android 10 removed call-audio recording for third-party apps and Android 11 closed the
  accessibility workaround; only the default dialer with carrier privileges or a system app can
  do it. A sideloaded app cannot, at all.
- **A real WhatsApp location pin.** Cannot be sent programmatically. A maps URL as text is the
  achievable version.
- **Adding or editing stock from the phone.** The database is the source of truth and is
  maintained elsewhere; the app is a reader by design.

## Building

See `docs/LOCAL-DEV.md` for the full setup. The short version, once JDK 21 and the Android SDK
are in place and `local.properties` points at the SDK:

```powershell
.\ci\dev-install.ps1              # build :bubble, install on the attached phone, launch it
.\gradlew.bat testDebugUnitTest   # the whole suite, about three minutes
```

CI builds all three APKs on every push, runs the unit tests, then installs and drives them on an
API 30 emulator before publishing a release. The emulator run checks six things: the debug build
as a control, the minified build, the floating rail overlay, the standalone bubble, the call log
list (with rows seeded into the provider), and the inventory screen.

## Current CI state

Build 32 green. Builds 33 (database-backed inventory) and 34 (the CI secret plumbing) were in
flight when this was written — check the Actions tab for where they landed.

Test suite: 17 test classes across the three modules.
