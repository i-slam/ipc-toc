# Prebuilt APK

`ipc-solution-poc-swiss-army-slim.apk` — 3.3 MB, minified release build (R8 + resource
shrinking), including the system-wide floating button. Checked in so it can be downloaded
straight from the repository when the Releases page is awkward to reach from a phone.

    sha256  97f2d8647062cd49e2bbac2ed516c1fad09d8e619a2311ef45a2bc3f4d609d56

## Installing

1. Uninstall any earlier build of `com.aistudio.ipcsolution.poc` first. Each CI run signs
   with a throwaway key, so installing over an older copy fails with "App not installed".
2. Allow installs from unknown sources for whichever app opens the file (Chrome, Files).
3. Open the APK. Play Protect will warn about an unknown developer — that is expected for
   a sideloaded debug-signed build.

## After first launch

Tap **Arm everything** — the green rocket at the top of the in-app rail on the right edge.
It asks for the call permissions, the overlay grant and the battery exemption in one chain,
starts the engine, and then puts the floating button on screen.

That floating button is the point: it sits above every app, not just this one. Drag it up
and down, tap it for Last Call Info, the popup trigger, call-end simulation and the engine
toggle. It comes back after a reboot, and "Hide this button" or the notification's Hide
action removes it.

Without the overlay grant there is no floating button and no call-end popup — that grant is
the one that matters most.

## Rebuilding

This file is a snapshot, not build output — it does not update itself. CI
(`.github/workflows/build-apk.yml`) assembles both APKs on every push, runs the unit
tests, installs the minified one on an API 30 emulator to confirm it launches, and only
then publishes them to a GitHub release. To refresh this copy, take the artifact from a
green run and commit it.
