import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import com.squareup.workflow1.buildsrc.internal.javaTarget
import com.squareup.workflow1.buildsrc.internal.javaTargetVersion

plugins {
  id("com.android.test")
  id("org.jetbrains.kotlin.android")
}

// Note: We are not including our defaults from .buildscript as we do not need the base Workflow
// dependencies that those include.

android {
  compileSdk = libsCatalog.version("compileSdk").toInt()

  compileOptions {
    sourceCompatibility = javaTargetVersion
    targetCompatibility = javaTargetVersion
  }

  kotlinOptions {
    jvmTarget = javaTarget
    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
  }

  defaultConfig {
    minSdk = 23
    targetSdk = libsCatalog.version("targetSdk").toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    create("release") {
      isDebuggable = true
      signingConfig = getByName("debug").signingConfig
      proguardFile("baseline-proguard-rules.pro")
    }
  }

  targetProjectPath = ":samples:dungeon:app"
  namespace = "com.squareup.sample.dungeon.benchmark"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
  implementation(libs.androidx.macro.benchmark)
  implementation(libs.androidx.test.espresso.core)
  implementation(libs.androidx.test.junit)
  implementation(libs.androidx.test.uiautomator)
}

androidComponents {
  beforeVariants(selector().all()) {
    // TODO use it.enable when using AGP 7.3+
    it.enable = it.buildType == "release"
  }
}
