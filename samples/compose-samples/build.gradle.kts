plugins {
  id("com.android.application")
  kotlin("android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.compose"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":workflow-ui:compose"))
  implementation(project(":workflow-ui:compose-tooling"))
  implementation(project(":workflow-ui:core-android"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.viewbinding)
  // For the LayoutInspector.
  implementation(libs.kotlin.reflect)

  androidTestImplementation(project(":workflow-runtime"))
  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(libs.androidx.compose.ui)
  androidTestImplementation(libs.kotlin.test.jdk)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
