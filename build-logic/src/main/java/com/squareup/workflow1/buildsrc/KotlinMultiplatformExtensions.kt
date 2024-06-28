package com.squareup.workflow1.buildsrc

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

fun KotlinMultiplatformExtension.iosTargets(): List<KotlinNativeTarget> {
  return listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  )
}
