plugins {
  id("com.android.application")
  `kotlin-android`
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
  androidTestImplementation(libs.androidx.test.uiautomator)

  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.kotlinx.coroutines.rx2)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.squareup.okio)
  implementation(libs.timber)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))
  implementation(project(":workflow-tracing"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-ui:core-android"))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
