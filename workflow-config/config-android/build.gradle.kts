plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

square {
  published(
    artifactId = "workflow-config-android",
    name = "Workflow Runtime Android Configuration"
  )
}

android {
  @Suppress("UnstableApiUsage")
  buildFeatures.buildConfig = true

  val runtimeConfig = project.findProperty("workflow.runtime") ?: "baseline"

  defaultConfig {
    buildConfigField("String", "WORKFLOW_RUNTIME", "\"$runtimeConfig\"")
  }
  namespace = "com.squareup.workflow1.config"
}

dependencies {
  api(libs.kotlin.common)

  implementation(project(":workflow-runtime"))
}
