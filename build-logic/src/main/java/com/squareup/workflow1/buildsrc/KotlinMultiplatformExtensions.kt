package com.squareup.workflow1.buildsrc

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

fun KotlinMultiplatformExtension.iosWithSimulatorArm64(target: Project) {
  ios()
  iosSimulatorArm64()

  sourceSets.getByName("iosSimulatorArm64Main") {
    it.dependsOn(sourceSets.getByName("iosMain"))
  }
  sourceSets.getByName("iosSimulatorArm64Test") {
    it.dependsOn(sourceSets.getByName("iosTest"))
  }
}
