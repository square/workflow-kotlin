plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  id("org.jetbrains.dokka")
}

android {
  @Suppress("UnstableApiUsage")
  buildFeatures.buildConfig = true

  val runtimeConfig = project.findProperty("workflow.runtime") ?: "baseline"

  defaultConfig {
    buildConfigField("String", "WORKFLOW_RUNTIME", "\"$runtimeConfig\"")
  }
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  api(project(":workflow-runtime"))
}
