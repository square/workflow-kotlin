@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateless
import com.squareup.workflow1.ui.TreeSnapshotSaver.HasTreeSnapshot
import com.squareup.workflow1.ui.WorkflowRunner.Config
import com.squareup.workflow1.ui.WorkflowRunnerViewModel.Factory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString
import org.junit.After
import org.junit.Test
import java.lang.RuntimeException

@OptIn(ExperimentalCoroutinesApi::class, WorkflowUiExperimentalApi::class)
class WorkflowRunnerViewModelTest {

  private val testScope = CoroutineScope(Unconfined)
  private val runnerScope = testScope + Job(parent = testScope.coroutineContext[Job]!!)

  @After fun tearDown() {
    testScope.cancel()
  }

  @Test fun snapshotUpdatedOnHostEmission() {
    val snapshot1 = TreeSnapshot.forRootOnly(Snapshot.of("one"))
    val snapshot2 = TreeSnapshot.forRootOnly(Snapshot.of("two"))
    val outputChannel = Channel<String>()
    val treeSnapshot = TreeSnapshot.forRootOnly(Snapshot.of("snapshot"))
    val renderingsAndSnapshots = MutableStateFlow(RenderingAndSnapshot(Any(), treeSnapshot))

    val runner = WorkflowRunnerViewModel(runnerScope, outputChannel, renderingsAndSnapshots)

    assertThat(runner.latestSnapshot()).isEqualTo(treeSnapshot)

    renderingsAndSnapshots.value = RenderingAndSnapshot(Unit, snapshot1)
    assertThat(runner.latestSnapshot()).isEqualTo(snapshot1)

    renderingsAndSnapshots.value = RenderingAndSnapshot(Unit, snapshot2)
    assertThat(runner.latestSnapshot()).isEqualTo(snapshot2)
  }

  @Test fun `Factory buffers multiple results received between hosts`() {
    val onResult = mock<(String) -> Unit>()
    val trigger1 = CompletableDeferred<String>()
    val trigger2 = CompletableDeferred<String>()
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(Worker.from { trigger1.await() }, "worker-1") { action { setOutput(it) } }
      runningWorker(Worker.from { trigger2.await() }, "worker-2") { action { setOutput(it) } }
    }
    val runner = workflow.run()

    val host1 = testScope.async { runner.receiveOutput() }

    host1.cancel()
    trigger1.complete("fnord1")
    trigger2.complete("fnord2")
    val host2 = testScope.launch {
      while (isActive) {
        onResult(runner.receiveOutput())
      }
    }

