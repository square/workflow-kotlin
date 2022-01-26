plugins {
  id("com.android.library")
  kotlin("android")
  id("android-defaults")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  api(project(":samples:dungeon:timemachine"))

  implementation(project(":workflow-ui:core-android"))
  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.kotlin.jdk8)
  implementation(libs.squareup.seismic)
}
