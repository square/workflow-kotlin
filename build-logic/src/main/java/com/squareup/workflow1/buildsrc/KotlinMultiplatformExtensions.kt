package com.squareup.workflow1.buildsrc

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.iosWithSimulatorArm64() {
  iosX64()
  iosArm64()
  iosSimulatorArm64()
}
