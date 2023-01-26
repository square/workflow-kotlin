plugins {
  `kotlin-jvm`
}

dependencies {
  api(project(":workflow-ui:core-common"))

  implementation(libs.kotlin.stdlib)

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
