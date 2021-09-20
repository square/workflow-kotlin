plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-rx2"))
  implementation(Dependencies.Kotlin.Stdlib.jdk6)
}
