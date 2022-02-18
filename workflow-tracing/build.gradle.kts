plugins {
  `kotlin-jvm`
  id("com.vanniktech.maven.publish")
}



dependencies {
  compileOnly(libs.jetbrains.annotations)

  api(project(":trace-encoder"))
  api(project(":workflow-runtime"))
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  implementation(libs.squareup.okio)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.squareup.moshi)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.kotlin)
}
