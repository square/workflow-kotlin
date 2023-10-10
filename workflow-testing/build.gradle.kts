@file:Suppress("SuspiciousCollectionReassignment")

import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
  id("java-library")
  id("kotlin-jvm")
  id("published")
}

val friendPathsConfiguration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  attributes {
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    attribute(
      TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
      objects.named(TargetJvmEnvironment.STANDARD_JVM)
    )
    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    // Configure friend paths so the testing module can access internal declarations from the
    // following modules. Note that the IntelliJ Kotlin plugin won't be aware of this configuration
    // so it will still complain about internal accesses across modules, but they will actually
    // compile just fine. See https://youtrack.jetbrains.com/issue/KT-20760.
    friendPaths.from(friendPathsConfiguration)
  }
}

dependencies {
  api(libs.kotlin.jdk7)
  api(libs.kotlinx.coroutines.test)

  friendPathsConfiguration(project(":workflow-core"))
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