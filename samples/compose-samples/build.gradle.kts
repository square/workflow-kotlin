plugins {
  id("com.android.application")
  `kotlin-android`
  `android-sample-app`
  `android-ui-tests`
  `compose-ui-tests`
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.compose"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
  }
  namespace = "com.squareup.sample.compose"
}

dependencies {
  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(libs.androidx.compose.ui)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.kotlin.test.jdk)

  androidTestImplementation(project(":workflow-runtime"))

  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.geometry)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.viewbinding)
  implementation(libs.kotlin.common)
  // For the LayoutInspector.
  implementation(libs.kotlin.reflect)

  implementation(project(":workflow-ui:compose"))
  implementation(project(":workflow-ui:compose-tooling"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
