import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import com.squareup.workflow1.buildsrc.internal.javaTarget
import com.squareup.workflow1.buildsrc.internal.javaTargetVersion

plugins {
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.androidx.benchmark)
}

android {
  namespace = "com.example.cw2"
  compileSdk = libsCatalog.version("compileSdk").toInt()

  defaultConfig {
    minSdk = 28
    targetSdk = libsCatalog.version("targetSdk").toInt()

    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
  }

  testBuildType = "release"
  buildTypes {
    debug {
      // Since isDebuggable can"t be modified by gradle for library modules,
      // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro"
      )
    }
    release {
      isDefault = true
    }
  }
  compileOptions {
    sourceCompatibility = javaTargetVersion
    targetCompatibility = javaTargetVersion
  }
  kotlinOptions {
    jvmTarget = javaTarget
  }
}

dependencies {
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.benchmark)
  // Add your dependencies here. Note that you cannot benchmark code
  // in an app module this way - you will need to move any code you
  // want to benchmark to a library module:
  // https://developer.android.com/studio/projects/android-library#Convert

}
