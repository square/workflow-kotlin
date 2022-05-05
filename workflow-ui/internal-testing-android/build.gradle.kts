plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  id("org.jetbrains.dokka")
}

// This module is not published, since it's just internal testing utilities.

dependencies {
  api(project(":workflow-ui:core-android"))

  api(libs.androidx.appcompat)
  api(libs.androidx.test.core)
  api(libs.androidx.test.espresso.core)
  api(libs.androidx.test.junit)
  api(libs.androidx.test.runner)
  api(libs.androidx.test.truth)
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.test)
  api(libs.truth)

  implementation(libs.squareup.leakcanary.instrumentation)

  testImplementation(libs.robolectric)
}
