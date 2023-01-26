package com.squareup.workflow1.buildsrc

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.kpm.external.project
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

fun KotlinMultiplatformExtension.iosWithSimulatorArm64(target: Project) {
  ios()
  iosSimulatorArm64()

  targets.withType(KotlinNativeTargetWithSimulatorTests::class.java) {
    it.testRuns.configureEach {
      // Kotlin 1.7.2x is hard-coded to "iPhone 13", which is no longer included by default in Xcode
      it.deviceId = target.properties["workflow.iosSimulatorName"] as? String ?: "iPhone 14"
    }
  }

  sourceSets.getByName("iosSimulatorArm64Main") {
    it.dependsOn(sourceSets.getByName("iosMain"))
  }
  sourceSets.getByName("iosSimulatorArm64Test") {
    it.dependsOn(sourceSets.getByName("iosTest"))
  }
}
