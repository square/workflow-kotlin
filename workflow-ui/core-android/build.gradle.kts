plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  `android-ui-tests`
  published
}

dependencies {
  compileOnly(libs.androidx.viewbinding)

  api(project(":workflow-core"))
  // Needs to be API for the WorkflowInterceptor argument to WorkflowRunner.Config.
  api(project(":workflow-runtime"))
  api(project(":workflow-ui:core-common"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.core)
  implementation(libs.androidx.fragment.core)
  implementation(libs.androidx.lifecycle.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.core)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.lifecycle.testing)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)

  androidTestImplementation(libs.androidx.appcompat)
  androidTestImplementation(libs.truth)
}
