plugins {
  `kotlin-jvm`
  published
}

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.squareup.okio)

  api(project(":workflow-ui:core-common"))

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
