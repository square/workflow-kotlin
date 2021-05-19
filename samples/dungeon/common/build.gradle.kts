plugins {
  `java-library`
  kotlin("jvm")
  id("kotlinx-serialization")
}

dependencies {
  implementation(project(":workflow-ui:core-common"))
  implementation(project(":workflow-core"))

  implementation(libs.kotlin.serialization.json)

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.test.kotlin.jdk)
  testImplementation(libs.test.truth)
}
