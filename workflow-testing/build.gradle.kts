@file:Suppress("SuspiciousCollectionReassignment")

plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
// TODO We had this configuration previously to expose internals of workflow-core to
//  workflow-testing. However, it doesn't appear to work with multiplatform, so currently an opt-in
//  annotation is used instead.

    // // Configure friend paths so the testing module can access internal declarations from the
    // // following modules. Note that the IntelliJ Kotlin plugin won't be aware of this configuration
    // // so it will still complain about internal accesses across modules, but they will actually
    // // compile just fine. See https://youtrack.jetbrains.com/issue/KT-20760.
    // val friendModules = listOf(
    //     project(":workflow-core")
    // )
    // val friendClassDirs = friendModules.flatMap { project ->
    //   project.sourceSets["main"].output.classesDirs.toList()
    // }
    // freeCompilerArgs += friendClassDirs.map { "-Xfriend-paths=$it" }

    freeCompilerArgs += "-Xopt-in=com.squareup.workflow1.InternalWorkflowApi"
  }
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))
  api(Dependencies.Kotlin.Stdlib.jdk7)

  implementation(project(":internal-testing-utils"))
  implementation(Dependencies.Kotlin.Coroutines.core)
  implementation(Dependencies.Kotlin.Coroutines.test)
  implementation(Dependencies.Kotlin.reflect)

  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Kotlin.Test.mockito)
  testImplementation(Dependencies.Kotlin.Test.mockk)
}
