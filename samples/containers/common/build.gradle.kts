plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-core"))

  implementation(Dependencies.Kotlin.Stdlib.jdk6)

  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Test.hamcrestCore)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(project(":workflow-testing"))
}
