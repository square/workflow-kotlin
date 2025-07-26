plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
}

// This module is not published, since it's just internal testing utilities.

android {
  namespace = "com.squareup.workflow1.ui.internal.test.compose"
}

dependencies {
  api(libs.androidx.compose.ui.test.junit4)

  api(project(":workflow-ui:compose"))
  api(project(":workflow-ui:core-android"))
}
