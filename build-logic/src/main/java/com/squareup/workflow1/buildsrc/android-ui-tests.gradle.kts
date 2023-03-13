package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.buildsrc.internal.library
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

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
  "androidTestImplementation"(libsCatalog.library("squareup-leakcanary-instrumentation"))
}
