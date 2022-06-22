import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.library
import com.squareup.workflow1.libsCatalog

plugins {
  id("android-defaults")
}

configure<TestedExtension> {
  @Suppress("UnstableApiUsage")
  buildFeatures.viewBinding = true
}

dependencies {
  "implementation"(project(":workflow-core"))
  "implementation"(project(":workflow-runtime"))
  "implementation"(project(":workflow-config:config-android"))

  "implementation"(libsCatalog.library("androidx-appcompat"))
  "implementation"(libsCatalog.library("timber"))
}
