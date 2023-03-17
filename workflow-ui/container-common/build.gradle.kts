plugins {
  `kotlin-jvm`
}

square {
  published(
    artifactId = "workflow-ui-container-common-jvm",
    name = "Workflow UI Container"
  )
}

dependencies {
  api(libs.kotlin.jdk6)

  api(project(":workflow-ui:core-common"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
