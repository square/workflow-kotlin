package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.buildsrc.internal.androidTestImplementation
import com.squareup.workflow1.buildsrc.internal.invoke
import com.squareup.workflow1.buildsrc.internal.library
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidUiTestsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(AndroidDefaultsPlugin::class.java)

    target.extensions.configure(TestedExtension::class.java) { testedExtension ->

      testedExtension.defaultConfig { defaultConfig ->
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      testedExtension.testOptions { testOptions ->
        // Disable transition and rotation animations.
        testOptions.animationsDisabled = true
      }
    }

    target.dependencies {
      androidTestImplementation(target.project(":workflow-ui:internal-testing-android"))

      androidTestImplementation(target.libsCatalog.library("androidx-test-espresso-core"))
      androidTestImplementation(target.libsCatalog.library("androidx-test-junit"))
      androidTestImplementation(target.libsCatalog.library("squareup-leakcanary-instrumentation"))
    }
  }
}
