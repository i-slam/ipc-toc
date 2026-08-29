plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example.core"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }
}

// Shared by both apps, so the pieces they have in common are exported rather than duplicated.
dependencies {
  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.activity.compose)
  api(libs.androidx.compose.material.icons.core)
  api(libs.androidx.compose.material.icons.extended)
  api(libs.androidx.compose.material3)
  api(libs.androidx.compose.ui)
  api(libs.androidx.compose.ui.graphics)
  api(libs.androidx.compose.ui.tooling.preview)
  api(libs.androidx.core.ktx)
  api(libs.androidx.lifecycle.runtime.compose)
  api(libs.androidx.lifecycle.runtime.ktx)
  api(libs.kotlinx.coroutines.android)
  api(libs.kotlinx.coroutines.core)
}
