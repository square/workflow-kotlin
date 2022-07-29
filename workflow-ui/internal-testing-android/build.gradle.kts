plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  id("org.jetbrains.dokka")
}

// This module is not published, since it's just internal testing utilities.

dependencies {
  api(libs.androidx.appcompat)
  api(libs.androidx.lifecycle.core)
  api(libs.androidx.test.core)
  api(libs.androidx.test.espresso.core)
  api(libs.androidx.test.espresso.idlingResource)
  api(libs.androidx.test.junit)
  api(libs.androidx.test.runner)
  api(libs.androidx.test.truth)
  api(libs.junit)
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.coroutines.test)
  api(libs.truth)

  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.androidx.lifecycle.common)
  implementation(libs.squareup.leakcanary.instrumentation)
  implementation(libs.squareup.leakcanary.objectwatcher.android)

  testImplementation(libs.hamcrest)
  testImplementation(libs.robolectric)
  testImplementation(libs.robolectric.annotations)
}
