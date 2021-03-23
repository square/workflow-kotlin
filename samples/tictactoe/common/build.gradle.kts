plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:backstack-common"))
  implementation(project(":workflow-ui:modal-common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.test.hamcrestCore)
  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)
}
