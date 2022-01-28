package com.squareup.sample.timemachine.shakeable

import android.content.Context
import com.squareup.sample.timemachine.TimeMachineWorkflow
import com.squareup.sample.timemachine.TimeMachineWorkflow.TimeMachineProps
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.PropsFactory
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.State
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.State.PlayingBack
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.State.Recording
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * A wrapper around a [TimeMachineWorkflow] that uses [ShakeableTimeMachineLayoutRunner] to render
 * the [delegate workflow][TimeMachineWorkflow.delegateWorkflow]'s rendering, but wrap it in a
 * UI to scrub around the recorded timeline when the device is shaken.
 *
 * This workflow takes a [PropsFactory] as its props. See that class for more documentation.
 */
@ExperimentalTime
class ShakeableTimeMachineWorkflow<in P, O : Any, out R : Any>(
  private val timeMachineWorkflow: TimeMachineWorkflow<P, O, R>,
  context: Context
) : StatefulWorkflow<PropsFactory<P>, State, O, ShakeableTimeMachineRendering>() {

  /**
   * A factory that knows how to create the props for a [TimeMachineWorkflow.delegateWorkflow],
   * given a flag indicating whether the workflow is in recording mode or not.
   */
  class PropsFactory<out P>(
    val createDelegateProps: (recording: Boolean) -> P
  )

  sealed class State {
    object Recording : State()
    data class PlayingBack(val timestamp: Duration) : State()
  }

  private val shakeWorker = ShakeWorker(context)

  override fun initialState(
    props: PropsFactory<P>,
    snapshot: Snapshot?
  ): State = Recording

  override fun snapshotState(state: State): Snapshot? = null

  override fun render(
    renderProps: PropsFactory<P>,
    renderState: State,
    context: RenderContext
  ): ShakeableTimeMachineRendering {
    // Only listen to shakes when recording.
    if (renderState === Recording) context.runningWorker(shakeWorker) { onShake }

    val delegateProps = renderProps.createDelegateProps(renderState === Recording)

    val timeMachineProps = when (renderState) {
      Recording -> TimeMachineProps.Recording(delegateProps)
      is PlayingBack -> TimeMachineProps.PlayingBackAt(delegateProps, renderState.timestamp)
    }

    val timeMachineRendering =
      context.renderChild(timeMachineWorkflow, timeMachineProps) { output: O ->
        forwardOutput(output)
      }

    return ShakeableTimeMachineRendering(
      rendering = timeMachineRendering.value,
      totalDuration = timeMachineRendering.totalDuration,
      playbackPosition = if (renderState is PlayingBack) {
        minOf(renderState.timestamp, timeMachineRendering.totalDuration)
      } else {
        Duration.INFINITE
      },
      recording = (renderState is Recording),
      onSeek = when (renderState) {
        Recording -> {
          // No handler. Need the _ so the type inferencer doesn't get confused for the lambda
          // below. This will be fixed with the new type inference algorithm in 1.4.
          { _ -> }
        }
        is PlayingBack -> {
          { position -> context.actionSink.send(SeekAction(position)) }
        }
      },
      onResumeRecording = when (renderState) {
        Recording -> {
          // No handler.
          {}
        }
        is PlayingBack -> {
          { context.actionSink.send(ResumeRecordingAction()) }
        }
      }
    )
  }

  private val onShake = action {
    state = PlayingBack(Duration.INFINITE)
  }

  private inner class SeekAction(
    private val newPosition: Duration
  ) : WorkflowAction<PropsFactory<P>, State, O>() {
    override fun Updater.apply() {
      state = PlayingBack(newPosition)
    }
  }

  private inner class ResumeRecordingAction : WorkflowAction<PropsFactory<P>, State, O>() {
    override fun Updater.apply() {
      state = Recording
    }
  }

  private fun forwardOutput(output: O) = action { setOutput(output) }
}
