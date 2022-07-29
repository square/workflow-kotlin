plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  id("org.jetbrains.dokka")
}

// This module is not published, since it's just internal testing utilities.

dependencies {
  api(libs.androidx.compose.ui.test.junit4)

  api(project(":workflow-ui:compose"))
  api(project(":workflow-ui:core-android"))
}
