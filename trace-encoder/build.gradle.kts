plugins {
  `java-library`
  kotlin("jvm")
  kotlin("kapt")
  id("com.vanniktech.maven.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(libs.annotations.intellij)
  compileOnly(libs.moshi.codeGen)

  kapt(libs.moshi.codeGen)

  api(libs.kotlin.jdk8)
  api(libs.kotlin.coroutines.core)

  implementation(libs.moshi.adapters)
  implementation(libs.moshi.moshi)

  testImplementation(libs.test.kotlin.jdk)
}
