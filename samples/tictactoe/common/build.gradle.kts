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

  implementation(Dependencies.Kotlin.Stdlib.jdk6)

  testImplementation(Dependencies.Test.hamcrestCore)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(project(":workflow-testing"))
}
