@file:JvmName("Deps")

import java.util.Locale.US
import kotlin.reflect.full.declaredMembers

object Versions {
  const val targetSdk = 29
}

@Suppress("unused")
object Dependencies {
  const val android_gradle_plugin = "com.android.tools.build:gradle:7.0.0"

  object AndroidX {
    const val activity = "androidx.activity:activity:1.2.3"
    const val activityKtx = "androidx.activity:activity-ktx:1.2.3"
    const val appcompat = "androidx.appcompat:appcompat:1.3.0"

    object Compose {
      const val foundation = "androidx.compose.foundation:foundation:1.0.0-alpha12"
      const val ui = "androidx.compose.ui:ui:1.0.0-alpha12"
    }

    const val constraint_layout = "androidx.constraintlayout:constraintlayout:2.1.0-rc01"
    const val fragment = "androidx.fragment:fragment:1.3.5"
    const val fragmentKtx = "androidx.fragment:fragment-ktx:1.3.5"
    const val gridlayout = "androidx.gridlayout:gridlayout:1.0.0"

    object Lifecycle {
      const val ktx = "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1"
      const val viewModelKtx = "androidx.lifecycle:lifecycle-viewmodel-ktx:2.3.1"
      const val viewModelSavedState = "androidx.lifecycle:lifecycle-viewmodel-savedstate:1.1.0"
    }

    // Note that we're not using the actual androidx material dep yet, it's still alpha.
    const val material = "com.google.android.material:material:1.3.0"
    const val recyclerview = "androidx.recyclerview:recyclerview:1.2.1"

    // Note that we are *not* using lifecycle-viewmodel-savedstate, which at this
    // writing is still in beta and still fixing bad bugs. Probably we'll never bother to,
    // it doesn't really add value for us.
    const val savedstate = "androidx.savedstate:savedstate:1.1.0"
    const val transition = "androidx.transition:transition:1.4.1"
    const val viewbinding = "androidx.databinding:viewbinding:4.2.1"
  }

  const val cycler = "com.squareup.cycler:cycler:0.1.9"

  // Required for Dungeon Crawler sample.
  const val desugar_jdk_libs = "com.android.tools:desugar_jdk_libs:1.1.5"
  const val radiography = "com.squareup.radiography:radiography:2.3.0"
  const val rxandroid2 = "io.reactivex.rxjava2:rxandroid:2.1.1"
  const val seismic = "com.squareup:seismic:1.0.2"
  const val timber = "com.jakewharton.timber:timber:4.7.1"

  object Moshi {
    const val adapters = "com.squareup.moshi:moshi-adapters:1.12.0"
    const val codeGen = "com.squareup.moshi:moshi-kotlin-codegen:1.12.0"
    const val moshi = "com.squareup.moshi:moshi:1.12.0"
  }

  object Kotlin {
    const val binaryCompatibilityValidatorPlugin =
      "org.jetbrains.kotlinx:binary-compatibility-validator:0.6.0"
    const val gradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0"

    object Stdlib {
      const val common = "org.jetbrains.kotlin:kotlin-stdlib-common"
      const val jdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
      const val jdk7 = "org.jetbrains.kotlin:kotlin-stdlib-jdk7"
      const val jdk6 = "org.jetbrains.kotlin:kotlin-stdlib"
    }

    object Coroutines {
      const val android = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0"
      const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0"
      const val rx2 = "org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.5.0"

      const val test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.0"
    }

    const val reflect = "org.jetbrains.kotlin:kotlin-reflect:1.5.0"

    object Serialization {
      const val gradlePlugin = "org.jetbrains.kotlin:kotlin-serialization:1.5.0"
      const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1"
    }

    object Test {
      const val common = "org.jetbrains.kotlin:kotlin-test-common"
      const val annotations = "org.jetbrains.kotlin:kotlin-test-annotations-common"
      const val jdk = "org.jetbrains.kotlin:kotlin-test-junit"
      const val mockito = "com.nhaarman:mockito-kotlin-kt1.1:1.6.0"
      const val mockk = "io.mockk:mockk:1.11.0"
    }
  }

  const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:1.4.32"

  object Jmh {
    const val gradlePlugin = "me.champeau.gradle:jmh-gradle-plugin:0.5.3"
    const val core = "org.openjdk.jmh:jmh-core:1.32"
    const val generator = "org.openjdk.jmh:jmh-generator-annprocess:1.32"
  }

  const val mavenPublish = "com.vanniktech:gradle-maven-publish-plugin:0.16.0"
  const val ktlint = "org.jlleitschuh.gradle:ktlint-gradle:10.1.0"
  const val lanterna = "com.googlecode.lanterna:lanterna:3.1.1"
  const val okio = "com.squareup.okio:okio:2.10.0"

  object RxJava2 {
    const val rxjava2 = "io.reactivex.rxjava2:rxjava:2.2.21"
  }

  object Annotations {
    const val intellij = "org.jetbrains:annotations:19.0.0"
  }

  object Test {
    object AndroidX {
      const val compose = "androidx.compose.ui:ui-test-junit4:1.0.0-alpha12"
      const val core = "androidx.test:core:1.3.0"

      object Espresso {
        const val core = "androidx.test.espresso:espresso-core:3.3.0"
        const val idlingResource = "androidx.test.espresso:espresso-idling-resource:3.3.0"
        const val intents = "androidx.test.espresso:espresso-intents:3.3.0"
      }

      const val junitExt = "androidx.test.ext:junit:1.1.2"
      const val runner = "androidx.test:runner:1.3.0"
      const val truthExt = "androidx.test.ext:truth:1.3.0"
      const val uiautomator = "androidx.test.uiautomator:uiautomator:2.2.0"
    }

    const val hamcrestCore = "org.hamcrest:hamcrest-core:2.2"
    const val junit = "junit:junit:4.13.2"
    const val mockito = "org.mockito:mockito-core:3.3.3"
    const val robolectric = "org.robolectric:robolectric:4.5.1"
    const val truth = "com.google.truth:truth:1.1.3"
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
fun getDependencyFromGroovy(path: String): String = Dependencies.resolveObject(
    path.toLowerCase(US)
        .split(".")
)

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
