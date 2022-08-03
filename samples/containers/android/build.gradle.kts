plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

android {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

dependencies {
  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(libs.androidx.compose.ui)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.kotlin.test.jdk)

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  api(project(":samples:containers:common"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
}
