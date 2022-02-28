# benchmark

This module contains benchmarks. Used to measure and help improve Workflow performance. These tests
should be run on physical devices.

## dungeon-benchmark

Currently this is the only benchmark included here. It is for the /samples/dungeon/app. This
includes a macrobenchmark [test](benchmarks/dungeon-benchmark/src/main/java/com/squareup/sample/dungeon/benchmark/WorkflowBaselineProfiles.kt)
that exercises this sample app (with UiAutomator) to collect a [baseline profile](https://developer.android.com/studio/profile/baselineprofiles).
After running this the `profile.txt` file can be taken off of the device and used directly in the
/src/main directory of the application. This will include the profile into the APK for guided
optimization at install time.

Better yet, the profile can be split up and added into each Workflow module's src directory so that
it will be included with all APKs built using Workflow (including 3rd party). To do this a java
program available in the android-x open source [code](https://github.com/androidx/androidx) is used
to split the profile based on src paths. That tool is found at
[androidx-main/frameworks/support/out/androidx/development/splitBaselineProfiles].

To split the profile paths for Workflow, the tool can be used as follows:

```bash
java -jar splitBaselineProfiles-all.jar --profilePath ~/path/to/profile.txt --checkoutPath ~/Development/workflow-kotlin/
```

This will create an output file separated by module and then also by package as a fallback. The
profile for each module can be added into its /src/main directory as `baseline-prof.txt`. Then on a
release build this will be included with the resulting APK/binary.

The other [test](benchmarks/dungeon-benchmark/src/main/java/com/squareup/sample/dungeon/benchmark/WorkflowBaselineBenchmark.kt)
is used to verify the results of including the baseline profiles on the startup time. This runs the
same scenario with and without forcing the use of the profiles. To force the use of profiles, the
`libs.androidx.profileinstaller` dependency is included into the app under profile (dungeon in this
case) for side-loading the profiles.
