plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  `android-ui-tests`
  published
}

android {
  namespace = "com.squareup.workflow1.ui.radiography"
}

dependencies {
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)

  androidTestImplementation(project(":workflow-ui:container-android"))

  implementation(libs.squareup.radiography)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
