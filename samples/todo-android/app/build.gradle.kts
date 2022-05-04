plugins {
  id("com.android.application")
  `kotlin-android`
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
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.kotlinx.coroutines.rx2)
  implementation(libs.squareup.okio)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.timber)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.uiautomator)
}
