package com.squareup.workflow1.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinAndroidConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply("org.jetbrains.kotlin.android")

    target.kotlinCommonSettings(bomConfigurationName = "implementation")
  }
}
