plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-core"))

  implementation(libs.kotlin.jdk6)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(project(":workflow-testing"))
}
