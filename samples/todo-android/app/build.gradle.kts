plugins {
  id("com.android.application")
  kotlin("android")
  `android-sample-app`
  `android-ui-tests`
}

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
