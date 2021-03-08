@file:JvmName("Deps")

import java.util.Locale.US
import kotlin.reflect.full.declaredMembers

object Versions {
  const val targetSdk = 29
}

@Suppress("unused")
object Dependencies {
  const val android_gradle_plugin = "com.android.tools.build:gradle:_"

  object AndroidX {
    const val activity = "androidx.activity:activity:_"
    const val activityKtx = "androidx.activity:activity-ktx:_"
    const val appcompat = "androidx.appcompat:appcompat:_"
    const val constraint_layout = "androidx.constraintlayout:constraintlayout:_"
    const val fragment = "androidx.fragment:fragment:_"
    const val fragmentKtx = "androidx.fragment:fragment-ktx:_"
    const val gridlayout = "androidx.gridlayout:gridlayout:_"

    object Lifecycle {
      const val ktx = "androidx.lifecycle:lifecycle-runtime-ktx:_"
      const val viewModelKtx = "androidx.lifecycle:lifecycle-viewmodel-ktx:_"
      const val viewModelSavedState = "androidx.lifecycle:lifecycle-viewmodel-savedstate:_"
    }

    // Note that we're not using the actual androidx material dep yet, it's still alpha.
    const val material = "com.google.android.material:material:_"
    const val recyclerview = "androidx.recyclerview:recyclerview:_"

    // Note that we are *not* using lifecycle-viewmodel-savedstate, which at this
    // writing is still in beta and still fixing bad bugs. Probably we'll never bother to,
    // it doesn't really add value for us.
    const val savedstate = "androidx.savedstate:savedstate:_"
    const val transition = "androidx.transition:transition:_"
    const val viewbinding = "androidx.databinding:viewbinding:_"
  }

  const val cycler = "com.squareup.cycler:cycler:_"

  // Required for Dungeon Crawler sample.
  const val desugar_jdk_libs = "com.android.tools:desugar_jdk_libs:_"
  const val moshi = "com.squareup.moshi:moshi:_"
  const val radiography = "com.squareup.radiography:radiography:_"
  const val rxandroid2 = "io.reactivex.rxjava2:rxandroid:_"
  const val seismic = "com.squareup:seismic:_"
  const val timber = "com.jakewharton.timber:timber:_"

  object Kotlin {
    const val binaryCompatibilityValidatorPlugin =
      "org.jetbrains.kotlinx:binary-compatibility-validator:_"
    const val gradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:_"

    object Stdlib {
      const val common = "org.jetbrains.kotlin:kotlin-stdlib-common"
      const val jdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
      const val jdk7 = "org.jetbrains.kotlin:kotlin-stdlib-jdk7"
      const val jdk6 = "org.jetbrains.kotlin:kotlin-stdlib"
    }

    object Coroutines {
      const val android = "org.jetbrains.kotlinx:kotlinx-coroutines-android:_"
      const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:_"
      const val rx2 = "org.jetbrains.kotlinx:kotlinx-coroutines-rx2:_"

      const val test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:_"
    }

    const val moshi = "com.squareup.moshi:moshi-kotlin:_"
    const val reflect = "org.jetbrains.kotlin:kotlin-reflect:_"

    object Serialization {
      const val gradlePlugin = "org.jetbrains.kotlin:kotlin-serialization:_"
      const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:_"
    }

    object Test {
      const val common = "org.jetbrains.kotlin:kotlin-test-common"
      const val annotations = "org.jetbrains.kotlin:kotlin-test-annotations-common"
      const val jdk = "org.jetbrains.kotlin:kotlin-test-junit"
      const val mockito = "com.nhaarman:mockito-kotlin-kt1.1:_"
      const val mockk = "io.mockk:mockk:_"
    }
  }

  const val dokka = "org.jetbrains.dokka:dokka-gradle-plugin:_"

  object Jmh {
    const val gradlePlugin = "me.champeau.gradle:jmh-gradle-plugin:_"
    const val core = "org.openjdk.jmh:jmh-core:_"
    const val generator = "org.openjdk.jmh:jmh-generator-annprocess:_"
  }

  const val mavenPublish = "com.vanniktech:gradle-maven-publish-plugin:_"
  const val ktlint = "org.jlleitschuh.gradle:ktlint-gradle:_"
  const val lanterna = "com.googlecode.lanterna:lanterna:_"
  const val detekt = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:_"
  const val okio = "com.squareup.okio:okio:_"

  object RxJava2 {
    const val rxjava2 = "io.reactivex.rxjava2:rxjava:_"
  }

  object Annotations {
    const val intellij = "org.jetbrains:annotations:_"
  }

  object Test {
    object AndroidX {
      object Espresso {
        const val core = "androidx.test.espresso:espresso-core:_"
        const val idlingResource = "androidx.test.espresso:espresso-idling-resource:_"
        const val intents = "androidx.test.espresso:espresso-intents:_"
      }

      const val junitExt = "androidx.test.ext:junit:_"
      const val runner = "androidx.test:runner:_"
      const val truthExt = "androidx.test.ext:truth:_"
      const val uiautomator = "androidx.test.uiautomator:uiautomator:_"
    }

    const val hamcrestCore = "org.hamcrest:hamcrest-core:_"
    const val junit = "junit:junit:_"
    const val mockito = "org.mockito:mockito-core:_"
    const val truth = "com.google.truth:truth:_"
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
