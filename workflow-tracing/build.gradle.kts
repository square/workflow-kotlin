plugins {
  `kotlin-jvm`
  published
}

dependencies {
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  api(project(":trace-encoder"))
  api(project(":workflow-runtime"))

  compileOnly(libs.jetbrains.annotations)

  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.squareup.okio)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.kotlin)
}
