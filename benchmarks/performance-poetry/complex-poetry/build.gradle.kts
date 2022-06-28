plugins {
  id("com.android.application")
  `kotlin-android`
  id("kotlin-parcelize")
  id("app.cash.molecule")
}
android {
  compileSdk = 32

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }

  defaultConfig {
    targetSdk = 32
    minSdk = 29
    applicationId = "com.squareup.benchmarks.performance.complex.poetry"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
    }
    create("benchmark") {
      initWith(buildTypes.getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
      // Selects release buildType if the benchmark buildType not available in other modules.
      matchingFallbacks.add("release")
      proguardFile("baseline-proguard-rules.pro")
    }
  }

  // Collisions in packaging.
  packagingOptions {
    resources.excludes.add("META-INF/AL2.0")
    resources.excludes.add("META-INF/LGPL2.1")
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  // API on an app module so these are transitive dependencies for the benchmarks.
  api(project(":samples:containers:android"))
  api(project(":samples:containers:poetry"))
  api(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.appcompat)
  // Used to side load Baseline Profile when Benchmarking and not installed by Play Store.
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.tracing.ktx)
  implementation(libs.timber)

  androidTestImplementation(project(":workflow-ui:internal-testing-android"))
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
}
