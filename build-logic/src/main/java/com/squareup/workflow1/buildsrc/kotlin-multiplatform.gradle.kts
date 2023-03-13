package com.squareup.workflow1.buildsrc

import gradle.kotlin.dsl.accessors._8404891dd4f144be485a653edb312c9d.kotlin
import gradle.kotlin.dsl.accessors._8404891dd4f144be485a653edb312c9d.sourceSets
import org.gradle.kotlin.dsl.kotlin

plugins {
  kotlin("multiplatform")
}

extensions.getByType(JavaPluginExtension::class).apply {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.RequiresOptIn")
      }
    }
  }
}

project.kotlinCommonSettings(bomConfigurationName = "commonMainImplementation")
