# Module workflow-runtime

This module contains the core APIs and logic for running workflows.

## Kotlin Multiplatform

This module is a Kotlin Multiplatform module. The targets currently included for build and test
are `jvm`, `android`, `ios`, and `iosSimulatorSimulatorArm64`. If you are having issues with the
tests, ensure you have the correct version of XCode installed and can launch a simulator as it's
specified in the gradle build file (Currently iPhone 14).

You can also choose to specify your targets for build and test with the property `workflow.targets`
as either `kmp`, `jvm`, `android`, `ios`, `js`. The default is `kmp` (all the targets). Set this in
your global `~/.gradle/gradle.properties` or specify the property in your gradle command, e.g.:

```bash
./gradlew build -Pworkflow.targets=jvm
```
