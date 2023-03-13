package com.squareup.workflow1.buildsrc

import com.squareup.workflow1.buildsrc.internal.library
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

plugins {
  id("android-defaults")
}

dependencies {
  "androidTestImplementation"(project(":workflow-ui:internal-testing-compose"))

  "androidTestImplementation"(libsCatalog.library("androidx-compose-ui-test-junit4"))
}
