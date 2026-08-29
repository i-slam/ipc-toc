# Prebuilt APK

`ipc-solution-poc-swiss-army-slim.apk` — 3.3 MB, minified release build (R8 + resource
shrinking), checked in so it can be downloaded straight from the repository when the
Releases page is awkward to reach from a phone.

    sha256  1f49c85efa8488a95d77844560b00e1fe25c640add0f5e496505f112a52b23bb

## Installing

1. Uninstall any earlier build of `com.aistudio.ipcsolution.poc` first. Each CI run signs
   with a throwaway key, so installing over an older copy fails with "App not installed".
2. Allow installs from unknown sources for whichever app opens the file (Chrome, Files).
3. Open the APK. Play Protect will warn about an unknown developer — that is expected for
   a sideloaded debug-signed build.

## After first launch

The Swiss-army rail sits on the right edge, mid-screen. Its top slot opens **Last Call
Info**, which needs the call log permission — the amber card on that page requests it.
Overlay permission and the battery exemption are further down the rail, and the call-end
popup will not appear without the overlay grant.

## Rebuilding

This file is a snapshot, not build output — it does not update itself. CI
(`.github/workflows/build-apk.yml`) assembles both APKs on every push, runs the unit
tests, installs the minified one on an API 30 emulator to confirm it launches, and only
then publishes them to a GitHub release. To refresh this copy, take the artifact from a
green run and commit it.
