package com.squareup.sample.timemachine.shakeable

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * @param rendering The rendering of the
 * [delegate workflow][com.squareup.sample.timemachine.TimeMachineWorkflow.delegateWorkflow].
 * @param totalDuration The total duration of the recorded session.
 * @param playbackPosition The timestamp of the rendering currently being played back, or
 * [Duration.INFINITE] if not recording.
 * @param recording If false, the time travelling UI should be shown.
 * @param onSeek Event handler that will be called when [recording] is false and the timeline is
 * scrubbed.
 * @param onResumeRecording Event handler that will be called when [recording] is false and the user
 * wants to go back to the live delegate workflow.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@ExperimentalTime
data class ShakeableTimeMachineScreen(
  val rendering: Screen,
  val totalDuration: Duration,
  val playbackPosition: Duration,
  val recording: Boolean,
  val onSeek: (Duration) -> Unit = {},
  val onResumeRecording: () -> Unit
) : Screen
