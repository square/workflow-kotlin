package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.squareup.workflow1.buildsrc.internal.library
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

plugins {
  id("android-defaults")
}

configure<TestedExtension> {
  @Suppress("UnstableApiUsage")
  buildFeatures.viewBinding = true
}

configure<BaseAppModuleExtension> {
  lint {
    baseline = file("lint-baseline.xml")
  }
}

dependencies {
  "implementation"(project(":workflow-core"))
  "implementation"(project(":workflow-runtime"))
  "implementation"(project(":workflow-config:config-android"))

  "implementation"(libsCatalog.library("androidx-appcompat"))
  "implementation"(libsCatalog.library("timber"))
}
