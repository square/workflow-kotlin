plugins {
  id("com.android.library")
  kotlin("android")
  `android-defaults`
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  api(project(":workflow-ui:core-android"))

  api(libs.androidx.appcompat)
  api(libs.kotlin.jdk6)
  api(libs.androidx.test.espresso.core)
}
