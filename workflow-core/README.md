# Module workflow-core

This module contains the core workflow APIs.

## Kotlin Multiplatform

This module is a Kotlin Multiplatform module. The targets currently included for build and test
are `jvm`, `ios`, and `iosSimulatorSimulatorArm64`. If you are having issues with the tests,
ensure you have the correct version of XCode installed and can launch a simulator as it's specified
in the gradle build file (Currently iPhone 14).

You can also choose to specify your targets for build and test with the property `workflow.targets`
as either `kmp`, `jvm`, `ios`, `js`. The default is `kmp` (all the targets). Set this in your
global `~/.gradle/gradle.properties` or specify the property in your gradle command, e.g.:

```bash
./gradlew build -Pworkflow.targets=jvm
```

## Notes on Dispatchers

_Dispatchers control what threads/pools coroutines are run on. [See here for more information.][1]_

Most workflow code will default to, or force, the use of the `Unconfined` dispatcher. The reason for
this is that the workflow code itself is side-effect-free and doesn't care what dispatcher it runs
on, so using the `Unconfined` dispatcher eliminates the overhead of having to actually dispatch
anything unnecessarily. When required, your code can use `withContext` to use a different
dispatcher. For example, if a workflow is making a network call, it should wrap the call in
`withContext(IO)`.

 [1]: https://kotlinlang.org/docs/reference/coroutines/coroutine-context-and-dispatchers.html
