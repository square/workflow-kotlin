plugins {
  `kotlin-jvm`
  published
}

dependencies {
  api(libs.kotlin.stdlib)

  api(project(":workflow-ui:core-common"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
