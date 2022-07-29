plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  `android-ui-tests`
  published
}

dependencies {
  androidTestImplementation(libs.truth)

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  api(project(":workflow-core"))
  api(project(":workflow-ui:container-common"))
  api(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.fragment.core)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation(project(":workflow-runtime"))

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
}
