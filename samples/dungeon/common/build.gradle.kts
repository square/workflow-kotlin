plugins {
  id("kotlin-jvm")
  id("kotlinx-serialization")
}

dependencies {
  api(libs.squareup.okio)

  api(project(":workflow-core"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.kotlin.jdk8)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
