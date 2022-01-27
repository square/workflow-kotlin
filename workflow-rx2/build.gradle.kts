plugins {
  `java-library`
`kotlin-jvm`
  publish
}

dependencies {
  compileOnly(libs.jetbrains.annotations)

  api(project(":workflow-core"))
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.rxjava2.rxjava)

  implementation(libs.kotlinx.coroutines.rx2)

  testImplementation(project(":workflow-testing"))
  testImplementation(libs.kotlin.test.jdk)
}
