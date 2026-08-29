# Signing keys

`ipc-toc-debug.keystore` and `ipc-toc-release.keystore` sign every build of both apps.

They are committed on purpose. CI used to generate a key per run, which meant each APK refused
to install over the previous one — `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, because Android treats a
different signature as a different author. Fixed keys make each build a normal update.

    ipc-toc-debug.keystore    alias androiddebugkey   password android
    ipc-toc-release.keystore  alias upload            password ipc-toc-public-ci

These are public and carry no secrecy. What that costs: anyone can build an APK signed with the
same key, which a phone would accept as an update to these apps. That is acceptable for builds
that are sideloaded from this repo and are not on any store — the APKs here are already public.

**Do not use these keys to publish anywhere.** A Play Store upload key must be private and kept
out of version control. The release config reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`
and `KEY_PASSWORD` from the environment first, so a real signing key overrides these without
touching the build files:

    KEYSTORE_PATH=/secure/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle assembleRelease

Switching to a different key means every sideloaded install has to be uninstalled once more.
