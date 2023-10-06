@file:Suppress("SuspiciousCollectionReassignment")

plugins {
  id("java-library")
  id("kotlin-jvm")
  id("published")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    // Configure friend paths so the testing module can access internal declarations from the
    // following modules. Note that the IntelliJ Kotlin plugin won't be aware of this configuration
    // so it will still complain about internal accesses across modules, but they will actually
    // compile just fine. See https://youtrack.jetbrains.com/issue/KT-20760.
    val friendModule = project(":workflow-core")

    // Pointing to jar instead of classes dir since :workflow-core is a multiplatform project.
    friendPaths.from(
      provider {
        friendModule.configurations.getByName("jvmRuntimeElements").artifacts.first().file
      }
    )
  }
}

dependencies {
  api(libs.kotlin.jdk7)
  api(libs.kotlinx.coroutines.test)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))

  compileOnly(libs.jetbrains.annotations)

  implementation(libs.kotlin.reflect)
  implementation(libs.turbine)

  implementation(project(":internal-testing-utils"))
  implementation(project(":workflow-config:config-jvm"))

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockk)
}
