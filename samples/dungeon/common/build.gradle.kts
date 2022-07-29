plugins {
  `kotlin-jvm`
  id("kotlinx-serialization")
}

dependencies {
  implementation(libs.kotlin.jdk8)
  implementation(libs.kotlinx.serialization.json)

  implementation(project(":workflow-core"))
  implementation(project(":workflow-ui:core-common"))

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
