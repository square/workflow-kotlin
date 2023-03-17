plugins {
  `kotlin-jvm`
}

square {
  published(
    artifactId = "workflow-internal-testing-utils",
    name = "Workflow internal testing utilities"
  )
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
