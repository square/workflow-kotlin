/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow1.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistry.SavedStateProvider
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.renderWorkflowIn
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

@WorkflowUiExperimentalApi
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowRunnerViewModel<OutputT>(
  private val scope: CoroutineScope,
  private val outputChannel: ReceiveChannel<OutputT>,
  private val renderingsAndSnapshots: StateFlow<RenderingAndSnapshot<Any>>
) : ViewModel(), WorkflowRunner<OutputT>, SavedStateProvider {

  internal interface SnapshotSaver {
    fun consumeSnapshot(): TreeSnapshot?
    fun registerProvider(provider: SavedStateProvider)

    companion object {
      fun fromSavedStateRegistry(savedStateRegistry: SavedStateRegistry) = object : SnapshotSaver {
        override fun consumeSnapshot(): TreeSnapshot? {
          return savedStateRegistry
              .consumeRestoredStateForKey(BUNDLE_KEY)
              ?.getParcelable<PickledWorkflow>(BUNDLE_KEY)
              ?.snapshot
        }

        override fun registerProvider(provider: SavedStateProvider) {
          savedStateRegistry.registerSavedStateProvider(BUNDLE_KEY, provider)
        }
      }
    }
  }

  internal class Factory<PropsT, OutputT>(
    private val snapshotSaver: SnapshotSaver,
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
          config.workflow, scope, props,
          initialSnapshot = snapshot,
          interceptors = config.interceptors
      ) { output ->
        outputChannel.send(output)
      }

      @Suppress("UNCHECKED_CAST")
      return WorkflowRunnerViewModel(scope, outputChannel, renderingsAndSnapshots).also {
        snapshotSaver.registerProvider(it)
      } as T
    }
  }

  override suspend fun receiveOutput(): OutputT = outputChannel.receive()

  private val lastSnapshot: TreeSnapshot get() = renderingsAndSnapshots.value.snapshot

  @OptIn(ExperimentalCoroutinesApi::class)
  override val renderings: StateFlow<Any> = renderingsAndSnapshots.mapState { it.rendering }

  override fun onCleared() {
    scope.cancel(CancellationException("WorkflowRunnerViewModel cleared."))
  }

  override fun saveState() = Bundle().apply {
    putParcelable(BUNDLE_KEY, PickledWorkflow(lastSnapshot))
  }

  @TestOnly
  internal fun clearForTest() = onCleared()

  @TestOnly
  internal fun getLastSnapshotForTest() = lastSnapshot

  private companion object {
    /**
     * Namespace key, used in two namespaces:
     *  - associates the [WorkflowRunnerViewModel] with the [SavedStateRegistry]
     *  - and is also the key for the [PickledWorkflow] in the bundle created by [saveState].
     */
    val BUNDLE_KEY = WorkflowRunner::class.java.name + "-workflow"
  }
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
  }
}
