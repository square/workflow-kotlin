plugins {
  `java-library`
  kotlin("jvm")
  id("kotlinx-serialization")
}

dependencies {
  implementation(project(":workflow-ui:core-common"))
  implementation(project(":workflow-core"))

  implementation(Dependencies.Kotlin.Serialization.json)
  implementation(Dependencies.Kotlin.Stdlib.jdk8)

  testImplementation(project(":workflow-testing"))
  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Test.truth)
}
