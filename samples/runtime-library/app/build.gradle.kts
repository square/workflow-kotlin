plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
  alias(libs.plugins.compose.compiler)
  id("compose-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.runtimelibrary.app"
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
    }
  }
  namespace = "com.squareup.sample.runtimelibrary.app"
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)

  // androidTestImplementation(libs.androidx.test.uiautomator)
  // androidTestImplementation(libs.squareup.leakcanary.instrumentation)
  //
  // debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.cardview)
  // implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core)
  // implementation(libs.androidx.gridlayout)
  // Used to side load Baseline Profile when Benchmarking.
  // implementation(libs.androidx.profileinstaller)
  // implementation(libs.androidx.recyclerview)
  // implementation(libs.google.android.material)
  implementation(libs.kotlin.common)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.rx2)
  // implementation(libs.rxjava2.rxandroid)
  // implementation(libs.squareup.cycler)
  // implementation(libs.squareup.okio)
  implementation(composeBom)
  implementation(libs.androidx.compose.foundation)

  implementation(project(":workflow-runtime"))
  // implementation(project(":workflow-ui:core-android"))
  // implementation(project(":workflow-ui:core-common"))
  implementation(project(":workflow-ui:compose"))
  implementation(project(":samples:runtime-library:lib"))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
