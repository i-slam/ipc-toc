# Building on your own machine

Everything so far has been built in GitHub Actions, because the environment Claude runs in
cannot reach `dl.google.com` and so cannot install the Android SDK. Nothing about the project
requires that - it is an ordinary Gradle build. These are the steps to run it locally on
Windows.

## One-time setup

**1. JDK 21.** Temurin 21 is what CI uses:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

**2. The Android SDK.** If you already have Android Studio, you have this and can skip to
step 3. Otherwise the command-line tools are enough:

```powershell
winget install Google.AndroidStudio     # easiest, includes the SDK manager UI
```

You already have `D:\android-tools\Sdk\platform-tools\adb.exe`, so point everything at
`D:\android-tools\Sdk`.

**3. Tell Gradle where the SDK is.** Create `local.properties` in the repository root -
it is gitignored, so it stays on your machine:

```properties
sdk.dir=D:\\android-tools\\Sdk
```

Note the doubled backslashes; it is a Java properties file.

**3b. Point the inventory at the database.** The vehicle inventory is read from Supabase, and the
credentials are deliberately *not* in the repository - this repo is public and its APKs are
published, so anything committed here is broadcast. Add them to the same `local.properties`:

```properties
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your publishable key>
```

A build without them still runs; the inventory screen says it has no database configured and
falls back to whatever that phone last cached.

For the published APKs to read live stock, the same two values go in as **repository secrets**
(`Settings → Secrets and variables → Actions`), named `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
The workflow passes them through to the build; with them unset it still succeeds, unconfigured.

Before putting that key in an APK you hand out, check what the `anon` role is allowed to do:

```sql
select policyname, cmd, roles::text, qual, with_check
from pg_policies where tablename = 'vehicles';
```

A publishable key in a distributed app is normal and safe when `anon` can only `SELECT`. It is
not safe while `anon` can also `INSERT` or `UPDATE`, because anyone can unpack an APK.

**4. Accept the SDK licences**, so Gradle can fetch the platform and build-tools it needs
on the first build:

```powershell
& "D:\android-tools\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

If `cmdline-tools` is missing, install it once from Android Studio's SDK Manager
(SDK Tools → Android SDK Command-line Tools), or run
`sdkmanager "cmdline-tools;latest"` from an existing installation.

## Getting the code

```powershell
git clone https://github.com/i-slam/ipc-toc.git
cd ipc-toc
git checkout claude/phone-app-sticky-actions-s46yjw
```

## The everyday loop

```powershell
.\ci\dev-install.ps1
```

That builds `:bubble`, installs it on the attached phone, pre-grants the permissions so you
are not tapping through dialogs on every reinstall, opens the call log screen and fails
loudly if it crashed. Options:

```powershell
.\ci\dev-install.ps1 -Variant debug      # unminified, builds faster, no R8
.\ci\dev-install.ps1 -Module app         # the full diagnostic app instead
.\ci\dev-install.ps1 -NoLaunch           # install only
.\ci\dev-install.ps1 -SkipBuild          # reinstall what is already built
.\ci\dev-install.ps1 -Adb "C:\other\adb.exe"
```

Build only, no phone involved:

```powershell
.\gradlew.bat :bubble:assembleRelease
.\gradlew.bat testDebugUnitTest          # the whole test suite, ~3 minutes
.\gradlew.bat :core:testDebugUnitTest    # just the call log tests
```

The first build downloads Gradle 9.3.1 and the dependency tree and takes a few minutes.
After that an incremental `:bubble` build is seconds.

## What to expect

- **`:bubble` is the fast one.** It is one activity, one service and the shared `:core`
  library. `:app` drags in Firebase, Room, Retrofit and KSP, so it takes several times
  longer - build it only when you are changing the diagnostic app.
- **Signing is already handled.** Both apps sign with the fixed keys in `ci/`, so a locally
  built APK installs straight over one from a release and vice versa. See `ci/README.md`
  for what those keys are and are not for.
- **The emulator checks stay in CI.** `ci/smoke-test.sh` is written for a Linux runner with
  a headless AVD. Locally your phone is the better test device anyway - it has real call
  logs and WhatsApp actually installed, neither of which the emulator has.

## If something goes wrong

| Symptom | Cause |
| --- | --- |
| `SDK location not found` | `local.properties` missing or the path is wrong |
| `Failed to install ... licences not accepted` | run `sdkmanager --licenses` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | the phone holds a build signed before the keys were fixed; the script uninstalls and retries on its own |
| `No device attached` | check the cable, and that USB debugging is still authorised for this PC |
| Local build differs from CI | CI is the reference - it runs `gradle testDebugUnitTest`, `assembleDebug` and `assembleRelease` on every push |
