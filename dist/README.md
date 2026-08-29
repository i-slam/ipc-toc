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

## floating-button.apk — the standalone app

`floating-button.apk` — 795 KB, a second app with its own launcher icon and package
(`com.aistudio.ipcsolution.bubble`). It contains nothing but the floating button.

    sha256  adbb58fda87dcf057eec8ee3f4d6236d73f55ae163538a306115c7723199118c

Install it on its own — it does not need the diagnostic app. Open it once, tap **Show the
floating button**, allow the call log and then "display over other apps". Close the screen;
the button stays. Tapping the bubble shows the last call inline, copies it, opens the dialer,
or jumps to the diagnostic app when that one happens to be installed too. It returns after a
reboot, and hides from the bubble itself or its notification.

Both apps can be installed side by side: different packages, separate icons, separate state.
