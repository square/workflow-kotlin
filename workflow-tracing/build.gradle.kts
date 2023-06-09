plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  api(project(":trace-encoder"))
  api(project(":workflow-core"))
  api(project(":workflow-runtime"))

  compileOnly(libs.jetbrains.annotations)

  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.squareup.okio)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
}
