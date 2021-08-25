plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.compose"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.0.1"
  }
}

dependencies {
  implementation(project(":workflow-ui:compose"))
  implementation(project(":workflow-ui:compose-tooling"))
  implementation(project(":workflow-ui:core-android"))
  implementation(Dependencies.AndroidX.Compose.activity)
  implementation(Dependencies.AndroidX.Compose.material)
  implementation(Dependencies.AndroidX.Compose.tooling)
  implementation(Dependencies.AndroidX.Compose.ui)
  implementation(Dependencies.AndroidX.Lifecycle.viewModelKtx)
  implementation(Dependencies.AndroidX.Lifecycle.viewModelSavedState)
  implementation(Dependencies.AndroidX.viewbinding)
  // For the LayoutInspector.
  implementation(Dependencies.Kotlin.reflect)

  androidTestImplementation(project(":workflow-runtime"))
  androidTestImplementation(Dependencies.AndroidX.activity)
  androidTestImplementation(Dependencies.AndroidX.Compose.ui)
  androidTestImplementation(Dependencies.Kotlin.Test.jdk)
  androidTestImplementation(Dependencies.Test.AndroidX.core)
  androidTestImplementation(Dependencies.Test.AndroidX.truthExt)
  androidTestImplementation(Dependencies.Test.AndroidX.compose)
}
