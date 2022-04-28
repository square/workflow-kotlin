# benchmarks

This module contains benchmarks. Used to measure and help improve Workflow performance. The
deterministic tests - such as those for render passes - can be run on any device (virtual or
otherwise), but the benchmarks should be run on physical devices, or the closest approximation
to physical devices you can get.

## Baseline Profiles

The sample apps can be used to extract 'baseline profiles' to improve code loading time after first
install. See [baseline profiles](https://developer.android.com/studio/profile/baselineprofiles).

We can use
[dungeon](dungeon-benchmark/src/main/java/com/squareup/sample/dungeon/benchmark/DungeonGatherBaselineProfile.kt)
or [performance-poetry](performance-poetry/complex-benchmark/src/main/java/com/squareup/benchmarks/performance/poetry/complex/benchmark/ComplexPoetryGatherBaseline.kt)
to gather profiles. Performance Poetry has more adaptability and better benchmarks at this time, so
focus on that.

After extracting the profile with one of those tests, the `profile.txt` file can be taken off of the
device and used directly in the /src/main directory of the application. This will include the
profile into the APK for guided optimization at install time.

Better yet, the profile can be split up and added into each Workflow module's src directory so that
it will be included with all APKs built using Workflow (including 3rd party). To do this a java
program, available in the Android-X open source [code](https://github.com/androidx/androidx), is
used to split the profile based on src paths. That tool is found at
[androidx-main/frameworks/support/out/androidx/development/splitBaselineProfiles].

To split the profile paths for Workflow, the tool can be used as follows:

```bash
java -jar splitBaselineProfiles-all.jar --profilePath ~/path/to/profile.txt --checkoutPath ~/Development/workflow-kotlin/
```

This will create an output file separated by module and then also by package as a fallback. The
profile for each module can be added into its /src/main directory as `baseline-prof.txt`. Then on a
release build this will be included with the resulting APK/binary.

## dungeon-benchmark

These are benchmarks for the [../samples/dungeon] app. Please instead use performance-poetry where
possible.

*PLEASE NOTE:* The dungeon app includes AI ghosts with random travel which can 'eat' the player,
ending the game with a dialog. This obviously makes these test non-deterministic. We used these
originally to exercise enough of Workflow for a baseline profile. We can avoid at least the dialog
popping to end the game by making the player survive with the following change to
[../samples/dungeon/common/src/main/java/com/squareup/sample/dungeon/Game.kt]:

```kotlin
val isPlayerEaten: Boolean get() = false // Never Eaten!
```

The [benchmark](dungeon-benchmark/src/main/java/com/squareup/sample/dungeon/benchmark/DungeonStartupBenchmark.kt)
is used to verify the results of including the baseline profiles on the startup time. This runs the
same scenario with and without forcing the use of the profiles. To force the use of profiles, the
`libs.androidx.profileinstaller` dependency is included into the app under profile (dungeon in this
case) for side-loading the profiles.

## performance-poetry

Module of code for performance testing related to poetry applications.

### complex-poetry

This application is a modified version of the samples/containers/app-poetry app which also uses the
common components in samples/containers/common and samples/containers/poetry. It modifies this
application to pass the Workflow
a [SimulatedPerfConfig.](performance-poetry/complex-poetry/src/main/java/com/squareup/benchmarks/performance/complex/poetry/instrumentation/SimulatedPerfConfig.kt)

In this case we specify that the app should be more 'complex' which adds delays into each of the
selections that are run by Worker's which then trigger a loading state that is handled by the
[MaybeLoadingGatekeeperWorkflow.](performance-poetry/complex-poetry/src/main/java/com/squareup/benchmarks/performance/complex/poetry/MaybeLoadingGatekeeperWorkflow.kt)

One benchmark that is included (and run in CI) as a UI test is the [RenderPassTest.](performance-poetry/complex-poetry/src/androidTest/java/com/squareup/benchmarks/performance/complex/poetry/RenderPassTest.kt)
This measures two things:

 1. The number of "Render Passes" that the Raven scenario triggers.
 1. The ratio of 'fresh renderings' to 'stale renderings' in the Raven scenario.

A rendering is 'fresh' if the node's state has changed. A rendering is 'stale' if its state is the
same. Note that Workflow is designed to to have cheap, idempotent renderings and the fresh rendering
ratio will never be 1.0 by design. However, if there are smells that have led to 'render churn' one
will see a very poor rendering ratio and we would like a way to track that and to test that it is
constant (or improving!).

This module includes an [instrumentation package](performance-poetry/complex-poetry/src/main/java/com/squareup/benchmarks/performance/complex/poetry/instrumentation)
that has two [WorkflowInterceptor]s that can count render passes or instrument perfetto Trace
sections, as well as data class for tracking those.

This module also includes a [robots package](performance-poetry/complex-poetry/src/main/java/com/squareup/benchmarks/performance/complex/poetry/robots)
that provides some utility helper 'robots' for the UiAutomator [androidx.test.uiautomator.UiDevice]
as well as scenarios specific to this Complex Poetry application.

### complex-benchmark

This is an Android Test module which hosts an application that can run androidx.macrobenchmarks.
See the kdoc on [ComplexPoetryBenchmarks.](performance-poetry/complex-benchmark/src/main/java/com/squareup/benchmarks/performance/complex/poetry/benchmark/ComplexPoetryBenchmarks.kt)

The results for this are stored in the same folder at [ComplexPoetryResults.txt.](performance-poetry/complex-benchmark/src/main/java/com/squareup/benchmarks/performance/complex/poetry/benchmark/ComplexPoetryResults.txt)
