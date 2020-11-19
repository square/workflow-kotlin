# Releasing workflow

## Production Releases

---
1. Merge an update of [the change log](CHANGELOG.md) with the changes since the last release.

1. Make sure you're on the `main` branch (or fix branch, e.g. `v0.1-fixes`).

1. Confirm that the kotlin build is green before committing any changes
   ```bash
   (cd kotlin && ./gradlew build connectedCheck)
   ```

1. In `kotlin/gradle.properties`, remove the `-SNAPSHOT` prefix from the `VERSION_NAME` property.
   E.g. `VERSION_NAME=0.1.0`

1. Create a commit and tag the commit with the version number:
   ```bash
   git commit -am "Releasing v0.1.0."
   git tag v0.1.0
   ```

1. Upload the kotlin artifacts:
   ```bash
   (cd kotlin && ./gradlew clean build && ./gradlew publish --no-parallel)
   ```

   Disabling parallelism and daemon sharing is required by the vanniktech maven publish plugin.
   Without those, the artifacts will be split across multiple (invalid) staging repositories.

1. Close and release the staging repository at https://oss.sonatype.org.

1. Bump the version
   - **Kotlin:** Update the `VERSION_NAME` property in `kotlin/gradle.properties` to the new
     snapshot version, e.g. `VERSION_NAME=0.2.0-SNAPSHOT`.

1. Commit the new snapshot version:
   ```
   git commit -am "Finish releasing v0.1.0."
   ```

1. Push your commits and tag:
   ```
   git push origin main
   # or git push origin fix-branch
   git push origin v0.1.0
   ```

1. Create the release on GitHub:
   1. Go to the [Releases](https://github.com/square/workflow/releases) page for the GitHub
      project.
   1. Click "Draft a new release".
   1. Enter the tag name you just pushed.
   1. Title the release with the same name as the tag.
   1. Copy & paste the changelog entry for this release into the description.
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

1. Publish the website. See https://github.com/square/workflow/blob/main/RELEASING.md.

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

In order to deploy artifacts to a Maven repository, you'll need to set 4 properties in your private
Gradle properties file (`~/.gradle/gradle.properties`):

```
RELEASE_REPOSITORY_URL=<url of release repository>
SNAPSHOT_REPOSITORY_URL=<url of snapshot repository
SONATYPE_NEXUS_USERNAME=<username>
SONATYPE_NEXUS_PASSWORD=<password>
```

#### Snapshot Releases

Double-check that `gradle.properties` correctly contains the `-SNAPSHOT` suffix, then upload
snapshot artifacts to Sonatype just like you would for a production release:

```bash
./gradlew clean build && ./gradlew uploadArchives --no-parallel --no-daemon
```

You can verify the artifacts are available by visiting
https://oss.sonatype.org/content/repositories/snapshots/com/squareup/workflow/.
