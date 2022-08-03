plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

dependencies {
  api(libs.kotlinx.coroutines.core)

  api(project(":samples:dungeon:timemachine"))
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.transition)
  implementation(libs.google.android.material)
  implementation(libs.kotlin.jdk8)
  implementation(libs.squareup.seismic)
}
