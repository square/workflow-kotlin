package com.squareup.workflow1.buildsrc.internal

import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.jvm.toolchain.JavaLanguageVersion

fun DependencyHandler.implementation(notation: Any) {
  add("implementation", notation)
}

fun DependencyHandler.androidTestImplementation(notation: Any) {
  add("androidTestImplementation", notation)
}

operator fun DependencyHandler.invoke(
  config: DependencyHandler.() -> Unit
): DependencyHandler = apply { config(this) }

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val Project.isRunningFromIde
  get() = properties["android.injected.invoked.from.ide"] == "true"

fun DependencyHandler.kotlin(
  module: String,
  version: String? = null
): Any = "org.jetbrains.kotlin:kotlin-$module${version?.let { ":$version" } ?: ""}"

/**
 * the jdk used in packaging
 *
 * "1.6", "1.8", "11", etc.
 */
val Project.javaTarget: String
  get() = libsCatalog.version("jdk-target")

/** `6`, `8`, `11`, etc. */
val Project.javaTargetInt: Int
  get() = javaTarget.substringAfterLast('.').toInt()

/**
 * Gradle's Java version enum.
 *
 * nb: Their class version is the jdk's integer value + 44.
 * For instance, Java 8's class version is 52.
 */
val Project.javaTargetVersion: JavaVersion
  get() = JavaVersion.forClassVersion(javaTargetInt + 44)

/**
 * the jdk used to build the project
 *
 * "1.6", "1.8", "11", etc.
 */
val Project.jdkToolchain: String
  get() = libsCatalog.version("jdk-toolchain")

/**
 * the jdk used to build the project
 *
 * "1.6", "1.8", "11", etc.
 */
val Project.javaLanguageVersion: JavaLanguageVersion
  get() = JavaLanguageVersion.of(jdkToolchain)

/**
 * the jdk used to build the project
 *
 * "1.6", "1.8", "11", etc.
 */
val Project.jdkToolchainInt: Int
  get() = jdkToolchain.substringAfterLast('.').toInt()
