plugins {
  `java-library`
  `kotlin-jvm`
  id("kotlinx-serialization")
}

dependencies {
  implementation(project(":workflow-ui:core-common"))
  implementation(project(":workflow-core"))

  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.jdk8)

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
