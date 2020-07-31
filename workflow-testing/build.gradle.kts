/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))
  api(Dependencies.Kotlin.Stdlib.jdk7)

  implementation(project(":internal-testing-utils"))
  implementation(Dependencies.Kotlin.Coroutines.test)
  implementation(Dependencies.Kotlin.reflect)

  testImplementation(Dependencies.Kotlin.Test.jdk)
}
