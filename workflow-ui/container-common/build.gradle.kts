plugins {
  `java-library`
`kotlin-jvm`
  publish
}

dependencies {
  api(project(":workflow-ui:core-common"))
  api(libs.kotlin.jdk6)
  api(libs.squareup.okio)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