    assertThat(host1.isCompleted).isTrue()
    assertThat(host2.isActive).isTrue()
    verify(onResult).invoke("fnord1")
    verify(onResult).invoke("fnord2")
  }

  @Test fun `Factory cancels host on result and no sooner`() {
    val trigger = CompletableDeferred<String>()
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(Worker.from { trigger.await() }) { action { setOutput(it) } }
    }
    val runner = workflow.run()
    val tester = testScope.async { runner.receiveOutput() }

    assertThat(tester.isActive).isTrue()
    trigger.complete("fnord")
    assertThat(tester.isCompleted).isTrue()
    assertThat(tester.getCompleted()).isEqualTo("fnord")
  }

  @Test fun `Factory propagates worker errors`() {
    val trigger = CompletableDeferred<String>()
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(Worker.from { trigger.await() }) { action { setOutput(it) } }
    }
    val runner = workflow.run()
    val tester = testScope.async { runner.receiveOutput() }

    assertThat(tester.isActive).isTrue()

    val runtimeError = RuntimeException("fnord")
    trigger.completeExceptionally(runtimeError)

    assertThat(tester.isCompleted).isTrue()
    val completionExceptionCause = tester.getCompletionExceptionOrNull()?.cause
    assertThat(completionExceptionCause).isEqualTo(runtimeError)
  }

  @Test fun `Factory cancels result when cleared`() {
    val workflow = Workflow.stateless<Unit, Unit, Unit> {}
    val runner = workflow.run()
    val tester = testScope.async { runner.receiveOutput() }

    assertThat(tester.isActive).isTrue()
    runner.clearForTest()
    assertThat(tester.isCancelled).isTrue()
  }

  @Test fun `Factory cancels runtime when cleared`() {
    var cancelled = false
    val workflow = Workflow.stateless<Unit, Unit, Unit> {
      runningWorker(Worker.createSideEffect {
        suspendCancellableCoroutine {
          it.invokeOnCancellation {
            cancelled = true
          }
        }
      })
    }
    val runner = workflow.run()

    assertThat(cancelled).isFalse()
    runner.clearForTest()
    assertThat(cancelled).isTrue()
  }

  @Test fun hostCancelledOnCleared() {
    var cancellationException: Throwable? = null
    runnerScope.coroutineContext[Job]!!.invokeOnCompletion { e ->
      if (e is CancellationException) {
        cancellationException = e
      } else throw AssertionError(
          "Expected ${CancellationException::class.java.simpleName}", e
      )
    }
    val outputChannel = Channel<String>()
    val treeSnapshot = TreeSnapshot.forRootOnly(Snapshot.of(ByteString.EMPTY))
    val renderingsAndSnapshots = MutableStateFlow(RenderingAndSnapshot(Any(), treeSnapshot))
    val runner = WorkflowRunnerViewModel(runnerScope, outputChannel, renderingsAndSnapshots)

    assertThat(cancellationException).isNull()
    runner.clearForTest()
    assertThat(cancellationException).isInstanceOf(CancellationException::class.java)
    val cause = generateSequence(cancellationException) { it.cause }
        .firstOrNull { it !is CancellationException }
    assertThat(cause).isNull()
  }

  @OptIn(ObsoleteCoroutinesApi::class)
  @Test fun resultDelivered() {
    val outputs = BroadcastChannel<String>(1)
    @Suppress("DEPRECATION") val runner = Workflow
        .stateless<Unit, String, Unit> {
          runningWorker(outputs.asWorker()) { action { setOutput(it) } }
          Unit
        }
        .run()

    val tester = testScope.async { runner.receiveOutput() }

    assertThat(tester.isActive).isTrue()
    runBlocking { outputs.send("fnord") }
    assertThat(tester.isCompleted).isTrue()
    assertThat(tester.getCompleted()).isEqualTo("fnord")
  }

  @Test fun resultCancelledOnCleared() {
    val runner = Workflow
        .stateless<Unit, String, Unit> {
          runningWorker(flowNever<String>().asWorker()) { action { setOutput(it) } }
        }
        .run()

    val tester = testScope.async { runner.receiveOutput() }

    assertThat(tester.isActive).isTrue()
    runner.clearForTest()
    assertThat(tester.isCancelled).isTrue()
    assertThat(tester.getCompletionExceptionOrNull())
        .isInstanceOf(CancellationException::class.java)
    assertThat(tester.getCompletionCauseOrNull()).isNull()
  }

  private fun <O : Any, R : Any> Workflow<Unit, O, R>.run(): WorkflowRunnerViewModel<O> {
    @Suppress("UNCHECKED_CAST")
    return Factory(NoopSnapshotSaver) { Config(this, Unit, Unconfined) }
        .create(WorkflowRunnerViewModel::class.java) as WorkflowRunnerViewModel<O>
  }

  private fun <T> flowNever(): Flow<T> {
    return flow { suspendCancellableCoroutine { } }
  }

  private fun <T> Deferred<T>.getCompletionCauseOrNull() =
    generateSequence(getCompletionExceptionOrNull()) { it.cause }
        .firstOrNull { it !is CancellationException }

  private object NoopSnapshotSaver : TreeSnapshotSaver {
    override fun consumeSnapshot(): TreeSnapshot? = null
    override fun registerSource(source: HasTreeSnapshot) = Unit
  }
}
