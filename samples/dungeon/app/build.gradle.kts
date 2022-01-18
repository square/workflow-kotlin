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
  "coreLibraryDesugaring"(Dependencies.desugar_jdk_libs)

  debugImplementation(Dependencies.leakcanary)

  implementation(project(":samples:dungeon:common"))
  implementation(project(":samples:dungeon:timemachine-shakeable"))
  implementation(project(":workflow-ui:container-android"))
  implementation(project(":workflow-tracing"))

  implementation(Dependencies.AndroidX.activityKtx)
  implementation(Dependencies.AndroidX.constraint_layout)
  implementation(Dependencies.AndroidX.material)
  implementation(Dependencies.AndroidX.gridlayout)
  implementation(Dependencies.Kotlin.Coroutines.rx2)
  implementation(Dependencies.okio)
  implementation(Dependencies.rxandroid2)
  implementation(Dependencies.cycler)

  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)

  androidTestImplementation(Dependencies.Test.leakcanaryInstrumentation)
  androidTestImplementation(Dependencies.Test.AndroidX.uiautomator)
}
