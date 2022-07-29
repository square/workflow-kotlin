plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(libs.kotlin.jdk6)

  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))
  implementation(project(":workflow-ui:container-common"))

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
