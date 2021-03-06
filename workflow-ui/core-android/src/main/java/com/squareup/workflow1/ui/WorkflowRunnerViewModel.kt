@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.ui.TreeSnapshotSaver.HasTreeSnapshot
import com.squareup.workflow1.ui.WorkflowRunner.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.TestOnly

@Deprecated("Use your own ViewModel and com.squareup.workflow1.ui.renderWorkflowIn")
@WorkflowUiExperimentalApi
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowRunnerViewModel<OutputT>(
  private val scope: CoroutineScope,
  private val outputChannel: ReceiveChannel<OutputT>,
  private val renderingsAndSnapshots: StateFlow<RenderingAndSnapshot<Any>>
) : ViewModel(), WorkflowRunner<OutputT>, HasTreeSnapshot {

  internal class Factory<PropsT, OutputT>(
    private val snapshotSaver: TreeSnapshotSaver,
    private val configure: () -> Config<PropsT, OutputT>
  ) : ViewModelProvider.Factory {
    private val snapshot = snapshotSaver.consumeSnapshot()

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val config = configure()
      val props = config.props
      val scope = CoroutineScope(config.dispatcher)
      val outputChannel = Channel<OutputT>()
      scope.coroutineContext[Job]!!.invokeOnCompletion {
        outputChannel.close(it)
      }

      val renderingsAndSnapshots = renderWorkflowIn(
        config.workflow, scope, props, snapshot, config.interceptors
      ) { output -> outputChannel.send(output) }

      @Suppress("UNCHECKED_CAST")
      return WorkflowRunnerViewModel(scope, outputChannel, renderingsAndSnapshots).also {
        snapshotSaver.registerSource(it)
      } as T
    }
  }

  override suspend fun receiveOutput(): OutputT = outputChannel.receive()

  override fun latestSnapshot(): TreeSnapshot = renderingsAndSnapshots.value.snapshot

  @OptIn(ExperimentalCoroutinesApi::class)
  override val renderings: StateFlow<Any> = renderingsAndSnapshots.mapState { it.rendering }

  override fun onCleared() {
    scope.cancel(CancellationException("WorkflowRunnerViewModel cleared."))
  }

  @TestOnly
  internal fun clearForTest() = onCleared()
}

/**
 * Like [Flow.map], but preserves the [StateFlow.value] property.
 *
 * Issue to add this operator to standard library is here:
 * https://github.com/Kotlin/kotlinx.coroutines/issues/2081
 *
 * TODO(https://github.com/square/workflow/issues/1191) Remove once stateIn ships.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
  // map takes a suspend function, so we can't just pass the function reference in.
  val mappedFlow = map { transform(it) }
  return object : StateFlow<R>, Flow<R> by mappedFlow {
    override val value: R get() = transform(this@mapState.value)
    override val replayCache: List<R> get() = listOf(value)
  }
}
