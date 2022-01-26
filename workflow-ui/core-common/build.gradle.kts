plugins {
  `java-library`
  `kotlin-jvm`
  id("org.jetbrains.dokka")
  publish
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.squareup.okio)
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
