package com.squareup.workflow1.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class KotlinMultiPlatformConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    if (!target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
      target.plugins.apply("org.jetbrains.kotlin.multiplatform")
    }

    target.tasks.withType(Test::class.java) { test ->
      target.properties
        .asSequence()
        .filter { (key, value) ->
          key.startsWith("workflow.runtime") && value != null
        }
        .forEach { (key, value) ->
          // Add in a system property to the fork for the test.
          test.systemProperty(key, value!!)
        }
    }

    target.kotlinCommonSettings(bomConfigurationName = "commonMainImplementation")
  }
}
