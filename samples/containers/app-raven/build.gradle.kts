plugins {
  id("com.android.application")
  `kotlin-android`
  `android-sample-app`
  `android-ui-tests`
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.containers.raven"
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:containers:poetry"))
}
