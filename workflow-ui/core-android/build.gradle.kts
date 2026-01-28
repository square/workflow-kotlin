plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("android-ui-tests")
  id("published")
}

android {
  namespace = "com.squareup.workflow1.ui"
}

dependencies {
  androidTestImplementation(libs.androidx.activity.ktx)
  androidTestImplementation(libs.androidx.appcompat)
  androidTestImplementation(libs.androidx.lifecycle.viewmodel.ktx)
  androidTestImplementation(libs.androidx.lifecycle.viewmodel.savedstate)
  androidTestImplementation(libs.truth)

  api(libs.androidx.lifecycle.common)
  api(libs.androidx.savedstate)
  api(libs.kotlin.jdk6)

  // Needs to be API for the WorkflowInterceptor argument to WorkflowRunner.Config.
  api(project(":workflow-runtime"))
  api(project(":workflow-ui:core-common"))

  compileOnly(libs.androidx.viewbinding)

  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.core)
  implementation(libs.androidx.lifecycle.core)
  implementation(libs.androidx.lifecycle.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.savedstate)
  implementation(libs.androidx.transition)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.squareup.okio)

  implementation(project(":workflow-core"))

  testImplementation(libs.androidx.lifecycle.testing)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.robolectric)
  testImplementation(libs.robolectric.annotations)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.lifecycle.testing)
}
