import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.build.dependency
import com.squareup.workflow1.build.libsCatalog

plugins {
  `android-defaults`
}

configure<TestedExtension> {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["listener"] = "leakcanary.FailTestOnLeakRunListener"
  }

  testOptions {
    // Disable transition and rotation animations.
    testOptions.animationsDisabled = true
  }
}

dependencies {
  add("androidTestImplementation", project(":workflow-ui:internal-testing-android"))

  add("androidTestImplementation", project.libsCatalog.dependency("androidx-test-espresso-core"))
  add("androidTestImplementation", project.libsCatalog.dependency("androidx-test-junit"))
  add(
    "androidTestImplementation",
    project.libsCatalog.dependency("squareup-leakcanary-instrumentation")
  )
}
