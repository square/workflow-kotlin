package com.squareup.sample.dungeon

import android.content.Context
import com.squareup.sample.dungeon.DungeonAppWorkflow.Props
import com.squareup.sample.timemachine.TimeMachineWorkflow
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineScreen
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineWorkflow.PropsFactory
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.renderChild
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * A workflow that wraps [DungeonAppWorkflow] with a [ShakeableTimeMachineWorkflow] to enable
 * time travel debugging.
 */
@OptIn(ExperimentalTime::class)
class TimeMachineAppWorkflow(
  appWorkflow: DungeonAppWorkflow,
  clock: TimeSource,
  context: Context
) : StatelessWorkflow<BoardPath, Nothing, ShakeableTimeMachineScreen>() {

  private val timeMachineWorkflow =
    ShakeableTimeMachineWorkflow(
      TimeMachineWorkflow(appWorkflow, clock),
      context
    )

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
