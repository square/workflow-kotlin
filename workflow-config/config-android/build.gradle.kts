plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("published")
}

android {
  @Suppress("UnstableApiUsage")
  buildFeatures.buildConfig = true

  val runtimeConfig = project.findProperty("workflow.runtime") ?: "baseline"
  println("Workflow Runtime Configuration via test: 'workflow.runtime': '$runtimeConfig'")

  defaultConfig {
    buildConfigField("String", "WORKFLOW_RUNTIME", "\"$runtimeConfig\"")
  }
  namespace = "com.squareup.workflow1.config"
}

dependencies {
  api(libs.kotlin.common)

  implementation(project(":workflow-runtime"))
}
