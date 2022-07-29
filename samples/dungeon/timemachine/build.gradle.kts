plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(libs.kotlin.jdk8)

  implementation(project(":workflow-core"))

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
