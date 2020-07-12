@file:Suppress("UnstableApiUsage")

package com.squareup.workflow1

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.squareup.workflow1.WrongSnapshotUsageDetector.Companion.ISSUE_SNAPSHOT_STATE_EMPTY
import kotlin.test.Test

class WrongSnapshotUsageDetectorTest {

  @Test fun `snapshotState() returns Snapshot-EMPTY`() {
    lint()
        .files(
            SNAPSHOT_STUB,
            kotlin(
                """
                  package foo
                  import com.squareup.workflow1.Snapshot
                  import com.squareup.workflow1.StatefulWorkflow
                  class FooWorkflow : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {
                    override fun initialState(
                      props: Unit,
                      snapshot: Snapshot?
                    ): Unit = Unit

                    override fun render(
                      props: Unit,
                      state: Unit,
                      context: RenderContext
                    ): Unit = Unit

                    override fun snapshotState(state: Unit): Snapshot? = Snapshot.EMPTY
                  }
                """.trimIndent()
            )
        )
        .issues(ISSUE_SNAPSHOT_STATE_EMPTY)
        .run()
        .expect(
            """
              TODO()
            """.trimIndent()
        )
  }

  @Test fun `snapshotState() returns null`() {
    lint()
        .files(
            SNAPSHOT_STUB,
            kotlin(
                """
                  package foo
                  import com.squareup.workflow1.Snapshot
                  import com.squareup.workflow1.StatefulWorkflow
                  class FooWorkflow : StatefulWorkflow<Unit, Unit, Nothing, Unit>() {
                    override fun initialState(
                      props: Unit,
                      snapshot: Snapshot?
                    ): Unit = Unit

                    override fun render(
                      props: Unit,
                      state: Unit,
                      context: RenderContext
                    ): Unit = Unit

                    override fun snapshotState(state: Unit): Snapshot? = null
                  }
                """.trimIndent()
            )
        )
        .issues(ISSUE_SNAPSHOT_STATE_EMPTY)
        .run()
        .expectClean()
  }

  companion object {
    val SNAPSHOT_STUB = kotlin(
        """
          package com.squareup.workflow1
          class Snapshot {
            companion object {
              val EMPTY = TODO()
            }
          }
        """.trimIndent()
    )
  }
}
