plugins {
  `kotlin-jvm`
}

square {
  published(
    artifactId = "workflow-ui-core-common-jvm",
    name = "Workflow UI Core"
  )
}

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.okio)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
