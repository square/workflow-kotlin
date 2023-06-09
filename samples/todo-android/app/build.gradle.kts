plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.todo"
    multiDexEnabled = true
  }
  namespace = "com.squareup.sample.todo"
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
  implementation(project(":workflow-tracing"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
