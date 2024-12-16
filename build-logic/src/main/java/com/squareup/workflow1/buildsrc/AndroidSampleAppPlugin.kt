package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.rickbusarow.kgx.dependency
import com.rickbusarow.kgx.library
import com.rickbusarow.kgx.libsCatalog
import com.squareup.workflow1.buildsrc.internal.implementation
import com.squareup.workflow1.buildsrc.internal.invoke
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidSampleAppPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(AndroidDefaultsPlugin::class.java)

    target.extensions.configure(TestedExtension::class.java) { testedExtension ->

      @Suppress("UnstableApiUsage")
      testedExtension.buildFeatures.viewBinding = true
    }

    target.extensions.configure(BaseAppModuleExtension::class.java) { appExtension ->
      appExtension.lint {
        baseline = target.file("lint-baseline.xml")
      }
    }

    target.dependencies {
      implementation(target.project(":workflow-core"))
      implementation(target.project(":workflow-runtime"))
      implementation(target.project(":workflow-config:config-android"))

      implementation(target.libsCatalog.library("androidx-appcompat"))
      implementation(target.libsCatalog.library("timber"))
    }
  }
}
