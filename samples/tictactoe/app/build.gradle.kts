plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.tictacworkflow"
    multiDexEnabled = true
  }

  testOptions.unitTests.isIncludeAndroidResources = true
  namespace = "com.squareup.sample.tictactoe"
}

dependencies {
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.espresso.intents)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.truth)

  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.lifecycle.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.core)
  implementation(libs.androidx.test.espresso.idlingResource)
  implementation(libs.androidx.transition)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.rxjava2.rxjava)
  implementation(libs.squareup.okio)
  implementation(libs.timber)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:tictactoe:common"))
  implementation(project(":workflow-tracing"))
  implementation(project(":workflow-ui:container-android"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
