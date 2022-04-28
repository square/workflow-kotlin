package com.squareup.sample.dungeon

import android.content.Context
import com.squareup.sample.dungeon.DungeonAppWorkflow.Props
import com.squareup.sample.timemachine.TimeMachineWorkflow
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineScreen
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.PropsFactory
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.asScreen
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * A workflow that wraps [DungeonAppWorkflow] with a [ShakeableTimeMachineWorkflow] to enable
 * time travel debugging.
 */
@OptIn(ExperimentalTime::class, WorkflowUiExperimentalApi::class)
class TimeMachineAppWorkflow(
  appWorkflow: DungeonAppWorkflow,
  clock: TimeSource,
  context: Context
) : StatelessWorkflow<BoardPath, Nothing, ShakeableTimeMachineScreen>() {

  @Suppress("DEPRECATION")
  private val timeMachineWorkflow =
    ShakeableTimeMachineWorkflow(
      TimeMachineWorkflow(appWorkflow.mapRendering { asScreen(it) }, clock),
      context
    )

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: BoardPath,
    context: RenderContext
  ): ShakeableTimeMachineScreen {
    val propsFactory = PropsFactory { recording ->
      Props(paused = !recording)
    }
    return context.renderChild(timeMachineWorkflow, propsFactory)
  }
}
