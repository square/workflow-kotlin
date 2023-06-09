plugins {
  id("kotlin-jvm")
}

dependencies {
  api(libs.kotlinx.coroutines.core)

  api(project(":workflow-core"))

  implementation(libs.lanterna)

  implementation(project(":workflow-runtime"))
}
