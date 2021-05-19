plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.todo"
    multiDexEnabled = true
  }
}

dependencies {
  implementation(project(":samples:containers:android"))
  implementation(project(":samples:todo-android:common"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activityKtx)
  implementation(libs.androidx.constraint)
  implementation(libs.androidx.material)
  implementation(libs.kotlin.coroutines.rx2)
  implementation(libs.okio)
  implementation(libs.rxandroid2)
  implementation(libs.timber)

  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)

  androidTestImplementation(libs.test.androidx.uiautomator)
}
