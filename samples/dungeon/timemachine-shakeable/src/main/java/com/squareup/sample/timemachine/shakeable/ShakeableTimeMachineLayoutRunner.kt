package com.squareup.sample.timemachine.shakeable

import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.transition.TransitionManager
import com.squareup.sample.timemachine.shakeable.internal.GlassFrameLayout
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromLayout
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.setBackHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Renders [ShakeableTimeMachineWorkflow][ShakeableTimeMachineWorkflow]
 * [renderings][ShakeableTimeMachineScreen].
 */
@OptIn(ExperimentalTime::class, WorkflowUiExperimentalApi::class)
class ShakeableTimeMachineLayoutRunner(
  private val view: View
) : ScreenViewRunner<ShakeableTimeMachineScreen> {

  private val glassView: GlassFrameLayout = view.findViewById(R.id.glass_view)
  private val childStub: WorkflowViewStub = view.findViewById(R.id.child_stub)
  private val seek: SeekBar = view.findViewById(R.id.time_travel_seek)
  private val group: Group = view.findViewById(R.id.group)
  private val endLabel: TextView = view.findViewById(R.id.end_label)
  private var wasRecording: Boolean? = null

  init {
    val startLabel = view.findViewById<TextView>(R.id.start_label)
    startLabel.text = Duration.ZERO.toUiString()
  }

  override fun showRendering(
    rendering: ShakeableTimeMachineScreen,
    environment: ViewEnvironment
  ) {
    // Only handle back presses explicitly if in playback mode.
    view.setBackHandler(rendering.onResumeRecording.takeUnless { rendering.recording })

    seek.max = rendering.totalDuration.toProgressInt()
    seek.progress = rendering.playbackPosition.toProgressInt()

    endLabel.text = rendering.totalDuration.toUiString()

    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(
        seekBar: SeekBar,
        progress: Int,
        fromUser: Boolean
      ) {
        if (!fromUser) return
        rendering.onSeek(progress.toProgressDuration())
      }

      override fun onStartTrackingTouch(seekBar: SeekBar) {
        // Don't care.
      }

      override fun onStopTrackingTouch(seekBar: SeekBar) {
        // Don't care.
      }
    })

    if (wasRecording != rendering.recording) {
      wasRecording = rendering.recording

      val visibility = if (rendering.recording) View.GONE else View.VISIBLE
      TransitionManager.beginDelayedTransition(view as ViewGroup)
      group.visibility = visibility
      glassView.blockTouchEvents = !rendering.recording
    }

    // Show the child screen.
    childStub.show(rendering.rendering, environment)
  }

  private fun Duration.toProgressInt(): Int = this.inWholeMilliseconds.toInt()
  private fun Int.toProgressDuration(): Duration = this.milliseconds

  private fun Duration.toUiString(): String = toString()

  companion object : ScreenViewFactory<ShakeableTimeMachineScreen> by fromLayout(
    R.layout.shakeable_time_machine_layout,
    ::ShakeableTimeMachineLayoutRunner
  )
}
