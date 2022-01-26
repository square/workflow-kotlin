plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
  publish
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
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
