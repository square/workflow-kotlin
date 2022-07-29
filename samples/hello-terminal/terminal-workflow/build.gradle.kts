plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(libs.lanterna)

  implementation(project(":workflow-core"))
  implementation(project(":workflow-runtime"))
}
