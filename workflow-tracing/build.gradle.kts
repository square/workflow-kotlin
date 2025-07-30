plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
}
