plugins {
  id("com.android.application")
  `kotlin-android`
  `android-sample-app`
  `android-ui-tests`
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.dungeon"
    multiDexEnabled = true

    testInstrumentationRunner = "com.squareup.sample.dungeon.DungeonTestRunner"
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
    }
  }
}

dependencies {
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.squareup.leakcanary.instrumentation)

  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.gridlayout)
  // Used to side load Baseline Profile when Benchmarking.
  implementation(libs.androidx.profileinstaller)
  implementation(libs.google.android.material)
  implementation(libs.kotlinx.coroutines.rx2)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.squareup.cycler)
  implementation(libs.squareup.okio)

  implementation(project(":samples:dungeon:common"))
  implementation(project(":samples:dungeon:timemachine-shakeable"))
  implementation(project(":workflow-tracing"))
  implementation(project(":workflow-ui:container-android"))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
