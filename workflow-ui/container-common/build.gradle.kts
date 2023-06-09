plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  api(libs.kotlin.jdk6)

  api(project(":workflow-ui:core-common"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
