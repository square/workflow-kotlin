plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("android-ui-tests")
  id("published")
}

android {
  namespace = "com.squareup.workflow1.ui.radiography"
}

dependencies {
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)

  implementation(libs.squareup.radiography)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
