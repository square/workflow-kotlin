plugins {
  id("com.android.library")
  kotlin("android")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))
apply(from = rootProject.file(".buildscript/configure-compose.gradle"))

dependencies {
  api(Dependencies.Workflow.UI.coreAndroid)

  implementation(Dependencies.Compose.foundation)
  implementation(Dependencies.Compose.layout)
  implementation(Dependencies.Compose.savedstate)

  androidTestImplementation(Dependencies.Compose.material)
}
