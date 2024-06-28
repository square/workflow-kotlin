plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
}

android {
  namespace = "com.squareup.sample.timemachine.shakeable"
}

dependencies {
  api(libs.kotlinx.coroutines.core)

  api(project(":samples:dungeon:timemachine"))
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.transition)
  implementation(libs.google.android.material)
  implementation(libs.kotlin.jdk8)
  implementation(libs.squareup.seismic)
}
