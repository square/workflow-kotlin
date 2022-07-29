plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  `android-ui-tests`
  published
}

dependencies {
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)

  androidTestImplementation(project(":workflow-ui:container-android"))

  api(libs.kotlin.jdk6)
  api(libs.squareup.radiography)

  api(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.fragment.core)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation(project(":workflow-runtime"))
}
