plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
  published
}

android {
  @Suppress("UnstableApiUsage")
  buildFeatures.buildConfig = true

  val runtimeConfig = project.findProperty("workflow.runtime") ?: "baseline"

  defaultConfig {
    buildConfigField("String", "WORKFLOW_RUNTIME", "\"$runtimeConfig\"")
  }
}

dependencies {
  api(project(":workflow-runtime"))
}
