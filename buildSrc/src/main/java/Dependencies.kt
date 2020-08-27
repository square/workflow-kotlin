/*
 * Copyright 2020 Square Inc.
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
@file:JvmName("Deps")

import java.util.Locale.US
import kotlin.reflect.full.declaredMembers

object Versions {
  const val compose = "1.0.0-alpha01"
  const val kotlin = "1.4.0"
  const val targetSdk = 29
  const val workflow = "0.28.0"
}

@Suppress("unused")
object Dependencies {
  const val android_gradle_plugin = "com.android.tools.build:gradle:4.2.0-alpha07"

  object AndroidX {
    const val appcompat = "androidx.appcompat:appcompat:1.1.0"
  }

  object Compose {
    const val foundation = "androidx.compose.foundation:foundation:${Versions.compose}"
    const val layout = "androidx.compose.foundation:foundation-layout:${Versions.compose}"
    const val material = "androidx.compose.material:material:${Versions.compose}"
    const val savedstate =
      "androidx.compose.runtime:runtime-saved-instance-state:${Versions.compose}"
    const val test = "androidx.ui:ui-test:${Versions.compose}"
    const val tooling = "androidx.ui:ui-tooling:${Versions.compose}"
  }

  const val timber = "com.jakewharton.timber:timber:4.7.1"

  object Kotlin {
    const val binaryCompatibilityValidatorPlugin =
      "org.jetbrains.kotlinx:binary-compatibility-validator:0.2.3"
    const val gradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val reflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}"
  }

  const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:0.10.0"
  const val mavenPublish = "com.vanniktech:gradle-maven-publish-plugin:0.11.1"
  const val ktlint = "org.jlleitschuh.gradle:ktlint-gradle:9.2.0"
  const val detekt = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.0.1"

  object Test {
    object AndroidX {
      const val junitExt = "androidx.test.ext:junit:1.1.1"
      const val runner = "androidx.test:runner:1.2.0"
      const val truthExt = "androidx.test.ext:truth:1.2.0"
      const val uiautomator = "androidx.test.uiautomator:uiautomator:2.2.0"

      object Espresso {
        const val core = "androidx.test.espresso:espresso-core:3.2.0"
      }
    }

    const val junit = "junit:junit:4.13"
    const val kotlin = "org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}"
    const val truth = "com.google.truth:truth:1.0.1"
  }

  object Workflow {
    const val core = "com.squareup.workflow:workflow-core-jvm:${Versions.workflow}"
    const val runtime = "com.squareup.workflow:workflow-runtime-jvm:${Versions.workflow}"

    object UI {
      const val coreAndroid = "com.squareup.workflow:workflow-ui-core-android:${Versions.workflow}"
    }
  }
}

/**
 * Workaround to make [Dependencies] accessible from Groovy scripts. [path] is case-insensitive.
 *
 * ```
 * dependencies {
 *   implementation Deps.get("kotlin.stdlib.common")
 * }
 * ```
 */
@JvmName("get")
fun getDependencyFromGroovy(path: String): String = try {
  Dependencies.resolveObject(
      path.toLowerCase(US)
          .split(".")
  )
} catch (e: Throwable) {
  throw IllegalArgumentException("Error resolving dependency: $path", e)
}

private tailrec fun Any.resolveObject(pathParts: List<String>): String {
  require(pathParts.isNotEmpty())
  val klass = this::class

  if (pathParts.size == 1) {
    @Suppress("UNCHECKED_CAST")
    val member = klass.declaredMembers.single { it.name.toLowerCase(US) == pathParts.single() }
    return member.call() as String
  }

  val nestedKlasses = klass.nestedClasses
  val selectedKlass = nestedKlasses.single { it.simpleName!!.toLowerCase(US) == pathParts.first() }
  return selectedKlass.objectInstance!!.resolveObject(pathParts.subList(1, pathParts.size))
}
