import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.libsCatalog
import com.squareup.workflow1.version

configure<TestedExtension> {
  compileSdkVersion(libsCatalog.version("compileSdk").toInt())

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  defaultConfig {
    minSdk = libsCatalog.version("minSdk").toInt()
    targetSdk = libsCatalog.version("targetSdk").toInt()
    versionCode = 1
    versionName = "1.0"
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
  }

  buildFeatures.buildConfig = false

  // See https://github.com/Kotlin/kotlinx.coroutines/issues/1064#issuecomment-479412940
  @Suppress("UnstableApiUsage")
  packagingOptions {
    resources.excludes.add("META-INF/atomicfu.kotlin_module")
    resources.excludes.add("META-INF/common.kotlin_module")
    resources.excludes.add("META-INF/android_debug.kotlin_module")
    resources.excludes.add("META-INF/android_release.kotlin_module")
    resources.excludes.add("META-INF/AL2.0")
    resources.excludes.add("META-INF/LGPL2.1")
  }
}
