plugins {
  id("com.android.library")
  `kotlin-android`
}

apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))

dependencies {
  api(project(":samples:dungeon:timemachine"))

  implementation(project(":workflow-ui:core-android"))
  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.kotlin.jdk8)
  implementation(libs.squareup.seismic)
}
