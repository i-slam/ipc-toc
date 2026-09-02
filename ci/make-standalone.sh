#!/usr/bin/env bash
# Assembles the floating-button app as a project that stands on its own.
#
# It is generated rather than kept as a second copy in the tree, because a copy drifts: the last
# hand-made one shipped without a signing config and had never been compiled. This runs from the
# live sources every time, and CI builds what it produces, so what gets handed out is known to
# work.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/build/standalone/floating-button}"

rm -rf "$OUT"
mkdir -p "$OUT"

echo "-- copying modules"
cp -r "$ROOT/core" "$OUT/core"
cp -r "$ROOT/bubble" "$OUT/bubble"
rm -rf "$OUT/core/build" "$OUT/bubble/build"

echo "-- copying the build system"
cp -r "$ROOT/gradle" "$OUT/gradle"
cp "$ROOT/gradlew" "$ROOT/gradlew.bat" "$ROOT/gradle.properties" "$OUT/"
chmod +x "$OUT/gradlew"

echo "-- copying the signing keys and the install script"
mkdir -p "$OUT/ci"
cp "$ROOT/ci/ipc-toc-release.keystore" "$ROOT/ci/ipc-toc-debug.keystore" "$ROOT/ci/README.md" "$OUT/ci/"
cp "$ROOT/ci/dev-install.ps1" "$OUT/ci/"

# The diagnostic app is not here, so neither are the plugins only it used. Leaving ksp, roborazzi,
# secrets and google-services declared would make every build resolve plugins nothing applies.
cat > "$OUT/build.gradle.kts" <<'EOF'
// Only what :core and :bubble actually apply. The diagnostic app's plugins are not here because
// the diagnostic app is not here.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
EOF

cat > "$OUT/settings.gradle.kts" <<'EOF'
pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Floating Button"

include(":core")
include(":bubble")
EOF

# googleServices.missing.passthrough belongs to a plugin that is not applied here.
grep -v "googleServices.missing.passthrough" "$ROOT/gradle.properties" > "$OUT/gradle.properties"

cat > "$OUT/.gitignore" <<'EOF'
*.iml
.gradle
.kotlin
local.properties
/build
/core/build
/bubble/build
.idea
.DS_Store
EOF

cat > "$OUT/local.properties.example" <<'EOF'
# Copy to local.properties and fill in. That file is gitignored and never leaves this machine.

# Where your Android SDK is.
sdk.dir=D:\\android-tools\\Sdk

# The inventory database. Without these the app still runs and says it has no database
# configured; with them it reads live stock.
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-publishable-key
EOF

cat > "$OUT/README.md" <<'EOF'
# Floating Button

A bubble that sits over every app. Tap it for an arc of actions: the last call and a WhatsApp
message to that number, the full call log with a WhatsApp button per row, the vehicle inventory
read live from Supabase, and the dialer. When a call ends the bubble opens itself onto whoever
just called.

Package `com.aistudio.ipcsolution.bubble`.

## Setup

1. **JDK 21** — `winget install EclipseAdoptium.Temurin.21.JDK`
2. **Android SDK** — via Android Studio, or the command-line tools.
3. **`local.properties`** — copy `local.properties.example` to `local.properties` and fill it in.
   `sdk.dir` is required; the two Supabase values are optional and decide whether the inventory
   screen reads live stock or says it has no database configured.

## Everyday use

```powershell
.\ci\dev-install.ps1              # build, install on the attached phone, launch, report a crash
.\ci\dev-install.ps1 -Variant debug
.\gradlew.bat :bubble:assembleRelease
.\gradlew.bat testDebugUnitTest
```

The first build downloads Gradle and the dependency tree and takes a few minutes; after that an
incremental build is seconds.

## What is in here

| Directory | What it is |
| --- | --- |
| `bubble/` | The app: one setup screen, the overlay service, the arc menu. |
| `core/` | Everything it is built from — call log, WhatsApp, inventory, overlay hosting, theme. |
| `ci/` | The signing keys and the install script. See `ci/README.md` for what those keys are. |

`core/` is a library rather than part of the app because the diagnostic app in the parent project
shares it. On its own here it is simply where most of the code lives.

## Signing

Both build types sign with the committed keys in `ci/`, so a build from here installs straight
over one from the parent project's releases and vice versa. `ci/README.md` says plainly what
those keys are and are not for. Override them with `KEYSTORE_PATH`, `STORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` in the environment for a real release.

## Generated

This project is generated from the parent repository by `ci/make-standalone.sh`, and CI builds
the result on every push, so it is known to compile. Edit here freely; to pull changes made
upstream, regenerate rather than merge by hand.
EOF

echo "-- done: $OUT"
find "$OUT" -maxdepth 1 -mindepth 1 | sort
