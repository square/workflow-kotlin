@file:Suppress("SuspiciousCollectionReassignment")

plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
  `maven-publish`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    // Configure friend paths so the testing module can access internal declarations from the
    // following modules. Note that the IntelliJ Kotlin plugin won't be aware of this configuration
    // so it will still complain about internal accesses across modules, but they will actually
    // compile just fine. See https://youtrack.jetbrains.com/issue/KT-20760.
    val friendModules = listOf(
        project(":workflow-core")
    )
    val friendClassDirs = friendModules.flatMap { project ->
      project.sourceSets["main"].output.classesDirs.toList()
    }
    freeCompilerArgs += friendClassDirs.map { "-Xfriend-paths=$it" }
  }
}

dependencies {
  compileOnly(libs.jetbrains.annotations)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))
  api(libs.kotlin.jdk7)

  implementation(project(":internal-testing-utils"))
  implementation(libs.kotlinx.coroutines.test)
  implementation(libs.kotlin.reflect)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockk)
}
