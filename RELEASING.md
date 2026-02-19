# Releasing workflow

## Production Releases

---
1. Pull a fresh copy of `origin/main` and tags.
   ```bash
   git fetch --tags
   ```

1. Create a release branch off of `origin/main` (e.g. `release-v0.1`). (If this is a patch release, re-use the existing release branch if it still exists, or else check out a new one from the appropriate tag.)
   ```bash
   git co -b release-v0.1 origin/main
   ```

1. Confirm that the kotlin build is green before committing any changes.
   Takes about three minutes on an M3 Macbook Pro.
   (Note we exclude benchmarks, but you can check those too!)
   ```bash
   ./gradlew build && ./gradlew connectedCheck -x :benchmarks:dungeon-benchmark:connectedCheck -x :benchmarks:performance-poetry:complex-benchmark:connectedCheck -x  :benchmarks:performance-poetry:complex-poetry:connectedDebugAndroidTest -x :samples:todo-android:app:connectedDebugAndroidTest -x :benchmarks:runtime-microbenchmark:connectedReleaseAndroidTest
   ```
   NOTE: If you have any unexpected errors in the build or tests and they are related to non-jvm
   targets you may need to update your XCode or other iOS tools. See note in the workflow-core and
   workflow-runtime modules. Alternatively you can specify only the target you care about (while
   developing - do not do this for actual releases) with the property `workflow.targets` which is
   set to any of `kmp`, `jvm`, `ios`, or `js`.

1. In `gradle.properties`, remove the `-SNAPSHOT` suffix from the `VERSION_NAME` property.
   E.g. `VERSION_NAME=0.1.0`

1. Create a commit and tag the commit with the version number:
   ```bash
   git commit -am "Releasing v0.1.0."
   git tag v0.1.0
   ```

1. Push the release branch and tag to the repository:
   ```bash
   git push -u --tags
   ```

1. Run the `Publish Release` action in the [GitHub Actions](https://github.com/square/workflow-kotlin/actions/workflows/publish-release.yml) page by clicking "run workflow" on the right. Select the appropriate tag.

1. Wait for that publishing job to succeed.

1. Back on your local machine, bump the version
   - **Kotlin:** Update the `VERSION_NAME` property in `gradle.properties` to the new
     snapshot version, e.g. `VERSION_NAME=0.2.0-SNAPSHOT`.
   - **Kotlin:** Update the `LAST_PUBLISHED_VERSION_NAME` property in `gradle.properties` to the new
     version you just published, e.g. `LAST_PUBLISHED_VERSION_NAME=0.1.0`.

1. Commit the new snapshot version:
   ```bash
   git commit -am "Finish releasing v0.1.0."
   ```

1. Push your release branch again:
   ```bash
   git push origin release-v0.1
   ```

1. Merge the release branch into main and push again:
   ```bash
   git co main
   git reset --hard origin/main
   git merge --no-ff release-v0.1
   git push origin main
   ```

1. Create the release on GitHub:
   1. Go to the [Releases](https://github.com/square/workflow-kotlin/releases) page for the GitHub
      project.
   1. Click _Draft a new release_.
   1. Enter the tag name you just pushed.
   1. Click _Auto-generate release notes_. 
      - Edit the generated notes if you feel the need.
      - See [this page](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes) if you have an itch to customize how our notes are generated.
   1. If this is a pre-release version, check the pre-release box.
   1. Hit "Publish release".

1. If this was a fix release, merge changes to the main branch:
   ```bash
   git checkout main
   git pull
   git merge --no-ff v0.1-fixes
   # Resolve conflicts. Accept main's versions of gradle.properties and podspecs.
   git push origin main
   ```

1. Publish the documentation website, https://github.com/square/workflow
   1. Run the [Publish Documentation Site action](https://github.com/square/workflow/actions/workflows/update-docs.yml), providing a personal branch name in the last field (e.g. `yourname/kotlin-v0.1.0`).
   1. Pull the created branch and merge it into `gh-pages`
      1. `git fetch --all`
      1. `git co gh-pages`
      1. `git merge --no-ff origin/yourname/kotlin-v0.1.0`
      1. `git push origin gh-pages`

1. Once Maven artifacts are synced, update the workflow version used by the tutorial in
   `samples/tutorial/build.gradle`.

### Validating Markdown

Since all of our high-level documentation is written in Markdown, we run a linter in CI to ensure
we use consistent formatting. Lint errors will fail your PR builds, so to run locally, install
[markdownlint](https://github.com/markdownlint/markdownlint):

```bash
gem install mdl
```

Run the linter using the `lint_docs.sh`:

```bash
./lint_docs.sh
```

Rules can be configured by editing `.markdownlint.rb`.

---

## Notes

### Development

To build and install the current version to your local Maven repository (`~/.m2`), run:

```bash
./gradlew clean publishToMavenLocal
```

### Deploying

#### Configuration

In order to deploy artifacts to `central.sonatype.org`, you'll need to provide
your credentials via these two properties in your private Gradle properties
file(`~/.gradle/gradle.properties`).

```
mavenCentralUsername=<username>
mavenCentralPassword=<password>
```

In order to sign you'll need to specify your GPG key config in your private
Gradle properties file(`~/.gradle/gradle.properties`).

```
signing.keyId=<keyid>
signing.password=<password>
signing.secretKeyRingFile=<path/to/secring.gpg>
```

If this is your first time for either, the following one time steps need
to be performed:

1. Sign up for a Sonatype JIRA account.
1. Generate a GPG key (if you don't already have one). [Instructions](https://central.sonatype.org/publish/requirements/gpg/#generating-a-key-pair).
1. Distribute the GPG key to public servers. [Instructions](https://central.sonatype.org/publish/requirements/gpg/#distributing-your-public-key).
1. Get access to deploy under 'com.squareup' from Sonatype.

#### Snapshot Releases

Double-check that `gradle.properties` correctly contains the `-SNAPSHOT` suffix, then upload
snapshot artifacts to Sonatype just like you would for a production release:

```bash
./gradlew clean build && ./gradlew publish
```

You can verify the artifacts are available by visiting
https://central.sonatype.org/content/repositories/snapshots/com/squareup/workflow1/.
