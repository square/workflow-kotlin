plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:backstack-common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))
}
