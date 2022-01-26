plugins {
  `java-library`
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.vanniktech.maven.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)
  compileOnly(Dependencies.Moshi.codeGen)

  ksp(Dependencies.Moshi.codeGen)

  api(Dependencies.Kotlin.Stdlib.jdk8)
  api(Dependencies.Kotlin.Coroutines.core)

  implementation(Dependencies.Moshi.adapters)
  implementation(Dependencies.Moshi.moshi)

  testImplementation(Dependencies.Kotlin.Test.jdk)
}
