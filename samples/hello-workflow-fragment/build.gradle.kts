plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.helloworkflowfragment"
  }
  namespace = "com.squareup.sample.helloworkflowfragment"
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))
}
