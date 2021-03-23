plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":workflow-core"))
  implementation(project(":workflow-runtime"))

  implementation(libs.lanterna)
}
