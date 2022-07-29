plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(libs.kotlin.jdk6)

  implementation(project(":workflow-core"))
  implementation(project(":workflow-ui:container-common"))

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
