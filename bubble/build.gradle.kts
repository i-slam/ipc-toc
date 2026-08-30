import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// The inventory database's URL and publishable key. Kept out of the repository: this one is
// public, and its APKs are published, so anything committed here is effectively broadcast. Put
// them in local.properties (gitignored) or pass them through the environment.
val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

fun setting(name: String): String =
  System.getenv(name) ?: localProperties.getProperty(name) ?: ""


android {
  namespace = "com.example.bubble"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.ipcsolution.bubble"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    buildConfigField("String", "SUPABASE_URL", "\"${setting("SUPABASE_URL")}\"")
    buildConfigField("String", "SUPABASE_KEY", "\"${setting("SUPABASE_ANON_KEY")}\"")
  }

  // Shares the committed keys with :app - see the note there.
  signingConfigs {
    create("release") {
      storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/ci/ipc-toc-release.keystore")
      storePassword = System.getenv("STORE_PASSWORD") ?: "ipc-toc-public-ci"
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "ipc-toc-public-ci"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/ci/ipc-toc-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(project(":core"))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
