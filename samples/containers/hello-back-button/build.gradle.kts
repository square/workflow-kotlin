plugins {
  id("com.android.application")
  `kotlin-android`
  `android-sample-app`
  `android-ui-tests`
  id("kotlin-parcelize")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.hellobackbutton"
  }
  namespace = "com.squareup.sample.hellobackbutton"
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-ui:container-android"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
