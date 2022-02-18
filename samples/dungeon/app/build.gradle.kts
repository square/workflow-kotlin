plugins {
  id("com.android.application")
  kotlin("android")
  `android-sample-app`
  `android-ui-tests`
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.dungeon"
    multiDexEnabled = true

    testInstrumentationRunner = "com.squareup.sample.dungeon.DungeonTestRunner"
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":samples:dungeon:common"))
  implementation(project(":samples:dungeon:timemachine-shakeable"))
  implementation(project(":workflow-ui:container-android"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.google.android.material)
  implementation(libs.androidx.gridlayout)
  implementation(libs.kotlinx.coroutines.rx2)
  implementation(libs.squareup.okio)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.squareup.cycler)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  androidTestImplementation(libs.squareup.leakcanary.instrumentation)
  androidTestImplementation(libs.androidx.test.uiautomator)
}
