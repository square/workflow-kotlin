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
  api(project(":workflow-core"))
  api(project(":workflow-ui:container-android"))
  api(project(":samples:containers:common"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(libs.androidx.compose.ui)
  androidTestImplementation(libs.kotlin.test.jdk)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
