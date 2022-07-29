plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

dependencies {
  api(project(":samples:dungeon:timemachine"))

  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.kotlin.jdk8)
  implementation(libs.squareup.seismic)

  implementation(project(":workflow-ui:core-android"))
}
