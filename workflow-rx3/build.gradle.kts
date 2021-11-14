plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  api(project(":workflow-core"))
  api(Dependencies.Kotlin.Stdlib.jdk6)
  api(Dependencies.Kotlin.Coroutines.core)
  api(Dependencies.RxJava.rxjava3)

  implementation(Dependencies.Kotlin.Coroutines.rx3)

  testImplementation(project(":workflow-testing"))
  testImplementation(Dependencies.Kotlin.Test.jdk)
}
