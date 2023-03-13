package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import com.squareup.workflow1.buildsrc.internal.version
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidDefaultsPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.extensions.configure(TestedExtension::class.java) { testedExtension ->

      testedExtension.compileSdkVersion(target.libsCatalog.version("compileSdk").toInt())

      testedExtension.compileOptions { compileOptions ->

        compileOptions.sourceCompatibility = VERSION_1_8
        compileOptions.targetCompatibility = VERSION_1_8
      }

      testedExtension.defaultConfig { defaultConfig ->
        defaultConfig.minSdk = target.libsCatalog.version("minSdk").toInt()
        defaultConfig.targetSdk = target.libsCatalog.version("targetSdk").toInt()
        defaultConfig.versionCode = 1
        defaultConfig.versionName = "1.0"
      }

      testedExtension.testOptions { testOptions ->
        testOptions.unitTests { unitTestOptions ->

          unitTestOptions.isReturnDefaultValues = true
          unitTestOptions.isIncludeAndroidResources = true
        }
      }

      @Suppress("UnstableApiUsage")
      testedExtension.buildFeatures.buildConfig = false

      // See https://github.com/Kotlin/kotlinx.coroutines/issues/1064#issuecomment-479412940
      @Suppress("UnstableApiUsage")
      testedExtension.packagingOptions { packagingOptions ->
        packagingOptions.resources.excludes.add("META-INF/atomicfu.kotlin_module")
        packagingOptions.resources.excludes.add("META-INF/common.kotlin_module")
        packagingOptions.resources.excludes.add("META-INF/android_debug.kotlin_module")
        packagingOptions.resources.excludes.add("META-INF/android_release.kotlin_module")
        packagingOptions.resources.excludes.add("META-INF/AL2.0")
        packagingOptions.resources.excludes.add("META-INF/LGPL2.1")
      }
    }
  }
}
