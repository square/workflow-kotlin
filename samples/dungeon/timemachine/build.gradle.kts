plugins {
  id("kotlin-jvm")
}

dependencies {
  api(project(":workflow-core"))

  implementation(libs.kotlin.jdk8)

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
