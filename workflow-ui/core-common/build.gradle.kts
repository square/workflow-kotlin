plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.okio)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.truth)
  testImplementation(libs.turbine)
}
