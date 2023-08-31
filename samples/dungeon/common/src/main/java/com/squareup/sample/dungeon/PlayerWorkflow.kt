package com.squareup.sample.dungeon

import com.squareup.sample.dungeon.ActorWorkflow.ActorProps
import com.squareup.sample.dungeon.ActorWorkflow.ActorRendering
import com.squareup.sample.dungeon.PlayerWorkflow.Action.StartMoving
import com.squareup.sample.dungeon.PlayerWorkflow.Action.StopMoving
import com.squareup.sample.dungeon.PlayerWorkflow.Rendering
import com.squareup.sample.dungeon.board.BoardCell
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowLocal

/**
 * Workflow that represents the actual player of the game in the [GameWorkflow].
 */
class PlayerWorkflow(
  private val avatar: BoardCell = BoardCell("👩🏻‍🎤"),
  private val cellsPerSecond: Float = 15f
) : StatefulWorkflow<ActorProps, Movement, Nothing, Rendering>() {

  sealed class Action : WorkflowAction<ActorProps, Movement, Nothing>() {

    class StartMoving(private val direction: Direction) : Action() {
      override fun Updater.apply() {
        state += direction
      }
    }

    class StopMoving(private val direction: Direction) : Action() {
      override fun Updater.apply() {
        state -= direction
      }
    }
  }

  data class Rendering(
    val actorRendering: ActorRendering,
    val onStartMoving: (Direction) -> Unit,
    val onStopMoving: (Direction) -> Unit
  )

  override fun initialState(
    props: ActorProps,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ): Movement = Movement(cellsPerSecond = cellsPerSecond)

  override fun render(
    renderProps: ActorProps,
    renderState: Movement,
    context: RenderContext
  ): Rendering = Rendering(
    actorRendering = ActorRendering(avatar = avatar, movement = renderState),
    onStartMoving = { context.actionSink.send(StartMoving(it)) },
    onStopMoving = { context.actionSink.send(StopMoving(it)) }
  )

  override fun snapshotState(state: Movement): Snapshot? = null
}
