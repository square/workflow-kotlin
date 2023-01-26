package com.squareup.workflow1.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class KotlinJvmConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.plugins.apply("org.jetbrains.kotlin.jvm")


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


    target.kotlinCommonSettings(bomConfigurationName = "implementation")
  }
}
