package com.squareup.workflow1.buildsrc

import com.rickbusarow.kgx.library
import com.rickbusarow.kgx.libsCatalog
import com.squareup.workflow1.buildsrc.internal.androidTestImplementation
import com.squareup.workflow1.buildsrc.internal.invoke
import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposeUiTestsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply(AndroidDefaultsPlugin::class.java)

    target.dependencies {
      androidTestImplementation(target.project(":workflow-ui:internal-testing-compose"))

      androidTestImplementation(target.libsCatalog.library("androidx-compose-ui-test-junit4"))
    }
  }
}
