import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.library
import com.squareup.workflow1.libsCatalog

plugins {
  id("android-defaults")
}

configure<TestedExtension> {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions {
    // Disable transition and rotation animations.
    testOptions.animationsDisabled = true
  }
}

dependencies {
  "androidTestImplementation"(project(":workflow-ui:internal-testing-android"))

  "androidTestImplementation"(libsCatalog.library("androidx-test-espresso-core"))
  "androidTestImplementation"(libsCatalog.library("androidx-test-junit"))
  "androidTestImplementation"(libsCatalog.library("squareup-leakcanary-instrumentation")
  )
}
