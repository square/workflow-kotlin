plugins {
  `kotlin-jvm`
  published
}

dependencies {
  api(libs.kotlin.stdlib)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.okio)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
