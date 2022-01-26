plugins {
  kotlin("jvm")
  publish
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
