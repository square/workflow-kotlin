plugins {
  `kotlin-jvm`
  published
}

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.reactivestreams)
  api(libs.rxjava2.rxjava)

  api(project(":workflow-core"))

  compileOnly(libs.jetbrains.annotations)

  implementation(libs.kotlinx.coroutines.rx2)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)

  testImplementation(project(":workflow-testing"))
}
