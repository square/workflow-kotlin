plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":workflow-core"))

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.test.kotlin.jdk)
  testImplementation(libs.test.hamcrestCore)
  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)
}
