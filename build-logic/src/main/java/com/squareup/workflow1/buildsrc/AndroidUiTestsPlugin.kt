package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.rickbusarow.kgx.library
import com.rickbusarow.kgx.libsCatalog
import com.squareup.workflow1.buildsrc.internal.androidTestImplementation
import com.squareup.workflow1.buildsrc.internal.invoke
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency

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

      if (!target.file("src/androidTest/AndroidManifest.xml").exists()) {
        testedExtension.useLeakCanaryMinSdkOverrideManifest(target, "androidTest")
      }
    }

    target.dependencies {
      androidTestImplementation(target.project(":workflow-ui:internal-testing-android"))

      androidTestImplementation(target.libsCatalog.library("androidx-test-espresso-core"))
      androidTestImplementation(target.libsCatalog.library("androidx-test-junit"))
      addProvider<MinimalExternalModuleDependency, ExternalModuleDependency>(
        "androidTestImplementation",
        target.libsCatalog.library("squareup-leakcanary-instrumentation")
      ) { dependency ->
        dependency.exclude(
          mapOf(
            "group" to "com.squareup.leakcanary",
            "module" to "leakcanary-android-core"
          )
        )
      }
    }
  }
}
