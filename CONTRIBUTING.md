Contributing
============

If you would like to contribute code to Workflow you can do so through GitHub by
forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions
and style in order to keep the code as readable as possible. Please also make
sure your code compiles by running `./gradlew clean build`. If you're using IntelliJ IDEA,
we use [Square's code style definitions][2].

Building
--------

You need **JDK 17 or newer** to run Gradle, and **JDK 21 installed** for the build to compile
against.

The build uses three separate JDK versions, configured in `gradle/libs.versions.toml`:

| Version | Purpose |
|---|---|
| `jdk-target` (11) | Bytecode version published to consumers |
| `jdk-toolchain` (21) | JDK the libraries are compiled with, via a Gradle toolchain |
| `jdk-buildLogic` (17) | JDK `build-logic` is compiled with — the floor for your Gradle daemon |

Gradle forks a JDK 21 toolchain for compilation, so your `JAVA_HOME` does not need to be 21 — but a
JDK 21 must be installed somewhere Gradle can [auto-detect][3] it. If you see
`Cannot find a Java installation ... matching languageVersion=21`, install a JDK 21.

 [3]: https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection

Before your code can be accepted into the project you must also sign the
[Individual Contributor License Agreement (CLA)][1].

 [1]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
 [2]: https://github.com/square/java-code-styles
