plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.dungeon"
    multiDexEnabled = true

    testInstrumentationRunner = "com.squareup.sample.dungeon.DungeonTestRunner"
  }

  compileOptions {
    // Required for SnakeYAML.
    isCoreLibraryDesugaringEnabled = true
  }
}

dependencies {
  // Required for SnakeYAML.
  "coreLibraryDesugaring"(libs.desugar)

  implementation(project(":samples:dungeon:common"))
  implementation(project(":samples:dungeon:timemachine-shakeable"))
  implementation(project(":workflow-ui:modal-android"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activityKtx)
  implementation(libs.androidx.constraint)
  implementation(libs.androidx.material)
  implementation(libs.androidx.gridlayout)
  implementation(libs.kotlin.coroutines.rx2)
  implementation(libs.okio)
  implementation(libs.rxandroid2)
  implementation(libs.cycler)

  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)

  androidTestImplementation(libs.test.androidx.uiautomator)
}
