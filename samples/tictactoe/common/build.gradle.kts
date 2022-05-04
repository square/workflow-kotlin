plugins {
  `kotlin-jvm`
}

dependencies {
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))

  implementation(libs.kotlin.jdk6)

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(project(":workflow-testing"))
}
