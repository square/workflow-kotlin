plugins {
  `java-gradle-plugin`
  kotlin("jvm") version "2.1.21"
}

repositories {
  mavenCentral()
}

// Bundle the extraction script as a resource so the Gradle task can delegate to it.
// The script at scripts/extract-ai-rules.main.kts is the SINGLE implementation of
// the extraction logic — the Gradle task is just a wrapper that discovers classpath
// JARs and invokes the script.
sourceSets {
  main {
    resources {
      srcDir(rootProject.projectDir.resolve("../scripts"))
      include("extract-ai-rules.main.kts")
    }
  }
}

gradlePlugin {
  plugins {
    create("ai-rules-extract") {
      id = "ai-rules-extract"
      implementationClass = "com.squareup.workflow1.buildsrc.airules.AiRulesExtractPlugin"
    }
  }
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
