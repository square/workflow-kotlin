plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  `android-ui-tests`
}

square {
  published(
    artifactId = "workflow-ui-container-android",
    name = "Workflow UI Container Android"
  )
}

android {
  namespace = "com.squareup.workflow1.ui.container"
}

dependencies {
  androidTestImplementation(libs.truth)

  api(libs.kotlin.jdk6)

  api(project(":workflow-ui:container-common"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.common)

  implementation(project(":workflow-ui:core-android"))

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.robolectric)
  testImplementation(libs.robolectric.annotations)
  testImplementation(libs.truth)
}
