package com.squareup.sample.timemachine

import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * @param value When [recording][TimeMachineWorkflow.TimeMachineProps.Recording], this is the last
 * recorded value. When [playing back][TimeMachineWorkflow.TimeMachineProps.PlayingBackAt], this is
 * the value closest to the playback position.
 * @param totalDuration Total length of the recording.
 */
@ExperimentalTime
data class TimeMachineRendering<out T>(
  val value: T,
  val totalDuration: Duration
)
